package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    //Result createVoucherOrder(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);

    void handleVoucherOrder(VoucherOrder voucherOrder);

    /**
     * 用户发起支付（模拟支付通道）：校验订单与支付方式后触发状态落实
     */
    Result payVoucherOrder(Long orderId, Integer payType);

    /**
     * 支付结果回调：把订单落成"已支付"，幂等（重复回调视为成功）
     */
    Result payCallback(Long orderId, Integer payType);

    /**
     * 查询当前登录用户自己的订单
     */
    VoucherOrder getVoucherOrderDetail(Long orderId);

    /**
     * 取消未支付订单（幂等）：CAS status 1→4 + 回补 DB/Redis 库存
     */
    boolean cancelVoucherOrder(Long orderId);

    /**
     * 兜底扫描：取消所有超时未支付订单，返回处理数量
     */
    int sweepExpiredOrders();

}
