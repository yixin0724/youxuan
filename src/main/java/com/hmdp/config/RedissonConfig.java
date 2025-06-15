package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author yixin
 * @date 2025/6/11
 * @description Redisson配置类
 */
@Configuration
public class RedissonConfig {

    /**
     * 创建RedissonClient对象
     * @return
     */
    @Bean
    public RedissonClient redissonClient() {
        //创建配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.217.80:6379").setPassword("1234");
        //创建RedissonClient对象并返回
        return Redisson.create(config);
    }

    //下面这两个是为了模拟redis集群
//    @Bean
//    public RedissonClient redissonClient2() {
//        //创建配置
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://192.168.217.130:6379");
//        //创建RedissonClient对象并返回
//        return Redisson.create(config);
//    }
//
//    @Bean
//    public RedissonClient redissonClient3() {
//        //创建配置
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://192.168.217.131:6379");
//        //创建RedissonClient对象并返回
//        return Redisson.create(config);
//    }
}
