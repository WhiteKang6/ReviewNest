package com.hmdp.mq;

import com.hmdp.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单超时取消消费者：接收下单时预约的延迟消息，到期把未支付订单取消。
 * <p>
 * 幂等：cancelVoucherOrder 内部 CAS（status 1→4），重复消费、与兜底扫描并发都只会生效一次；
 * 系统异常（DB/Redis 宕机）抛出触发重投，与兜底扫描互为补偿。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = "order_timeout",
        consumerGroup = "order_timeout_group"
)
public class VoucherOrderTimeoutConsumer implements RocketMQListener<Long> {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Override
    public void onMessage(Long orderId) {
        if (!voucherOrderService.cancelVoucherOrder(orderId)) {
            log.info("延迟取消消息：订单无需取消 orderId={}", orderId);
        }
    }
}
