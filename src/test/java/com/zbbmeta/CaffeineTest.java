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
