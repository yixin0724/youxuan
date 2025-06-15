package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author yixin
 * @date 2025/6/11
 * @description 基于redis的全局id唯一生成器
 */
@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1749600000L;
    private static final long COUNT_BITS = 32;  //序列号位数
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成方式是基于redis的自增长，需要使用前缀来区分
     * redis对于key的自增是有上限的，2的64次方
     * @param keyPrefix
     * @return
     */
    public long nextId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //这里尽量加上当前时间戳，这样生成的id会更长，方便后续处理
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长得到的序列号尽量用基本数据类型，因为后面还要做运算，封装类型拆箱运算有几率造成空指针错误
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3.拼接并返回
        //因为时间戳和序列号都用long，所以可以左移32位，将时间戳左移32位，将序列号放在低32位
        //可以使用|运算符将时间戳和序列号拼接起来，因为序列号默认位置是0在填充
        return timestamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        //生成时间戳
        LocalDateTime time = LocalDateTime.of(2025, 6, 11, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}
