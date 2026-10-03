package com.hmdp.utils;

/**
 * 消息队列相关常量
 */
public class MqConstants {

    /**
     * 缓存失效补偿主题：数据库已更新、缓存删除失败时投递
     */
    public static final String TOPIC_CACHE_INVALIDATE = "cache_invalidate";

    /**
     * 缓存失效补偿消费者组
     */
    public static final String CONSUMER_GROUP_CACHE_INVALIDATE = "cache_invalidate_group";

    /**
     * 订单超时取消主题：下单后投递延迟消息，到期消费检查并取消未支付订单
     */
    public static final String TOPIC_ORDER_TIMEOUT = "order_timeout";

    /**
     * 订单超时取消消费者组
     */
    public static final String CONSUMER_GROUP_ORDER_TIMEOUT = "order_timeout_group";
}
