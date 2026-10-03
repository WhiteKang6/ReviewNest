package com.hmdp.utils;

public class SystemConstants {
    // 前端重构后上传目录随示例图片一起迁至 frontend/public/imgs:
    // dev 由 vite 直接服务 public/ 目录,build 时 public → dist 拷贝(emptyOutDir 只清 dist,上传图片跨构建存活)
    public static final String IMAGE_UPLOAD_DIR = "C:\\code\\java\\redis-study\\ReviewNest\\frontend\\public\\imgs";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;

    /**
     * 支付方式 1：余额支付；2：支付宝；3：微信
     */
    public static final int PAY_TYPE_BALANCE = 1;
    public static final int PAY_TYPE_ALIPAY = 2;
    public static final int PAY_TYPE_WECHAT = 3;

    /**
     * 订单状态 1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款
     */
    public static final int ORDER_STATUS_UNPAID = 1;
    public static final int ORDER_STATUS_PAID = 2;
    public static final int ORDER_STATUS_USED = 3;
    public static final int ORDER_STATUS_CANCELLED = 4;
    public static final int ORDER_STATUS_REFUNDING = 5;
    public static final int ORDER_STATUS_REFUNDED = 6;

    /**
     * 未支付订单自动取消：支付超时窗口（分钟）
     */
    public static final int ORDER_PAY_TIMEOUT_MINUTES = 15;

    /**
     * 订单超时检查延迟消息档位：对应 broker.conf messageDelayLevel 第 15 档（15m），两者必须一致
     */
    public static final int DELAY_LEVEL_ORDER_TIMEOUT = 15;

    /**
     * 兜底扫描周期：延迟消息丢失/发送失败时的补偿频率
     */
    public static final long ORDER_CANCEL_SWEEP_MS = 60_000;
}
