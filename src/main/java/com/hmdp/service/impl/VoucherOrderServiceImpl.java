package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.LockImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.NonNull;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RocketMQTemplate rocketMQTemplate;
    // 自注入：走 AOP 代理，保证内部调用的 createVoucherOrder(@Transactional) 事务生效
    @Lazy
    @Resource
    private IVoucherOrderService self;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static{
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // MQ 发送失败时回补 Redis 预扣的脚本
    private static final DefaultRedisScript<Long> SECKILL_ROLLBACK_SCRIPT;
    static{
        SECKILL_ROLLBACK_SCRIPT = new DefaultRedisScript<>();
        SECKILL_ROLLBACK_SCRIPT.setLocation(new ClassPathResource("seckill_rollback.lua"));
        SECKILL_ROLLBACK_SCRIPT.setResultType(Long.class);
    }

    /* ===================== Redis Stream 异步下单方案（已用 RocketMQ 替代，代码注释保留对照） =====================
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    // 非static：@Service为单例，实例字段即可；避免DevTools热重启时旧线程池被关闭后新任务无法提交
    private final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    @PreDestroy
    private void shutdown(){
        SECKILL_ORDER_EXECUTOR.shutdownNow();
        try {
            if (!SECKILL_ORDER_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("订单处理线程未在5秒内结束");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private class VoucherOrderHandler implements Runnable{
        private final String queueName = "stream.orders";
        @Override
        public void run() {
            while(!Thread.currentThread().isInterrupted()){
                try{
                    //1.获取队列中的订单信息
                    //VoucherOrder voucherOrder = orderTasks.take();
                    //2.创建订单
                    //handleVoucherOrder(voucherOrder);


                    // 0.初始化stream
                    initStream();
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    handleVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    //应用关闭时Redis连接工厂被销毁会抛IllegalStateException，此时直接退出，避免刷屏
                    if (e instanceof IllegalStateException) {
                        break;
                    }
                    log.error("处理订单异常", e);
                    handlePendingList();
                }
            }

        }
        public void initStream(){
            Boolean exists = stringRedisTemplate.hasKey(queueName);
            if (BooleanUtil.isFalse(exists)) {
                log.info("stream不存在，开始创建stream");
                // 不存在，需要创建
                stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.latest(), "g1");
                log.info("stream和group创建完毕");
                return;
            }
            // stream存在，判断group是否存在
            StreamInfo.XInfoGroups groups = stringRedisTemplate.opsForStream().groups(queueName);
            if(groups.isEmpty()){
                log.info("group不存在，开始创建group");
                // group不存在，创建group
                stringRedisTemplate.opsForStream().createGroup(queueName, ReadOffset.latest(), "g1");
                log.info("group创建完毕");
            }
        }

        private void handlePendingList() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    handleVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    //Redis连接停止/应用关闭时抛IllegalStateException，直接退出，避免在handlePendingList内死循环刷屏
                    if (e instanceof IllegalStateException) {
                        break;
                    }
                    log.error("处理订单异常", e);
                }
            }
        }
    }
    private IVoucherOrderService proxy ;
    private void handleVoucherOrder(VoucherOrder voucherOrder){
        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("您已购买过该优惠券");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }
    }
    ===================== Redis Stream 方案结束 ===================== */

    @Override
    public Result seckillVoucher(Long voucherId){
        //1.查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //1.1优惠券不存在
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        //2.判断优惠券是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //2.1 没开始，返回异常
            return Result.fail("秒杀未开始");
        }
        //2.2 判断是否过期
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //2.3 过期，返回异常
            return Result.fail("秒杀已过期");
        }
        //1.获取userId
        Long userId = UserHolder.getUser().getId();
        //订单id
        Long orderId = redisIdWorker.nextId("order");
        //2.执行lua脚本：判断库存 + 一人一单 + 预扣库存（不再 XADD 入 Redis Stream）
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.EMPTY_LIST,
                voucherId.toString(), userId.toString(),orderId.toString());
        //3.判断结果是否为0
        int r = result.intValue();
        if(r != 0){
            //3.1.否，返回异常信息
            return Result.fail(r ==1?"库存不足":"重复下单");
        }

        //4.构造订单（与原逻辑一致：仅设 id/userId/voucherId）
        VoucherOrder voucherOrder = new VoucherOrder()
                .setId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId);

        //5.同步顺序发送 MQ：同 userId 走同一队列，消费端串行处理，规避并发重复下单
        try {
            SendResult sendResult = rocketMQTemplate.syncSendOrderly(
                    "seckill_order",
                    MessageBuilder.withPayload(voucherOrder).build(),
                    String.valueOf(userId));
            if (sendResult == null || sendResult.getSendStatus() != SendStatus.SEND_OK) {
                //5.1.发送失败：回补 Redis 预扣，返回失败，用户可重试
                rollbackRedis(voucherId, userId);
                return Result.fail("下单失败，请重试");
            }
        } catch (Exception e) {
            log.error("MQ 发送异常，执行回补 userId={}, voucherId={}", userId, voucherId, e);
            rollbackRedis(voucherId, userId);
            return Result.fail("下单失败，请重试");
        }

        //6.返回订单id
        return Result.ok(orderId);
    }

    // ---------- 原 seckillVoucher（Redis Stream 生产端，已注释保留对照） ----------
//    public Result seckillVoucher(Long voucherId){
//        //1.获取userId
//        Long userId = UserHolder.getUser().getId();
//        //订单id
//        Long orderId = redisIdWorker.nextId("order");
//        //2.执行lua脚本
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                Collections.EMPTY_LIST,
//                voucherId.toString(), userId.toString(),orderId.toString());
//        //3.判断结果是否为0
//        int r = result.intValue();
//        if(r != 0){
//            //3.1.否，返回异常信息
//            return Result.fail(r ==1?"库存不足":"重复下单");
//        }
//
//        /*//3.2.是，将优惠券id,用户id,订单id存入阻塞队列
//        //3.3. 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //用户id
//        voucherOrder.setUserId(UserHolder.getUser().getId());
//        //优惠券id
//        voucherOrder.setVoucherId(voucherId);
//        //订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //填入阻塞队列
//        orderTasks.offer(voucherOrder);*/
//
//        //创建代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        //4.返回订单id
//        return Result.ok(orderId);
//    }
    // -----------------------------------------------------------------------

    /**
     * 发送失败时回补 Redis 预扣（库存 +1、移除用户下单标记）。
     * 原子脚本；若回补本身也失败（Redis 不可用），记录日志等待人工对账。
     */
    private void rollbackRedis(Long voucherId, Long userId) {
        try {
            stringRedisTemplate.execute(SECKILL_ROLLBACK_SCRIPT,
                    Collections.EMPTY_LIST,
                    voucherId.toString(), userId.toString());
        } catch (Exception ex) {
            log.error("回补 Redis 失败！需人工对账 voucherId={}, userId={}", voucherId, userId, ex);
        }
    }

    @Override
    public void handleVoucherOrder(VoucherOrder voucherOrder){
        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象（兜底一人一单，防 MQ 重投）
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("您已购买过该优惠券");
            return;
        }
        try {
            //3.经代理调用，@Transactional 才会生效
            self.createVoucherOrder(voucherOrder);
        } finally {
            //4.释放锁
            lock.unlock();
        }
    }

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //1.1优惠券不存在
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        //2.判断优惠券是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //2.1 没开始，返回异常
            return Result.fail("秒杀未开始");
        }
        //2.2 判断是否过期
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //2.3 过期，返回异常
            return Result.fail("秒杀已过期");
        }

        //3. 开始，判断库存是否足够
        if (voucher.getStock() <= 0) {
            //3.1 不充足，返回异常
            return Result.fail("库存不足");
        }
        // 悲观锁，单体有效，分布式无效
        Long userId = UserHolder.getUser().getId();
//        synchronized (userId.toString().intern()) {
//            //获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }

        //redis锁，分布式有效
        //LockImpl lock = new LockImpl("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean success = lock.tryLock();
        if (!success) {
            return Result.fail("您已购买过该优惠券");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);

        } finally {
            //释放锁
            lock.unlock();
        }
    }*/

    /*@Transactional
    public Result createVoucherOrder(Long voucherId) {
        //4.一人一单
        //4.1查询订单存在吗
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("您已购买过该优惠券");
        }

        //5. 充足，扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            //3.2.1 扣减失败，返回异常
            return Result.fail("库存不足");
        }
        //6. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        //优惠券id
        voucherOrder.setVoucherId(voucherId);
        //订单id
        voucherOrder.setId(redisIdWorker.nextId("voucher_order"));

        save(voucherOrder);
        //7.返回订单
        return Result.ok(voucherOrder.getId());

    }*/
    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //4.一人一单
        //4.1查询订单存在吗
        Long userId = voucherOrder.getUserId();
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if (count > 0) {
            log.error("您已购买过该优惠券");
            return;
        }

        //5. 充足，扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            //3.2.1 扣减失败，返回异常
            log.error("库存不足");
            return;
        }
        //6. 创建订单（命中唯一索引会抛 DataIntegrityViolationException，由消费者幂等处理）
        save(voucherOrder);
    }
}
