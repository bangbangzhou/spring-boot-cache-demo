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