# 商铺（Shop）规格

> 对应 `backend/src/main/java/com/hmdp/controller/ShopController.java` / `service/impl/ShopServiceImpl.java` / `utils/CacheClient.java`

## 职责
一句话：商铺信息的缓存读写（穿透 / 击穿 / 雪崩防护）、更新时的缓存一致性（先更库后删缓存 + MQ 补偿）、GEO 附近商铺查询。

## 明确不负责什么
- 不负责商铺类型（`ShopTypeController` / `ShopTypeServiceImpl`）
- 不负责图片上传（`UploadController`）
- 不负责 GEO 索引的初始化与同步（`shop:geo:{typeId}` 当前无任何写入逻辑——已知缺口，见 CONSTRAINTS §五）

## 输入 / 输出（Result 无 code 字段）

### GET /shop/{id}
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| id | Long | 是 | — |

成功：`{ success: true, data: Shop }`；商铺不存在时缓存空值 → `data: null`
失败：无（不存在由 `data: null` 表达）

### POST /shop（新增）
body: Shop JSON
成功：`{ success: true, data: shopId }`

### PUT /shop（更新）
body: Shop JSON，`id` 必填
成功：`{ success: true }`
失败：`商铺id不能为空`

### GET /shop/of/type?typeId&current&x&y
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| typeId | Integer | 是 | — |
| current | Integer | 否（默认 1） | ≥ 1 |
| x, y | Double | 否 | 两个都传才走 GEO，否则 DB 分页 |

成功：`{ success: true, data: List<Shop> }`（GEO 路径回填 `distance`）
⚠ GEO 索引未初始化 → 带 x/y 恒返回空列表

### GET /shop/of/name?name&current
成功：`{ success: true, data: List<Shop> }`（name 为空 → 不带条件分页查全部）

## 前置条件
- `/shop/**` 全部免登录（`MvcConfig` 白名单）
- 更新链路依赖 RocketMQ 可用（补偿消息）

## 边界清单
- 查询：id 不存在（穿透）→ 空值缓存 2min；**判别顺序不能反**——先"非空即命中"，再"非 null 即空值标记"
- 击穿：当前生效方案 `queryWithPassThrough`；`queryWithMutex`（互斥锁）/ `queryWithLogic`（逻辑过期）是保留对照，未启用
- 更新：id 为空 → 拒绝；先更库后删缓存；`delete` 返回 false（key 不存在）**不算失败**，不补偿
- `delete` 抛异常 → MQ 补偿（消息只带 shopId，只删不回填）→ 消费端重投 5 次 → TTL 30min 兜底
- 补偿消息本身发送失败 → 落日志，靠 TTL 自愈
- GEO：x/y 为 null → DB 分页；有值 → `GEOSEARCH ... BYRADIUS 5000m WITHDIST`；分页越界（`list.size() < from`）→ 空列表
- 分页 `current ≤ 0` → MyBatis-Plus 行为未校验（见验收标准，属待补边界）
- 改 `type_id` / `x` / `y` 的更新**不会**同步 GEO 索引（已知缺口 README §10.6）

## 状态机
无（商铺无状态字段）。

## 验收标准
- [ ] 穿透用例：不存在的 id 连续打两次，第二次命中空值缓存（DB 查询次数为 1）
- [ ] 更新一致性用例：更新后缓存被删；模拟 `delete` 抛异常 → MQ 补偿后缓存被删
- [ ] 补偿不发消息的用例：`delete` 返回 false 时补偿消息条数为 0（不白打消息）
- [ ] GEO 分页边界：越界、空结果、距离排序（`ORDER BY FIELD(id,...)`）保持
- [ ] 并发击穿对账：同一冷 key 并发 N 个请求，DB 只回源 1 次
