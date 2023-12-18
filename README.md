# SpringBoot多级缓存解决方案
![](https://files.mdnice.com/user/7954/9d8f0de6-2eba-47a3-9eac-66cf182d8a78.png)
今日内容介绍，大约花费30分钟,可以先收藏
![](https://files.mdnice.com/user/7954/9fdef092-4421-43a0-adf3-a7d2107f9569.png)

```
#代码地址
https://github.com/bangbangzhou/spring-boot-cache-demo.git
```
SpringBoot实现项目更删改查后，会有新的问题需要解决，**就是并发大的问题，一般而言，解决查询并发大的问题，常见的手段是为查询接口增加缓存，从而可以减轻持久层的压力**。
按照我们以往的经验，在查询接口中增加Redis缓存即可，将查询的结果数据存储到Redis中，执行查询时首先从Redis中命中，如果命中直接返回即可，没有命中查询**Mysql**，将解决写入到Redis中。
这样就解决问题了吗？其实并不是，试想一下，如果Redis宕机了或者是Redis中的数据大范围的失效，这样大量的并发压力就会进入持久层，会对持久层有较大的影响，甚至可能直接崩溃。
如何解决该问题呢，可以通过多级缓存的解决方案来进行解决。
## 1. 什么是多级缓存
![image.png](https://cdn.nlark.com/yuque/0/2022/png/27683667/1664261583745-c849ed54-d1d7-486e-b969-410169b3fb77.png#averageHue=%23f8f6f5&clientId=u967c9d75-9b30-4&errorMessage=unknown%20error&from=paste&height=274&id=uad63b5a1&name=image.png&originHeight=452&originWidth=1495&originalType=binary&ratio=1&rotation=0&showTitle=false&size=95615&status=error&style=shadow&taskId=u20b7d124-7e47-4fcb-b4e4-dcc087bedae&title=&width=906.0605536917164)
由上图可以看出，在用户的一次请求中，可以设置多个缓存以提升查询的性能，能够快速响应。

- 浏览器的本地缓存
- 使用Nginx作为反向代理的架构时，可以启用Nginx的本地缓存，对于代理数据进行缓存
- 如果Nginx的本地缓存未命中，可以在Nginx中编写Lua脚本从Redis中命中数据
- 如果Redis依然没有命中的话，请求就会进入到Tomcat，也就是执行我们写的程序，在程序中可以设置进程级的缓存，如果命中直接返回即可。
- 如果进程级的缓存依然没有命中的话，请求才会进入到持久层查询数据。

以上就是多级缓存的基本的设计思路，其核心思想就是让每一个请求节点尽可能的进行缓存操作。
```
🚨说明，这里我们实现二级缓存，分别是：JVM进程缓存和Redis缓存。
```
## 2. Caffeine快速入门
>**Caffeine**是一个基于Java8开发的，提供了近乎最佳命中率的高性能的**本地缓存库**，也就是可以通过Caffeine**实现进程级的缓存**。Spring内部的缓存使用的就是Caffeine。

Caffeine的性能非常强悍，下图是官方给出的性能对比：
![](https://cdn.nlark.com/yuque/0/2022/png/27683667/1664275426398-78c14aa6-2d50-4f98-9b95-5faffecc15bf.png#averageHue=%23f8f5f4&clientId=u0e5ee64d-2205-4&errorMessage=unknown%20error&from=paste&height=407&id=u8ced8a50&originHeight=813&originWidth=1000&originalType=url&ratio=1&rotation=0&showTitle=false&status=error&style=shadow&taskId=u6632c6f3-045a-490f-9605-5bce85c548b&title=&width=500)

### 2.1. 项目准备
**完整项目结构如下**

![](https://files.mdnice.com/user/7954/bf114d25-3ba0-4f45-94b6-2fac32c913a2.png)

- 创建`spring-boot-cache-demo`项目，并在pom.xml中添加依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <artifactId>spring-boot-starter-parent</artifactId>
        <groupId>org.springframework.boot</groupId>
        <version>2.7.15</version>
    </parent>

    <groupId>com.zbbmeta</groupId>
    <artifactId>spring-boot-cache-demo</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!--jvm进程缓存-->
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>

        <!-- Spring Boot Starter for Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>


<!--        test-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>3.5.3</version>
        </dependency>

        <dependency>
            <groupId>mysql</groupId>
            <artifactId>mysql-connector-java</artifactId>
            <version>8.0.30</version>
        </dependency>

        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>5.8.20</version>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>

    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>

```
- 创建项目需要使用到的表
```sql
create database backend_db;
use backend_db;

create table tb_tutorial
(
    id      bigint auto_increment comment '主键ID'
        primary key,
    title   varchar(40)    comment '标题',
    description    varchar(30)    comment '描述',
    published     tinyint        comment '1 表示发布 0 表示未发布'
);

```
- 根据MybatisX生成tb_tutorial对应实体类、Mapper、Service

![](https://files.mdnice.com/user/7954/659ddd5a-3bc9-4a56-9616-8cd9a6d8bc8f.png)

![](https://files.mdnice.com/user/7954/e07ac897-b481-4930-836f-5aecc668b073.png)

- 在`com.zbbmeta.controller`包下创建`TutorialController`类
```java
package com.zbbmeta.controller;


import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.zbbmeta.entity.Tutorial;
import com.zbbmeta.service.TutorialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;


import java.util.Objects;

/**
 * @author springboot葵花宝典
 * @description: TODO
 */
@RestController
@RequestMapping("/api")
public class TutorialController {

    @Autowired
    TutorialService tutorialService;


    /**
     * 根据ID查询Tutorial
     * @param id
     * @return
     */
    @GetMapping("/tutorials/{id}")
    public Tutorial getTutorialById(@PathVariable("id") long id) {
        Tutorial tutorial1 = this.tutorialService.getById(id);
        return tutorial1DTO;
    }

    /**
     * 创建Tutorial
     * @param tutorial
     * @return
     */
    @PostMapping("/tutorials")
    public Tutorial createTutorial(@RequestBody Tutorial tutorial) {
            boolean save = tutorialService.save(tutorial);
            return tutorial;
    }

}
```
### 2.2. 使用
导入依赖：
```xml
<!--jvm进程缓存-->
<dependency>
		<groupId>com.github.ben-manes.caffeine</groupId>
		<artifactId>caffeine</artifactId>
</dependency>
```


**基本使用**：
在项目的 src/test/java 目录下，创建`com.zbbmeta`包，在包下创建`CaffeineTest`测试类
```java
package com.zbbmeta;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author springboot葵花宝典
 * @description: TODO
 */

@SpringBootTest
public class CaffeineTest {

    @Test
    public void testCaffeine() {
        // 创建缓存对象
        Cache<String, Object> cache = Caffeine.newBuilder()
                .initialCapacity(10) //缓存初始容量
                .maximumSize(100) //缓存最大容量
                .build();

        //将数据存储缓存中
        cache.put("key1", 123);

        // 从缓存中命中数据
        // 参数一：缓存的key
        // 参数二：Lambda表达式，表达式参数就是缓存的key，方法体是在未命中时执行
        // 优先根据key查询进程缓存，如果未命中，则执行参数二的Lambda表达式，执行完成后会将结果写入到缓存中
        Object value1 = cache.get("key1", key -> 456);
        System.out.println(value1); //123

        Object value2 = cache.get("key2", key -> 456);
        System.out.println(value2); //456
    }
}


```
### 2.3. 驱逐策略
**Caffeine缓存的一种**，肯定需要**有缓存的清除策略**，不然的话内存总会有耗尽的时候。
Caffeine提供了三种缓存驱逐策略：

-  **基于容量**：设置缓存的数量上限
```java
// 创建缓存对象
Cache<String, String> cache = Caffeine.newBuilder()
    .maximumSize(1) // 设置缓存大小上限为 1，当缓存超出这个容量的时候，会使用Window TinyLfu策略来删除缓存。
    .build();
```

-  **基于时间**：设置缓存的有效时间
```java
// 创建缓存对象
Cache<String, String> cache = Caffeine.newBuilder()
    // 设置缓存有效期为 10 秒，从最后一次写入开始计时 
    .expireAfterWrite(Duration.ofSeconds(10)) 
    .build();
```

-  **基于引用**：设置缓存为软引用或弱引用，利用GC来回收缓存数据。性能较差，不建议使用。

**🚨注意：**在默认情况下，当一个缓存元素过期的时候，Caffeine不会自动立即将其清理和驱逐。而是在一次读或写操作后，或者在空闲时间完成对失效数据的驱逐。

## 3. 一级缓存
下面我们通过增加Caffeine实现一级缓存，主要是在 `com.zbbmeta.controller.TutorialController` 中实现缓存逻辑。
### 3.1. Caffeine配置
- 在`application.yml`中配置Caffeine
```yml
caffeine:
  init: 100
  max: 10000
```


- 在`com.zbbmeta.config`包下创建`CaffeineConfig`，实现Caffeine缓存配置

```java
package com.zbbmeta.config;

/**
 * @author springboot葵花宝典
 * @description: Caffeine缓存配置
 */

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zbbmeta.entity.Tutorial;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class CaffeineConfig {

    @Value("${caffeine.init}")
    private Integer init;
    @Value("${caffeine.max}")
    private Integer max;

    @Bean
    public Cache<String, Tutorial> transportInfoCache() {
        // 创建缓存对象
        return Caffeine.newBuilder()
                .initialCapacity(init) //缓存初始容量
                .maximumSize(max)//缓存最大容量
                .build();
    }

}
```

### 3.2. 实现缓存逻辑
在`com.zbbmeta.controller.TutorialController`中进行数据的命中，如果命中直接返回，没有命中查询Mysql。
```java
    @Autowired
    Cache<String, Tutorial> transportInfoCache;
    
    /**
     * 根据ID查询Tutorial
     * @param id
     * @return
     */
    @GetMapping("/tutorials/{id}")
    public Tutorial getTutorialById(@PathVariable("id") long id) {
        // 从缓存中命中数据
        // 参数一：缓存的key
        // 参数二：Lambda表达式，表达式参数就是缓存的key，方法体是在未命中时执行
        // 优先根据key查询进程缓存，如果未命中，则执行参数二的Lambda表达式，执行完成后会将结果写入到缓存中
        Tutorial tutorial1DTO = this.transportInfoCache.get(StrUtil.toString(id), s -> {
            Tutorial tutorial1 = this.tutorialService.getById(id);
            return tutorial1;
        });
        return tutorial1DTO;
    }
```
### 3.3. 测试
**未命中场景**：使用PostMan访问地址`http://localhost:8989/api/tutorials/1736743535144022017`

![](https://files.mdnice.com/user/7954/fa114438-0770-4396-9ad0-6757bac3dc0d.png)

**结果如下:**
![](https://files.mdnice.com/user/7954/3ff38e17-96c4-4c5d-bf5f-617e1e3c1656.png)

**命中之后,在此查询**：

![](https://files.mdnice.com/user/7954/42367e1f-075e-45e4-a37b-75d542e6cb74.png)

响应结果：

![](https://files.mdnice.com/user/7954/d87c9a5e-469f-4dc8-adcf-73efaf78e14e.png)

## 4. 二级缓存
>二级缓存通过Redis的存储实现，这里我们使用Spring Cache进行缓存数据的存储和读取。
### 4.1. Redis配置
**Spring Cache默认是采用jdk的对象序列化方式**，这种方式比较占用空间而且性能差，所以往往会将值以json的方式存储，此时就需要对RedisCacheManager进行自定义的配置。

在`com.zbbmeta.config`包下创建`RedisConfig`类配置redis
```java

/**
 * Redis相关的配置
 */
@Configuration
public class RedisConfig {

    /**
     * 存储的默认有效期时间，单位：小时
     */
    @Value("${redis.ttl:1}")
    private Integer redisTtl;

    @Bean
    public RedisCacheManager redisCacheManager(RedisTemplate redisTemplate) {
        // 默认配置
        RedisCacheConfiguration defaultCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                // 设置key的序列化方式为字符串
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // 设置value的序列化方式为json格式
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
//                .disableCachingNullValues() // 不缓存null
                .entryTtl(Duration.ofHours(redisTtl));  // 默认缓存数据保存1小时

        // 构redis缓存管理器
        RedisCacheManager redisCacheManager = RedisCacheManager.RedisCacheManagerBuilder
                .fromConnectionFactory(redisTemplate.getConnectionFactory())
                .cacheDefaults(defaultCacheConfiguration)
                .transactionAware() // 只在事务成功提交后才会进行缓存的put/evict操作
                .build();
        return redisCacheManager;
    }
}
```
### 4.2. 缓存注解
接下来需要在Service中增加SpringCache的注解，确保数据可以保存、更新数据到Redis。
```java
    @GetMapping("/tutorials/{id}")
    @Cacheable(value = "tutorial-info", key = "#p0") //新增缓存数据
    public Tutorial getTutorialById(@PathVariable("id") long id) {
      //省略
    }


    /**
     * 创建或者更新Tutorial
     * @param tutorial
     * @return
     */
    @CachePut(value = "tutorial-info", key = "#tutorial.id")
    @PostMapping("/tutorials")
    public Tutorial createOrUpdateTutorial(@RequestBody Tutorial tutorial) {

      //省略
    }
```

![](https://files.mdnice.com/user/7954/9d8f0de6-2eba-47a3-9eac-66cf182d8a78.png)
### 4.3. 测试
重启服务，进行功能测试，发现数据可以正常写入到Redis中，并且查询时二级缓存已经生效。

![](https://files.mdnice.com/user/7954/446c7cac-6eed-4af0-813d-6cf0c0516ff1.png)

到这里，已经完成了一级和二级缓存的逻辑。
## 5. 一级缓存更新的问题
更新`Tutorial`时，只是更新了Redis中的数据，并没有更新Caffeine中的数据，需要在更新数据时将Caffeine中相应的数据删除。
具体实现如下：

```java

    @Autowired
    Cache<String, Tutorial> transportInfoCache;
    
  /**
     * 创建或者更新Tutorial
     * @param tutorial
     * @return
     */
    @CachePut(value = "tutorial-info", key = "#tutorial.id")
    @PostMapping("/tutorials")
    public Tutorial createOrUpdateTutorial(@RequestBody Tutorial tutorial) {

            if( tutorial.getId()!=0){
                Tutorial tutorial1 = tutorialService.getById(tutorial.getId());
                if (Objects.nonNull(tutorial1)) {
                    tutorial1.setId(tutorial1.getId());
                    tutorial1.setTitle(tutorial.getTitle());
                    tutorial1.setDescription(tutorial.getDescription());
                    tutorial1.setPublished(tutorial.getPublished());
                    tutorialService.updateById(tutorial1);
                    //更新缓存中的数据
                    this.transportInfoCache.put(StrUtil.toString(tutorial.getId()),tutorial1);

                }
            }else {
               tutorialService.save(tutorial);
            }
            //清除缓存中的数据
            this.transportInfoCache.invalidate(StrUtil.toString(tutorial.getId()));
            return tutorial;


    }
```
这样的话就可以删除Caffeine中的数据，也就意味着下次查询时会从二级缓存中查询到数据，再存储到Caffeine中。
## 6. 分布式场景下的问题
### 6.1. 问题分析
通过前面的解决，视乎可以完成一级、二级缓存中数据的同步，**如果在单节点项目中是没有问题的，但是，在分布式场景下是有问题的**，看下图：


![](https://files.mdnice.com/user/7954/20ab62f1-accf-47ac-ae69-ea50ad19d9b3.png)


说明：

- 部署了2个`Tutorial`服务节点，每个微服务都有自己进程级的一级缓存，都共享同一个Redis作为二级缓存
- 假设，所有节点的一级和二级缓存都是空的，此时，用户通过节点1查询`Tutorial`信息，在完成后，节点1的caffeine和Redis中都会有数据
- 接着，系统通过节点2更新了物流数据，此时节点2中的caffeine和Redis都是更新后的数据
- 用户还是进行查询动作，依然是通过节点1查询，此时查询到的将是旧的数据，也就是出现了一级缓存与二级缓存之间的数据不一致的问题
### 6.2. 问题解决
如何解决该问题呢？可以通过消息的方式解决，就是任意一个节点数据更新了数据，发个消息出来，通知其他节点，其他节点接收到消息后，将自己caffeine中相应的数据删除即可。
关于消息的实现，可以采用RabbitMQ，也可以采用Redis的消息订阅发布来实现，在这里为了应用技术的多样化，所以采用Redis的订阅发布来实现。

![](https://files.mdnice.com/user/7954/b150e8ff-8407-4049-bd04-102bcd8b7f2d.png)


Redis 发布订阅(pub/sub)是一种消息通信模式：发送者(pub)发送消息，订阅者(sub)接收消息。
![image.png](https://cdn.nlark.com/yuque/0/2023/png/27683667/1672730986485-56e704a5-cf14-46a0-8663-9af815321c3d.png#averageHue=%23dcd8a7&clientId=uf341ec6b-5f9b-4&from=paste&height=238&id=ua061bf02&name=image.png&originHeight=357&originWidth=412&originalType=binary&ratio=1&rotation=0&showTitle=false&size=76706&status=done&style=shadow&taskId=ue149649c-b12b-431b-85d2-72367ea961c&title=&width=274.6666666666667)
当有新消息通过 publish 命令发送给频道 channel1 时， 这个消息就会被发送给订阅它的三个客户端。
Redis的订阅发布功能与传统的消息中间件（如：RabbitMQ）相比，相对轻量一些，针对数据准确和安全性要求没有那么高的场景可以直接使用。

- 在`com.zbbmeta.config.RedisConfig`增加订阅的配置：
```java
  public static final String CHANNEL_TOPIC = "tutorial-info-caffeine";

    /**
     * 配置订阅，用于解决Caffeine一致性的问题
     *
     * @param connectionFactory 链接工厂
     * @param listenerAdapter 消息监听器
     * @return 消息监听容器
     */
    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                                   MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new ChannelTopic(CHANNEL_TOPIC));
        return container;
    }
```
- 编写`RedisMessageListener`用于监听消息，删除caffeine中的数据。
  在`com.zbbmeta.listener`包下创建RedisMessageListener用于监听

```java
package com.zbbmeta.listener;


import cn.hutool.core.convert.Convert;
import com.github.benmanes.caffeine.cache.Cache;
import com.zbbmeta.entity.Tutorial;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
/**
 * @author springboot葵花宝典
 * @description:  redis消息监听，解决Caffeine一致性的问题
 */
@Component
public class RedisMessageListener extends MessageListenerAdapter {

    @Resource
    private Cache<String, Tutorial> transportInfoCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        //获取到消息中的运单id
        String id = Convert.toStr(message);
        //将本jvm中的缓存删除掉
        this.transportInfoCache.invalidate(id);
    }
}
```
更新数据后发送消息：
```java
    @CachePut(value = "tutorial-info", key = "#tutorial.id")
    @PostMapping("/tutorials")
    public Tutorial createOrUpdateTutorial(@RequestBody Tutorial tutorial) {

            if( tutorial.getId()!=0){
                Tutorial tutorial1 = tutorialService.getById(tutorial.getId());
                if (Objects.nonNull(tutorial1)) {
                    tutorial1.setId(tutorial1.getId());
                    tutorial1.setTitle(tutorial.getTitle());
                    tutorial1.setDescription(tutorial.getDescription());
                    tutorial1.setPublished(tutorial.getPublished());
                    tutorialService.updateById(tutorial1);
                    //更新缓存中的数据
                    this.transportInfoCache.put(StrUtil.toString(tutorial.getId()),tutorial1);

                }
            }else {
               tutorialService.save(tutorial);
            }
            //清除缓存中的数据
//            this.transportInfoCache.invalidate(StrUtil.toString(tutorial.getId()));
            //发布订阅消息到redis
            this.stringRedisTemplate.convertAndSend(RedisConfig.CHANNEL_TOPIC, StrUtil.toString(tutorial.getId()));
            return tutorial;


    }
```
### 6.3. 测试
测试时，需要启动2个相同的微服务，但是端口不能重复，需要设置不同的端口：

![](https://files.mdnice.com/user/7954/94bbd96f-2e08-428e-bfd4-c14664fb952e.png)

通过测试，发现可以接收到Redis订阅的消息：

![](https://files.mdnice.com/user/7954/22234679-f5d3-4d1f-9553-65a2e8f2a740.png)

![](https://files.mdnice.com/user/7954/49815188-348e-4d9f-9cc6-24a4c3677359.png)

最终可以解决多级缓存间的一致性的问题。

## 7.面试连环问
```
面试官问：

- 针对于查询并发高的问题你们是怎么解决的？有用多级缓存吗？具体是怎么用的？
- 多级缓存间的数据不一致是如何解决的？
- 来，说说在使用Redis场景中的缓存击穿、缓存雪崩、缓存穿透都是啥意思？对应的解决方案是啥？实际你解决过哪个问题？
- 说说布隆过滤器的优缺点是什么？什么样的场景适合使用布隆过滤器？
```

[缓存三兄弟（穿透、击穿、雪崩）总结](https://mp.weixin.qq.com/s?__biz=MzIzMjIyNTYwNg==&mid=2247488252&idx=1&sn=d655386c2249fee7fd8b9fa6da63a0e6&chksm=e8997cd5dfeef5c3659f6bf66b115fce8cced6c40b00ff58d552f2a99d10cb39e7ebef6584ab#rd)


[Redis 的过期策略都有哪些？](https://mp.weixin.qq.com/s?__biz=MzIzMjIyNTYwNg==&mid=2247488306&idx=1&sn=f7da33392e76a0f0b902c5bdae78cecc&chksm=e8997d1bdfeef40def0e6e0d0fdfdc211ca4e9b75373933d470f5493af17d7b6bc0731d5f9a5#rd)


[Redis 的持久化有哪几种方式？不同的持久化机制都有什么优缺点?
](https://mp.weixin.qq.com/s?__biz=MzIzMjIyNTYwNg==&mid=2247488300&idx=1&sn=8492ab18e9d7e19c685959fc330e5f9e&chksm=e8997d05dfeef413fd4bd9cadce0798337e7d1f895bf60935f164360465cabcc8be242f28d41#rd)


```
#代码地址
https://github.com/bangbangzhou/spring-boot-cache-demo.git
```


![](https://files.mdnice.com/user/7954/9d8f0de6-2eba-47a3-9eac-66cf182d8a78.png)
