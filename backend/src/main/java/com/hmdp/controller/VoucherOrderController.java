package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("/seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    /**
     * 模拟支付：payType 1 余额 / 2 支付宝 / 3 微信，默认余额支付
     */
    @PostMapping("/pay/{id}")
    public Result payVoucherOrder(@PathVariable("id") Long orderId,
                                  @RequestParam(value = "payType", defaultValue = "1") Integer payType) {
        return voucherOrderService.payVoucherOrder(orderId, payType);
    }

    /**
     * 支付结果回调（幂等）：重复回调返回"订单已支付"，可用来验证 CAS 防重复落状态
     */
    @PostMapping("/callback/{id}")
    public Result payCallback(@PathVariable("id") Long orderId,
                              @RequestParam(value = "payType", defaultValue = "1") Integer payType) {
        return voucherOrderService.payCallback(orderId, payType);
    }

    /**
     * 查询当前用户自己的订单，用来核对支付状态是否落实
     */
    @GetMapping("/detail/{id}")
    public Result getVoucherOrderDetail(@PathVariable("id") Long orderId) {
        VoucherOrder order = voucherOrderService.getVoucherOrderDetail(orderId);
        return order == null ? Result.fail("订单不存在") : Result.ok(order);
    }
}
