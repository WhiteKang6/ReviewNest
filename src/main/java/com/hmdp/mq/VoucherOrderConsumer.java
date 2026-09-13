package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消费者：顺序消费（ORDERLY），同 userId 的消息进同一队列串行消费，
 * 规避并发重复下单。
 * <p>
 * 顺序消费失败会无限重试并阻塞同队列，因此：
 * - 唯一索引命中（DataIntegrityViolationException）视为幂等成功，不重试；
 * - 其它系统异常抛出触发重试，达到 maxReconsumeTimes 后进死信队列，队列解除阻塞。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "seckill_order",
        consumerGroup = "seckill_consumer_group",
        consumeMode = ConsumeMode.ORDERLY,
        maxReconsumeTimes = 5
)
public class VoucherOrderConsumer implements RocketMQListener<VoucherOrder> {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public void onMessage(VoucherOrder order) {
        try {
            voucherOrderService.handleVoucherOrder(order);
        } catch (DataIntegrityViolationException e) {
            // 唯一索引命中 = 幂等重投/误回补后重单，视为成功，不重试（否则阻塞队列）
            log.warn("订单已存在，幂等忽略 orderId={}", order.getId());
        } catch (Exception e) {
            // 系统异常（DB/Redis 宕机等）→ 抛出触发顺序重试，达 5 次后进死信队列
            log.error("消费秒杀订单失败，将重试 orderId={}", order.getId(), e);
            throw new RuntimeException(e);
        }
    }
}
