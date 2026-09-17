# 黑马点评 Redis 数据结构应用总结

> 本文档梳理 hm-dianping 项目中实际用到的 Redis 数据结构（类型）、对应的 Key 模式、应用场景、使用到的命令，以及源码位置。Key 常量统一定义在 `com.hmdp.utils.RedisConstants`。

---

## 一、总览表

| 数据结构 | Key 模式 | 应用场景 | 主要命令 | 源码位置 |
|---|---|---|---|---|
| **String** | `login:code:{phone}` | 短信验证码 | `SET ... EX 120` / `GET` | UserServiceImpl |
| **String** | `login:token:{token}` | 用户会话（以 Hash 形态存） | — | UserServiceImpl / RefreshTokenInterceptor |
| **String** | `cache:shop:{id}` | 商户详情缓存 + 缓存击穿/穿透 | `SET/GET`、空值 `""` | ShopServiceImpl / CacheClient |
| **String** | `cache:shopTypeList` | 商铺类型列表缓存 | `SET/GET` | ShopTypeServiceImpl |
| **String** | `seckill:stock:{voucherId}` | 秒杀库存预扣 | `SET` / `GET` / `INCRBY -1` | VoucherServiceImpl、seckill.lua |
| **String** | `lock:shop:{id}` | 简单分布式锁（互斥重建缓存） | `SET NX EX 10` | ShopServiceImpl / CacheClient / LockImpl |
| **String** | `icr:{keyPrefix}:{date}` | 全局唯一 ID 生成 | `INCR` | RedisIdWorker |
| **String(BitMap)** | `sign:{userId}:yyyyMM` | 用户月度签到 | `SETBIT` / `BITFIELD` | UserServiceImpl |
| **Hash** | `login:token:{token}` | 登录用户信息存储 | `HSETALL` / `HGETALL` | UserServiceImpl / RefreshTokenInterceptor |
| **Set** | `follow:{userId}` | 关注关系 / 共同关注 | `SADD` / `SREM` / `SINTER` | FollowServiceImpl |
| **Set** | `seckill:order:{voucherId}` | 秒杀一人一单去重 | `SISMEMBER` / `SADD` | seckill.lua |
| **ZSet** | `blog:liked:{blogId}` | 点赞排行榜 | `ZADD` / `ZSCORE` / `ZRANGE` | BlogServiceImpl |
| **ZSet** | `feed:{userId}` | 关注推送（Feed 流收件箱） | `ZADD` / `ZREVRANGEBYSCORE` | BlogServiceImpl |
| **Stream** | `stream.orders` | 秒杀异步下单消息队列 | `XADD` / `XREADGROUP` / `XACK` | VoucherOrderServiceImpl、seckill.lua |
| **GEO** | `shop:geo:{typeId}` | 附近商铺（按距离排序） | `GEOADD` / `GEOSEARCH` | ShopServiceImpl（查询）、测试类（灌数据） |
| **Lua** | `unlock.lua` / `seckill.lua` | 原子性解锁 / 原子性秒杀校验 | `EVAL` | LockImpl / VoucherOrderServiceImpl |
| **Redisson** | `lock:order:{userId}` | 可重入分布式锁（一人一单） | `tryLock` / `unlock` | VoucherOrderServiceImpl |
| **HyperLogLog** | `HLL` | UV 统计（仅测试 demo） | `PFADD` / `PFCOUNT` | HmDianPingApplicationTests |

---

## 二、逐项详解

### 1. String

#### 1.1 登录验证码 `login:code:{phone}`
- **场景**：短信登录时存验证码，2 分钟过期。
- **命令**：`SET login:code:{phone} {code} EX 120`，登录时 `GET` 比对。
- **源码**：`UserServiceImpl#login`（写）、`#login` 内 `GET`（校验）。
- **常量**：`LOGIN_CODE_KEY`、`LOGIN_CODE_TTL=2min`。

#### 1.2 商户详情缓存 `cache:shop:{id}`
- **场景**：商铺查询的高频缓存，并实现「缓存穿透（空值缓存）」与「缓存击穿（互斥锁重建 + 逻辑过期）」。
- **命令**：`GET` 取缓存 → 未命中查库 → `SET cache:shop:{id} {json} EX 1800`；查库为空则 `SET cache:shop:{id} "" EX 120`（空值防穿透）。
- **互斥锁**：重建缓存前 `SET lock:shop:{id} 1 NX EX 10` 抢锁，失败则自旋等待。
- **源码**：`ShopServiceImpl`（手写版本）、`CacheClient`（通用封装版本）、`LockImpl`。
- **常量**：`CACHE_SHOP_KEY`、`CACHE_SHOP_TTL=30min`、`CACHE_NULL_TTL=2min`、`LOCK_SHOP_KEY`。

#### 1.3 商铺类型列表缓存 `cache:shopTypeList`
- **场景**：首页商铺分类（美食/休闲等）列表，变动少，整体 JSON 缓存。
- **源码**：`ShopTypeServiceImpl#queryTypeList`。
- ⚠️ 现有实现查到缓存后未 `return`，仍会继续查库（小瑕疵）。

#### 1.4 秒杀库存 `seckill:stock:{voucherId}`
- **场景**：秒杀前置库存。秒杀券上架时把库存写入 Redis，下单时由 Lua 原子扣减。
- **命令**：上架 `SET seckill:stock:{voucherId} {stock}`；Lua 内 `GET` 判断 + `INCRBY -1`。
- **源码**：`VoucherServiceImpl`（写入）、`seckill.lua`（扣减）、`VoucherOrderServiceImpl`。
- **常量**：`SECKILL_STOCK_KEY`。

#### 1.5 全局唯一 ID `icr:{keyPrefix}:{date}`
- **场景**：订单 ID 等全局唯一自增 ID，`前缀 + 日期 + 自增序列`。
- **命令**：`INCR icr:order:20260828`（自增后作为低 32 位）。
- **源码**：`RedisIdWorker#nextId`。

#### 1.6 月度签到（Bitmap） `sign:{userId}:yyyyMM`
- **场景**：用户签到、连续签到天数统计。Bitmap 基于 String 实现，1 个 bit 表示某日是否签到。
- **命令**：签到 `SETBIT sign:{uid}:202608 {day-1} 1`；统计 `BITFIELD ... GET u{day} 0` 取本月截至今日的位，再低位连续 1 计数。
- **源码**：`UserServiceImpl#sign` / `#signCount`。
- **常量**：`USER_SIGN_KEY`。

---

### 2. Hash

#### 2.1 登录用户会话 `login:token:{token}`
- **场景**：登录成功后以 token 为 key，把 `UserDTO` 各字段存入 Hash；后续请求从 Hash 还原 ThreadLocal 用户，并刷新 TTL。
- **命令**：登录 `HSETALL login:token:{token} {userMap}`；拦截器 `HGETALL` 还原；`EXPIRE` 续期。
- **源码**：`UserServiceImpl#login`（写）、`RefreshTokenInterceptor`（读 + 续期）、`LoginInterceptor`。
- **常量**：`LOGIN_USER_KEY`、`LOGIN_USER_TTL=360min`。

---

### 3. Set

#### 3.1 关注关系与共同关注 `follow:{userId}`
- **场景**：关注/取关时维护「我关注了谁」的集合；查共同关注时对两个集合求交。
- **命令**：关注 `SADD follow:{uid} {followUserId}`；取关 `SREM follow:{uid} {followUserId}`；共同关注 `SINTER follow:{uid1} follow:{uid2}`。
- **源码**：`FollowServiceImpl#follow` / `#getCommonFollow`（含缓存未命中回源重建）。
- ⚠️ 取关时原代码误删了 `userId.toString()`（应为 `followUserId.toString()`），已修复。

#### 3.2 秒杀一人一单去重 `seckill:order:{voucherId}`
- **场景**：Lua 脚本中判断该用户是否已下过该秒杀券，已下则拒绝，防并发重复下单。
- **命令**：`SISMEMBER seckill:order:{voucherId} {userId}` 判断；`SADD ... {userId}` 记录。
- **源码**：`seckill.lua`。

---

### 4. ZSet（Sorted Set）

#### 4.1 点赞排行榜 `blog:liked:{blogId}`
- **场景**：探店笔记点赞，一人一赞，并按点赞时间戳排序展示「最早点赞 Top5」。
- **命令**：点赞 `ZADD blog:liked:{blogId} {timestamp} {userId}`；取消赞 `ZREM`；判是否赞过 `ZSCORE`；Top5 `ZRANGE ... 0 4`。
- **源码**：`BlogServiceImpl#likeBlog` / `#queryBlogLikes` / `#isLiked`。
- **常量**：`BLOG_LIKED_KEY`。
- 备注：早期用 Set（`SISMEMBER`）实现，后改为 ZSet 以支持排行榜（注释中保留旧实现）。

#### 4.2 关注推送 Feed 流 `feed:{userId}`
- **场景**：发布笔记时推送到所有粉丝的收件箱；粉丝拉取时按时间戳滚动分页（实现 Feed 流的「推」模式 + 滚动分页）。
- **命令**：推送 `ZADD feed:{粉丝uid} {timestamp} {blogId}`；拉取 `ZREVRANGEBYSCORE` 带 `LIMIT` + `WITHSCORES`，配合 score 相同的 offset 实现滚动分页。
- **源码**：`BlogServiceImpl#saveBlog`（推送）、`#queryBlogOfFollow`（拉取）。
- **常量**：`FEED_KEY`。

---

### 5. Stream

#### 5.1 秒杀异步下单消息队列 `stream.orders`
- **场景**：秒杀前置校验（库存 + 一人一单）通过后，把订单信息写入 Stream；后台消费线程组读取并异步落库，实现下单与订单创建解耦。
- **结构**：消费者组 `g1`，消费者 `c1`。
- **命令**：
  - 生产：Lua 内 `XADD stream.orders * userId .. voucherId .. id ..`。
  - 消费：`XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >`。
  - 确认：`XACK stream.orders g1 {id}`。
  - 异常恢复：`XREADGROUP ... STREAMS stream.orders 0` 读 pending-list 重处理。
- **源码**：`VoucherOrderServiceImpl$VoucherOrderHandler`（消费）、`seckill.lua`（生产）。

> ⚠️ **潜在 Bug**：`seckill.lua` 第 33 行用的是 `redis.call("sadd","stream.orders",'*'...)`，应为 `redis.call("xadd","stream.orders",'*'...)`。`sadd` 会把 `stream.orders` 变成一个 **Set**，导致后续 `XREADGROUP` 抛 `WRONGTYPE`，真正下秒杀单时消费端收不到消息。当前未触发是因为尚未真实下单。建议修正为 `xadd`。

---

### 6. GEO

#### 6.1 附近商铺 `shop:geo:{typeId}`
- **场景**：按商铺类型 + 当前坐标查询 5km 内、按距离排序的附近商铺（分页）。
- **命令**：灌数据 `GEOADD shop:geo:{typeId} {lng} {lat} {shopId}`；查询 `GEOSEARCH shop:geo:{typeId} FROMLONLAT x y BYRADIUS 5000 m WITHDIST LIMIT end`。
- **源码**：`ShopServiceImpl#queryShopByType`（查询，`opsForGeo().search`）、`HmDianPingApplicationTests`（GEOADD 灌入坐标）。
- **常量**：`SHOP_GEO_KEY`。

---

### 7. Lua 脚本（原子性保证）

| 脚本 | 作用 | 涉及 Key/命令 | 源码 |
|---|---|---|---|
| `seckill.lua` | 原子完成「判库存 → 判重复 → 扣库存 → 记录用户 → 写消息队列」 | `seckill:stock:`(GET/INCRBY)、`seckill:order:`(SISMEMBER/SADD)、`stream.orders`(XADD) | VoucherOrderServiceImpl |
| `unlock.lua` | 原子完成「判断锁标识一致再删除」，避免误删别人的锁 | `lock:shop:`(GET/DEL) | LockImpl |

> Lua 脚本通过 `stringRedisTemplate.execute(SCRIPT, keys, args...)` 调用，保证多步操作的原子性（解决并发下的判断与执行之间的时间窗口问题）。

---

### 8. Redisson（可重入分布式锁）

#### 8.1 一人一单锁 `lock:order:{userId}`
- **场景**：下单时按用户加锁，防止并发下同一用户重复下单。相比手写 `SETNX` 锁，Redisson 提供可重入、看门狗续期、阻塞/超时获取等能力。
- **用法**：`RLock lock = redissonClient.getLock("lock:order:" + userId); lock.tryLock(); ... lock.unlock();`
- **源码**：`VoucherOrderServiceImpl#handleVoucherOrder`（及注释中的旧版 `seckillVoucher`）。
- 配置：`RedissonConfig`。

---

### 9. HyperLogLog（仅测试 demo）

- **场景**：去重统计 UV。测试类中把多个用户加入 `HLL`，再用 `PFCOUNT` 估算独立访客数。
- **命令**：`PFADD HLL {user...}` / `PFCOUNT HLL`。
- **源码**：`HmDianPingApplicationTests`（运行时主流程未使用）。

---

## 三、缓存策略与模式小结

项目围绕商铺查询实践了三类经典缓存问题：

| 问题 | 解决方案 | 实现 |
|---|---|---|
| 缓存穿透 | 查库为空时缓存空值 `""` + 短 TTL（2min） | ShopServiceImpl / CacheClient |
| 缓存击穿 | 互斥锁（`SETNX`）重建缓存；或逻辑过期（后台异步刷新） | ShopServiceImpl / CacheClient |
| 缓存一致性 | 缓存未命中回源 + 重建（如共同关注的 `hasKey` 判断 + DB 回源） | FollowServiceImpl |

秒杀链路则综合运用了 **String(库存) + Set(去重) + Stream(异步队列) + Lua(原子校验) + Redisson(分布式锁)**，是 Redis 多数据结构协同的典型场景。

---

## 四、已发现并处理的问题

1. **`FollowServiceImpl` 取关误删字段**：`SREM follow:{uid} userId`（误删自己的 id）→ 已改为 `followUserId`；并新增缓存未命中回源重建逻辑。
2. **`seckill.lua` 写 Stream 命令错误**：`sadd` 应为 `xadd`（见 §5.1），建议修正。
3. **`VoucherOrderServiceImpl` 异步消费线程无优雅关闭**：应用停止时线程仍在访问已 stop 的 Lettuce 连接工厂，刷屏报错 → 已加 `running` 标志 + `@PreDestroy` 关闭 + catch 中 `IllegalStateException` 退出。
4. **`BlogServiceImpl` 循环依赖**：`BlogServiceImpl` 自注入 `IBlogService` → 已移除，直接调用继承的 `save()`。
