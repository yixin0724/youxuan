package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private IVoucherOrderService proxy;

    //初始化静态代码块，将脚本加载到内存中
//    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
//    static {
//        SECKILL_SCRIPT = new DefaultRedisScript<>();
//        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
//        SECKILL_SCRIPT.setResultType(Long.class);
//    }

    //使用redis的stream队列替代了
//    //创建阻塞队列BlockingQueue，他的特点是当一个线程尝试取出队列中的元素，队列为空时，线程进入阻塞状态，直到队列中有元素为止
//    private BlockingQueue<VoucherOrder> orderQueue = new ArrayBlockingQueue<>(1024 * 1024);
    //创建一个线程池，用于处理阻塞队列中的订单，不用太复杂，使用单线程池就可以
//    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

//    @PostConstruct
//    public void init() {
//        //该方法保证该类在类刚初始化就加载
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }

    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    //1.获取消息队列中的订单信息 XREADGROUP GROUP 组名字 消费者名字 COUNT 1 BLOCK 2000 STREAMS 消息队列名字 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2.判断消息是否获取成功
                    if (list == null || list.isEmpty()) {
                        //如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    //3.解析消息队列中的订单信息，这里因为知道我自己count写的是1，直接取第一个
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    //把map转为order对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //4.如果有消息，就处理订单
                    handleVoucherOrder(voucherOrder);
                    //5.ACK确认 SACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单 TODO
                    createVoucherOrder(1L);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pendding订单异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            //1.获取用户，不能再向UserHolder中获取，这是多线程，所以不能再使用ThreadLocal获取用户
            Long userId = voucherOrder.getUserId();
            // 2.创建锁对象
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            // 3.尝试获取锁
            boolean isLock = lock.tryLock();
            // 4.判断是否获得锁成功
            if (!isLock) {
                // 获取锁失败，直接返回失败或者重试
                log.error("不允许重复下单！");
                return;
            }
            try {
                //注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
                //TODO
                proxy.createVoucherOrder(1L);
            } finally {
                // 释放锁
                lock.unlock();
            }
        }

    }

    //使用实现类，创建线程任务，该类得在类刚初始化就加载，因为用户随时都可能下单，这是阻塞队列的创建线程任务逻辑
//    private class VoucherOrderHandler implements Runnable {
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    //1.获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderQueue.take();
//                    //2.创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常", e);
//                }
//            }
//        }
//    }

    /**
     * 抢购秒杀优惠券
     * 使用stream实现的消息队列
     * @param voucherId
     * @return
     */
//    public Result seckillVoucher(Long voucherId) {
//        //获取用户id
//        Long userId = UserHolder.getUser().getId();
//        //获取订单id
//        long orderId = redisIdWorker.nextId("order");
//        //1.执行lua脚本，注意格式转换，装箱和基本类型转换用的是不同的方法
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                userId.toString(), voucherId.toString(),
//                String.valueOf(orderId)
//        );
//        //2.判断结果为0
//        if (result.intValue() != 0L) {
//            //不为0，代码没有购买资格
//            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
//        }
//        //获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //返回订单id
//        return Result.ok(orderId);
//    }
     /**
     * 抢购秒杀优惠券
     *
     * @param voucherId
     * @return
     */
//    public Result seckillVoucher_old_2(Long voucherId) {
//        //1.执行lua脚本
//        //闲获取用户id
//        Long userId = UserHolder.getUser().getId();
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                userId.toString(), voucherId.toString()
//        );
//        //2.判断结果为0
//        if (result.intValue() != 0L) {
//            //不为0，代码没有购买资格
//            return Result.fail(result.intValue() == 1 ? "库存不足" : "不能重复下单");
//        }
//        //为0，有购买资格，把下单信息保存到阻塞队列
//        long orderId = redisIdWorker.nextId("order");
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        orderQueue.add(voucherOrder);
//        //获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        //返回订单id
//        return Result.ok(orderId);
//    }

    /**
     * 抢购秒杀优惠券
     * 异步优化之前的代码
     *
     * @param voucherId
     * @return
     */
    public Result seckillVoucher(Long voucherId) {
        //1.根据id查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始以及结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        //3.判断库存是否充足
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        //4.扣减库存，使用乐观锁理念进行解决超卖问题
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")    //set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0)  //where id = ? and stock = ?
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //将用户id的toString后在进行intern处理作为锁对象，必须要保证锁对象唯一
        //如果只判断，intern是会去字符串常量池中找值相同的对象
        Long userId = UserHolder.getUser().getId();
//        //分布式环境可以使用基于setnx实现的自定义锁，也就是redis实现分布式锁方式一
//        //创建自定义基于setnx实现的锁对象，还是要注意锁对象唯一
//        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        //分布式环境使用redisson实现分布式锁方式二
        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //setnx方式的获取锁
//        boolean isLock = lock.tryLock(5);
        //redisson分布式锁的获取锁，无参的默认是失败不等待
        boolean isLock = lock.tryLock();
        //判断锁是否获取成功
        if (!isLock) {
            return Result.fail("请勿重复下单");
        }
        try {
            //获取代理对象，记得要在启动类开启暴露对象为true
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
        //下列被注释代码仅适用于单机操作，因为这种使用的是jvm自带的锁，不适合分布式环境
//        synchronized (userId.toString().intern()) {
//            //拿到事务的代理对象才能让事务生效，在这里代理对象就是IVoucherOrderService，而不是this
//            //获取代理对象，记得要在启动类开启暴露对象为true
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }
    }

    /**
     * 一人一单，从查询订单到创建订单结束都需要加锁，
     * 这里关键是锁的粒度，如果锁整个方法，并发量过大时，锁的冲突会频繁，从而影响性能。
     * 实际上针对的是用户，只需要锁用户id即可，锁的粒度越细，性能越好。但也要注意锁一定要在事务提交之后释放
     * 一定要注意事务如果要生效，必须得用事务的代理对象去调用方法
     * @param voucherId
     * @return
     */
    @Transactional  //事务是在函数执行完后才提交
    public Result createVoucherOrder(Long voucherId) {
        //先查询订单
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过一次");
        }
        //5.若第一次购买，则创建订单并返回订单id
        //注意需要设置订单的用户id，秒杀卷id，订单id
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherOrder.getVoucherId());
        save(voucherOrder);
        return Result.ok(orderId);
    }
}

