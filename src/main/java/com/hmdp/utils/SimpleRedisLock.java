package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author yixin
 * @date 2025/6/11
 * @description redis实现分布式锁的实现类
 */

public class SimpleRedisLock implements ILock{
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);   //生成一个去除横线随机的UUID
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private String name;    //业务的名称用于锁的key
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //静态代码块每次在类第一次加载的时候执行，之后就不再执行
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        //设置lua脚本
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    //使用构造函数接收这两个值
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 尝试获取锁
     * @param timeoutSec 锁的过期时间
     * @return
     */
    public boolean tryLock(long timeoutSec) {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁，这里主要要是值的设置需要带上线程的标识
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        //直接返回success可能会造成空指针异常
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     * 如果你自己的锁是因为超时被删除了，别的线程拿到了锁，你刚好业务完成了，去删除锁的时候，就会造成误删，所以要判断是不是自己的锁。
     * 并且你要保证释放锁的整个过程是原子的，可以通过Lua脚本实现原子性
     * Lua本身是一个语言，它能编写脚本实现一次性执行多条redis命令，是原子的。
     */
    public void unlock() {
//        //获取线程标识
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁中的标识
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        //判断删除的锁是不是自己的
//        if (!threadId.equals(id)) {
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //调用lua脚本，需要读取lua文件，可以提前定义好
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                threadId
                );
    }
}
