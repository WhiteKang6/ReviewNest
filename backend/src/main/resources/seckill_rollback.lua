-- 秒杀回补脚本：MQ 发送失败时回滚 Redis 预扣
-- 1.参数列表
-- 1.1优惠券id
local voucherId = ARGV[1]
-- 1.2用户id
local userId = ARGV[2]
-- 2.数据key
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. voucherId
-- 3.业务
-- 3.1.回补库存 +1
redis.call('incrby', stockKey, 1)
-- 3.2.移除用户下单标记
redis.call('srem', orderKey, userId)
return 0
