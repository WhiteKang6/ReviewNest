# ReviewNest 项目说明（AI 每轮开工必读）

黑马点评（hm-dianping）学习项目，按《AI开发测试闭环落地指南.md》的闭环方法论维护：
AI 写完 → 自动化验证 → 失败 → 提炼成永久规则写进约束文档 → 同类错误在开工前就被拦住。

## 开工流程（每轮必须执行）

1. **先读 `CONSTRAINTS.md`**（根目录）——禁止重复造轮子 / 技术栈防幻觉 / 依赖方向 / 编码硬规则 / 历史教训
2. **再读要改动的模块 SPEC**：
   - [x] `docs/specs/VoucherOrder.SPEC.md` —— 秒杀下单 / 支付 / 超时取消
   - [x] `docs/specs/Shop.SPEC.md` —— 商铺缓存与一致性
   - [x] `docs/specs/User.SPEC.md` —— 登录 / token 会话 / 签到
   - [ ] 待补：Blog / Follow / Voucher / ShopType / Upload（开工前先补 SPEC）
3. 一次只做一件事，单次改动 ≤ 3 个文件，改完汇报：**改了哪些文件、新增了什么、删除了什么**
4. 修完 bug 后追问一句"这条错误说明 CONSTRAINTS.md 缺了哪条约束？"，把答案追加进第五节（闭环核心，只增不删）

## 技术栈速查

Spring Boot 3.2 / Java 17（本机 21）/ MyBatis-Plus 3.5.5 / Redis / Redisson / RocketMQ / Vue 3 + Vite + Element Plus

## 常用命令

- 后端启动：`cd backend && mvn spring-boot:run`（端口 8081）
- 前端 dev：`cd frontend && npm run dev`（5173）；生产：`npm run build` + `./nginx.exe`（8080）
- RocketMQ：`cd backend && docker compose -f docker/rocketmq/docker-compose.yml up -d`

## 关键入口速查

- 秒杀链路：`VoucherOrderServiceImpl.seckillVoucher` → `seckill.lua` → RocketMQ `seckill_order`（ORDERLY）→ `VoucherOrderConsumer` → CAS 下单
- 缓存链路：`ShopServiceImpl` / `utils.CacheClient`（穿透 / 互斥锁 / 逻辑过期四套方案演进都在注释里）
- 认证链路：`RefreshTokenInterceptor`(order 0) + `LoginInterceptor`(order 1) + `utils.UserHolder`（ThreadLocal）
- 已知技术债：`README.md §10`（改到相关代码必须一并处理，也列在 CONSTRAINTS.md 第五节）

## 项目特点

- 演进式代码：每个方案的前一代实现都注释保留在旁边作对照，**不要删除对照注释**
- 后端在前缀 `com.hmdp`，无独立模块；后端根目录为 `backend/`，前端根目录为 `frontend/`
- 所有业务失败用 `Result.fail(msg)`，错误码只有 `success` + `errorMsg`（无 code 字段，见 CONSTRAINTS §一）
