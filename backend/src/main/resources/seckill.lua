-- 秒杀脚本
-- 1.参数列表
-- 1.1优惠券id
local voucherId = ARGV[1]
-- 1.2用户id
local userId = ARGV[2]
-- 1.3订单id
local id=ARGV[3]
-- 2.数据key
-- 2.1.库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2.订单key
local orderKey = 'seckill:order:' .. voucherId
-- 3.脚本业务
-- 3.1.判断库存是否充足 get stockKey
-- 3.2.库存不足，返回1
local stock = redis.call('get', stockKey)
-- 原写法：stockKey 缺失时 redis.call 返回 false → tonumber 得 nil → nil<=0 抛 "attempt to compare nil with number"
-- if (tonumber(redis.call('get', stockKey),10) <= 0) then
if (not stock or tonumber(stock,10) <= 0) then
    return 1
end
-- 3.2.判断用户是否下单 SISMEMBER orderKey userId
-- 3.3.存在，说明是重复下单，返回2
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 3.4.扣库存 incrby stockKey -1
redis.call('incrby',stockKey,-1)
-- 3.5.下单（保存用户）sadd orderKey userId
redis.call('sadd',orderKey,userId)

return 0



