package com.zbbmeta.controller;


import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.zbbmeta.config.RedisConfig;
import com.zbbmeta.entity.Tutorial;
import com.zbbmeta.service.TutorialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;


import javax.annotation.Resource;
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

    @Autowired
    Cache<String, Tutorial> transportInfoCache;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 根据ID查询Tutorial
     * @param id
     * @return
     */
    @GetMapping("/tutorials/{id}")
    @Cacheable(value = "tutorial-info", key = "#p0") //新增缓存数据
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



    /**
     * 根据id删除Tutorial
     * @param id
     * @return
     */
    @DeleteMapping("/tutorials/{id}")
    @CacheEvict(value ="tutorial-info", key = "#p0")
    public boolean deleteTutorial(@PathVariable("id") long id) {

        //清除缓存中的数据
        this.transportInfoCache.invalidate(StrUtil.toString(id));
        //发布订阅消息到redis
        this.stringRedisTemplate.convertAndSend(RedisConfig.CHANNEL_TOPIC, StrUtil.toString(id));
        return      tutorialService.removeById(id);

    }

    /**
     * 创建或者更新Tutorial
     * @param tutorial
     * @return
     */
    @CachePut(value = "tutorial-info", key = "#tutorial.id")
    @PostMapping("/tutorials")
    public Tutorial createOrUpdateTutorial(@RequestBody Tutorial tutorial) {

            if( tutorial.getId()!=null){
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



}