package com.lqian.repetition.conf.redis;

import com.lqian.repetition.lock.DistributedLock;
import com.lqian.repetition.lock.RedisDistributedLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;


@Configuration
@Order(4)
public class DistributedLockAutoConfiguration {



    @Autowired
    RedisTemplate<Object, Object> redisTemplate;

    /**
     * @param
     * @return
     */
    @Bean
    public DistributedLock redisDistributedLock(){
        return new RedisDistributedLock(redisTemplate);
    }
}
