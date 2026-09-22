package com.hmdp.mq;

import cn.hutool.core.util.BooleanUtil;
import com.hmdp.utils.MqConstants;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 商铺缓存失效补偿消费者。
 * <p>
 * 生产端在"更新数据库成功、删除缓存失败"时投递补偿消息，本消费者负责把脏缓存删掉。
 * 默认并发消费：不同商铺互不影响，同一商铺被重复消费也无副作用（delete 天然幂等）。
 * 消费失败抛异常触发重投，达到 maxReconsumeTimes 后进死信队列；
 * 期间缓存旧值仍受 CACHE_SHOP_TTL 兜底，不会无限存活。
 */
@Component
@Slf4j
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_CACHE_INVALIDATE,
        consumerGroup = MqConstants.CONSUMER_GROUP_CACHE_INVALIDATE,
        maxReconsumeTimes = 5
)
public class ShopCacheInvalidateConsumer implements RocketMQListener<ShopCacheInvalidMsg> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void onMessage(ShopCacheInvalidMsg msg) {
        Long id = msg.getShopId();
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        try {
            Boolean deleted = stringRedisTemplate.delete(key);
            if (BooleanUtil.isTrue(deleted)) {
                log.info("补偿删除商铺缓存成功 shopId={}, 原始失败原因={}", id, msg.getReason());
            } else {
                // 删除数为 0：缓存已不存在（被其它请求删掉或 TTL 过期），视为补偿完成
                log.info("商铺缓存已不存在，无需补偿 shopId={}, 原始失败原因={}", id, msg.getReason());
            }
        } catch (Exception e) {
            // 抛出异常触发重投，直到删成功或进入死信队列
            log.error("补偿删除商铺缓存失败，将重试 shopId={}, 原始失败原因={}", id, msg.getReason(), e);
            throw new RuntimeException(e);
        }
    }
}
