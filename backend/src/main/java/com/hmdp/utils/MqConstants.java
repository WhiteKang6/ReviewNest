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
}
