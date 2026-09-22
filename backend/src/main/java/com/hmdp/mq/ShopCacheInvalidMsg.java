package com.hmdp.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 商铺缓存失效补偿消息。
 * <p>
 * 生产端在"数据库已更新成功、缓存删除失败"时投递，消费端据此把脏缓存删掉，
 * 让缓存最终与数据库一致。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class ShopCacheInvalidMsg implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商铺id
     */
    private Long shopId;

    /**
     * 生产端删除缓存失败的原因，便于排查与死信队列回溯
     */
    private String reason;
}
