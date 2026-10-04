# 优惠券订单（VoucherOrder）规格

> 对应 `backend/src/main/java/com/hmdp/controller/VoucherOrderController.java` / `service/impl/VoucherOrderServiceImpl.java`
> 每轮开工先读本文件 + 根目录 `CONSTRAINTS.md`。本模块是秒杀系统的核心，注释里保留了 4 代演进实现，**不要删除对照注释**。

## 职责
一句话：秒杀券的**异步下单**、**订单支付状态落实**、**未支付订单超时自动取消**。
链路：Lua 原子判断（库存 + 一人一单 + 预扣）→ RocketMQ 顺序消息异步落库 → Redisson 锁兜底 → CAS 落实支付 / 取消状态。

## 明确不负责什么
- 不负责优惠券的增删改查（那是 `VoucherServiceImpl` / `VoucherController`）
- 不负责秒杀券的创建与库存预热（`VoucherServiceImpl.addSeckillVoucher`，事务内写 Redis，失败则整体回滚）
- 不负责核销（`3 已核销`）与退款（`5 退款中` / `6 已退款`）——当前**无代码入口**，`use_time` / `refund_time` 恒为 null
- 不负责支付通道——`/callback` 仍走登录校验、mock 同步调回调，真实回调应改签名校验（已知债）

## 输入 / 输出（Result 无 code 字段，失败看 `success=false` + `errorMsg`）

### POST /voucher-order/seckill/{id}
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| id（voucherId） | Long | 是 | 已存在且处于秒杀时间窗内的秒杀券 |

成功：`{ success: true, data: orderId }`（订单 ID 由 `RedisIdWorker.nextId("order")` 生成）
失败（errorMsg）：`优惠券不存在` / `秒杀未开始` / `秒杀已过期` / `库存不足` / `重复下单` / `下单失败，请重试`

### POST /voucher-order/pay/{id}?payType={1..3}
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| id（orderId） | Long | 是 | 属于当前登录用户 |
| payType | Integer | 否（默认 1） | 1 余额 / 2 支付宝 / 3 微信 |

成功：`{ success: true, data: "支付成功" }`
失败（errorMsg）：`支付方式不合法` / `订单不存在` / 当前状态文案（`订单已支付，请勿重复支付` 等，见 `orderStatusMsg`）

### POST /voucher-order/callback/{id}?payType={1..3}
幂等：重复回调返回 `data: "订单已支付"`。字段约束同上。
成功：`"支付成功"` / `"订单已支付"`（幂等重复）
失败：`支付方式不合法` / `订单不存在` / 状态文案

### GET /voucher-order/detail/{id}
成功：`{ success: true, data: VoucherOrder }`（只返回当前用户自己的订单；越权查他人订单 → `fail("订单不存在")`）
失败：`订单不存在`

## 前置条件
- 用户必须已登录（`UserHolder.getUser()` 非空）——三个写接口都在 `MvcConfig` 登录白名单外
- 秒杀券必须已创建并预热库存（Redis key `seckill:stock:{voucherId}` 存在）
- `tb_voucher_order` 已有 `idx_status_create_time` 索引（兜底扫描 `sweepExpiredOrders` 依赖）

## 边界清单

**下单**
- voucherId 不存在 / 秒杀未开始 / 已过期 → 对应 errorMsg
- 库存 0 或预扣耗尽 → `库存不足`（Lua 返回 1）
- 同用户对同券重复下单 → `重复下单`（Lua 返回 2）
- 同用户并发两笔 → 四道防线：Lua `SISMEMBER`+`SADD` 原子、MQ 顺序队列同 userId 串行、Redisson `lock:order:{userId}` 兜底、DB count 判重
- MQ `syncSendOrderly` 失败 → `seckill_rollback.lua` 回补库存 + 移除标记 → 返回 `下单失败，请重试`；回补也失败 → 落 error 日志人工对账
- 消费端重复投递同一 orderId → 幂等忽略（判重 + 锁，无唯一索引——与"取消后可重买"互斥，见 CONSTRAINTS §五）
- **取消后重买同券 → 允许**（判重只统计有效订单 `status IN (1,2,3)`）

**支付 / 回调**
- payType 越界 / 为 null → `支付方式不合法`
- 订单不存在 / 非本人订单 → `订单不存在`
- 订单非未支付 → 返回状态文案，不改状态
- 重复回调 / 并发双击 → CAS `WHERE id=? AND status=1` 只命中一次，`pay_time` 只写一次
- 支付与超时取消并发 → 行级 CAS 互斥，谁先改到谁赢，无需分布式锁
- 已取消订单再回调 → 拒绝（不允许把取消的订单改回已支付）

**超时取消**
- 延迟消息（第 15 档 = 15m）到达时订单已支付 / 已取消 → CAS 命中 0 行，幂等跳过，库存不重复回补
- 兜底扫描（`@Scheduled` 60s）与延迟消息并发 → 同上，互不干扰
- 延迟消息发送失败 → 只记日志（发送在事务提交前，失败不阻塞下单），兜底扫描补偿，最坏 15m + 60s 内取消
- 取消回补 Redis 失败 → **刻意不吞异常**：上抛 → 事务回滚 → 消息重投 / 兜底重扫自愈（与下单侧 `rollbackRedis` 吞异常靠日志对账的语义相反，两方法注释已区分）

## 状态机（`tb_voucher_order.status`，列默认 1）

```
1 未支付 ──pay/callback CAS──▶ 2 已支付
1 未支付 ──超时取消 CAS──────▶ 4 已取消
4 已取消 ──重买──▶ 生成新订单行（新 id，状态重新从 1 开始）

当前无代码入口：3 已核销 / 5 退款中 / 6 已退款
非法迁移（一律拒绝，需有测试）：2→1、2→4、4→2、3→2、任何 → 5/6
```

## 验收标准
- [ ] 上表每个边界都有对应用例和断言（L1 逻辑层 / L2 接口层）
- [ ] 并发场景有对账断言：N 并发下单后 `seckill:stock` 预扣值 + `tb_seckill_voucher.stock` 总和守恒、无人重复获单、无超卖
- [ ] 每条非法状态迁移都被拒绝且有测试覆盖
- [ ] "取消后可重买"有对应用例（同 user + voucher 出现第二行订单）
- [ ] 幂等用例：重复回调 / 重复消费 / 取消与支付并发，库最终状态唯一、回补只发生一次
