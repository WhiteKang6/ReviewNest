# 项目约束（AI 每次开工前必读）

> 依据《AI开发测试闭环落地指南.md》维护。**每轮开工先读本文件 + 对应模块 `docs/specs/*.SPEC.md`**，再动手写代码。
>
> **维护纪律（闭环核心）**：每修完一个 bug，强制追问 AI 一句——
> *"这条错误说明 CONSTRAINTS.md 缺了哪条约束？给出要追加的原文。"*
> AI 不知道 → 补进对应章节；AI 知道但没遵守 → 说明约束没被读到，提升到每轮必读的显式提示词。
> 第五节历史教训**只增不删**。

---

## 一、已有能力：禁止重复造轮子

> AI 遇到需求第一反应是"写一个"，必须强制它先查这里。用现成的，不要新建。

### 统一响应与异常
- 接口返回统一走 `com.hmdp.dto.Result`（`Result.ok(data)` / `Result.fail(msg)`），禁止返回裸对象 / 裸 Map
- 业务失败用 `Result.fail(msg)` 返回，**不抛异常**；系统异常抛 `RuntimeException`，由 `config.WebExceptionAdvice` 统一兜底
- 全局异常只兜 `RuntimeException`（受检异常会走 Spring 默认错误页返回非 JSON）→ 受检异常必须自己包成 `RuntimeException` 再抛

### 缓存（Redis）
- 统一走 `utils.CacheClient`：
  - 穿透防护：`queryWithPassThrough(keyPrefix, id, type, dbFallback, ttl, unit)` —— 查库为空也缓存空串 + `CACHE_NULL_TTL`
  - 逻辑过期：`queryWithLogic(...)` —— 永不击穿，返回可能旧数据
  - 写入：`set(...)` / `setWithLogicExpire(...)`
- Redis key 常量统一进 `utils.RedisConstants`，禁止裸字符串

### 分布式锁 / 幂等
- 分布式锁统一用 **Redisson `RLock`**（`redissonClient.getLock("lock:xxx:"+id)`），禁止用 `utils.LockImpl`（static `ID_PREFIX` 缺陷，见第五节）
- 状态变更幂等统一用 **CAS 条件 UPDATE**（`WHERE id=? AND status=?`），命中 0 行视为"幂等成功 / 无需处理"，不要抛异常

### ID 生成
- 订单等分布式 ID 统一 `utils.RedisIdWorker.nextId(prefix)`，禁止数据库自增 / 随机数当业务主键

### MQ（RocketMQ）
- 统一 `RocketMQTemplate`；topic 常量进 `utils.MqConstants`（已有 `TOPIC_CACHE_INVALIDATE` / `TOPIC_ORDER_TIMEOUT`）
  - ⚠ `seckill_order` 目前是生产端 / 消费端各写一次的字面量（README §5.7 已知违规）——**新增 topic 一律进常量类**
- 消息体类放 `mq/` 包（如 `ShopCacheInvalidMsg`）
- 消费端幂等原则：唯一索引冲突 / CAS 命中 0 行 / `delete` 返回 false 都视为成功，不重试
- 顺序消费失败会阻塞同队列 → 业务可判定的"重复"必须当成功吞掉，只对系统异常抛重试

### 登录用户
- 当前登录用户统一 `utils.UserHolder.getUser().getId()`，**禁止**从请求参数 / 路径拿 userId 拼业务（防越权）

### 其它工具
- Bean 拷贝 / JSON / 字符串：Hutool `BeanUtil` / `JSONUtil` / `StrUtil` / `BooleanUtil` / `RandomUtil`
- 手机号校验：`utils.RegexUtils` / `utils.RegexPatterns`
- 时间：`java.time.LocalDateTime`，禁止 `new Date()` 手搓
- ⚠ `utils.PasswordEncoder` 是**孤儿类**（密码登录未实现），不要引用
- Lua 脚本：`seckill.lua`（判库存 + 一人一单 + 预扣）/ `seckill_rollback.lua`（回补）/ `unlock.lua`（安全解锁）。新增原子需求优先扩 Lua，别改成 Java 分步判断

---

## 二、技术栈与版本（防幻觉）

> AI 会"猜"包名和版本号，这里给它唯一真相源。写依赖 / 换 API 前先核对这个清单。

| 层 | 选型 | 版本 | 备注 |
|---|---|---|---|
| 语言 / 运行时 | Java | 17（pom 声明；本机实际 21 可编译） | Spring Boot 3.2.0 |
| ORM | MyBatis-Plus | 3.5.5 | `mybatis-plus-spring-boot3-starter`；Service 统一继承 `ServiceImpl<Mapper, Entity>` |
| 数据库 | MySQL | — | 库 `hmdp`，`root/root123`（application.yaml 硬编码） |
| 缓存 | Spring Data Redis + lettuce 连接池 | Boot 管理 | `commons-pool2`；Redis 无密码 |
| 分布式锁 | Redisson | 3.27.2 | `RedissonConfig` 硬编码 `redis://127.0.0.1:6379`，独立于 `spring.data.redis` |
| 消息队列 | rocketmq-spring-boot-starter | 2.3.6 | 服务端 5.3.1（Docker，broker.conf 硬编码局域网 IP） |
| 工具 | Hutool | 5.8.27 | |
| 简化 | Lombok | Boot 管理 | `@Data` / `@Slf4j` |
| 前端 | Vue 3 + Vite | 3.5 / 8 | Element Plus + axios + vue-router 4；nginx 托管 `dist/` |

**禁止引入**：Spring Security / JWT 框架 / 其它 MQ 客户端 / 其它分布式锁组件 / 日期格式化类库（现有能力已覆盖，别叠新轮子）。

---

## 三、依赖方向（防架构腐化）

- `controller` → `service` → `mapper`，controller 只做路由和参数绑定，**不写业务**
- `entity` / `dto` 纯数据；`entity` ↔ `dto` 用 Hutool `BeanUtil.copyProperties`
- `utils` 不依赖 controller / service；常量类（`RedisConstants` / `SystemConstants` / `MqConstants`）是唯一常量来源
- 反向依赖一律拒绝

---

## 四、编码硬规则

1. **状态变更必须带 WHERE 条件（CAS）**：`UPDATE ... SET status=? WHERE id=? AND status=旧值`。禁止无条件 UPDATE 覆盖状态；单条 UPDATE 自带原子性，无需分布式锁 / 事务。
2. **库存扣减必须带 `.gt("stock", 0)` 条件**：`setSql("stock = stock - 1").gt("stock", 0)`，防超卖。
3. **事务内调用 `@Transactional` 方法必须走 `self` 自注入代理**（`@Lazy @Resource private IVoucherOrderService self;`），禁止 `this.xxx()`（事务失效）和 `AopContext.currentProxy()`（静态上下文耦合）。
4. **Redis 写操作禁止放进数据库事务**：缓存操作必须在事务提交之后。事务内只做 DB；缓存删除失败转 MQ 补偿。
5. **接口统一返回 `Result`**，业务失败 `Result.fail(msg)`；不返回裸 null 对象。
6. **所有常量进常量类**，禁止裸字符串（`"lock:shop:"` / `"follow:"` / `"cache:shopTypeList"` / `"seckill_order"` 等均为现有违规）。
7. **分布式 ID 用 `RedisIdWorker`**，禁止手搓雪花 / 随机 / 自增。
8. **订单状态 / 支付方式用 `SystemConstants` 常量**（`ORDER_STATUS_*` / `PAY_TYPE_*`），禁止裸写 `1` / `2`。
9. **MQ 发送失败必须有补偿路径**：回补（Lua）或落日志人工对账，禁止静默丢弃。
10. **RocketMQ 延迟消息档位必须与 `broker.conf` 的 `messageDelayLevel` 一致**：`DELAY_LEVEL_ORDER_TIMEOUT=15` ↔ 第 15 档 = 15 分钟。改档位需重启 broker，改完两边同步。
11. **涉及对外字段类型 / 状态常量变更，必须先改 SPEC 并同步调用方**（前端、MQ 消费者）。

---

## 五、历史教训（每修完一个 bug 后追加，永不删除）

### 已沉淀
- [2026-10-04] Lua 里 `tonumber(get(stockKey), 10) <= 0` 在 key 不存在时 `nil <= 0` **直接抛错**，整个 Lua 失败 → 请求 500 而不是"库存不足"
  → Lua 中任何 `nil` 参与比较前必须判空（`not stock or ...`）；新增 Lua 脚本后必须补空值用例。
- [2026-10-04] Redis Stream 异步下单：消费者线程异常 / 应用关闭时 `IllegalStateException` 死循环刷屏，且 stream 消息无自动补偿
  → 已弃用 Stream 方案改用 RocketMQ 顺序消息。**不要回归 Stream 方案**；异步链路必须有延迟消息 / 兜底扫描补偿。
- [2026-10-04] 缓存删除失败直接上抛 → 用户看到 500 但数据其实已改；且无补偿，脏缓存要等 TTL 才自愈
  → 更新缓存策略必须是"先更库后删缓存 + 删失败转 MQ 补偿 + TTL 兜底"三层；`delete` 返回 false（key 不存在）不算失败，不补偿。
- [2026-10-04] 秒杀并发超卖：DB 扣减无 `stock>0` 条件会扣到负数，Redis 预扣与 DB 库存对不上
  → 所有库存变更必须带 `.gt("stock", 0)` 条件更新；Redis 预扣只是"预扣"，DB 是最终账本。
- [2026-10-04] 一人一单判重不过滤订单状态：取消后订单行还在，用户永远不能再买同一张券、Redis 预扣被白白占掉
  → 判重必须只统计有效订单 `status IN (1,2,3)`；取消 / 退款后可重买，因此**不要建** `(user_id, voucher_id)` 唯一索引。
- [2026-10-04] 订单状态数字散落业务代码，支付 / 取消并发时无条件 UPDATE 互相覆盖
  → 状态迁移一律 CAS；常量进 `SystemConstants`；SPEC 状态机的非法迁移必须有测试覆盖。
- [2026-10-04] `IMAGE_UPLOAD_DIR` 硬编码 `C:\code\...\frontend\public\imgs`，换机器必挂
  → 路径类配置不写死仓库外；改上传路径必须同步 nginx alias（`/imgs/` → `public/imgs`）。
- [2026-10-04] `broker.conf` 硬编码 `brokerIP1=192.168.15.173`，换网络后 dashboard / 生产者连不上
  → 换网络必须 `ipconfig` 核对并改这一行；`messageDelayLevel` 与代码侧 `DELAY_LEVEL_ORDER_TIMEOUT` 是同一份事实的两处拷贝。

### 已知未修（改动涉及必须一并处理，详见 README §10）
- [2026-10-04] `FollowServiceImpl.follow` 取关分支 `remove(key, userId)` 删错成员（应为 `followUserId`）→ Redis 残留已取关关系
- [2026-10-04] `shop:geo:{typeId}` 全仓库无写入逻辑，带 x/y 的附近商铺查询恒返回空列表
- [2026-10-04] `BlogServiceImpl.queryBlogOfFollow` 的 `limit` 写死 `2`，每页最多 2 条
- [2026-10-04] `ShopTypeServiceImpl` 缓存命中分支无 return，每次都查库，且写入无 TTL
- [2026-10-04] `LockImpl.ID_PREFIX` 是 static，多把锁共享同一 value，unlock 可能误删别人的锁

---

## 六、验证纪律（对应指南第四 / 七节）

- 每个新接口必须附：单元测试 + 边界用例表（`docs/cases/*.csv`），边界至少覆盖 SPEC 边界清单
- 并发场景必须带**对账断言**（总额守恒 / 只扣一次 / 未超卖）——只跑压测不算并发测，压测报的是吞吐不是正确性
- 状态机**非法迁移**必须有测试（不只测主流程）
- 单次改动 ≤ 3 个文件，不攒大包；合并前过一遍《AI开发测试闭环落地指南.md》第八节自检清单
- ⚠ 本项目**暂未接 CI**（指南第七节 GitLab 片段是目标态），测试先本地跑，跑完把结果贴在对话里
