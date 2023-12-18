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


        return Caffeine.newBuilder()
                .initialCapacity(init)
                .maximumSize(max)
                .build();
    }

}