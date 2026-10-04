# 用户（User）规格

> 对应 `backend/src/main/java/com/hmdp/controller/UserController.java` / `service/impl/UserServiceImpl.java`

## 职责
一句话：短信验证码登录（含新用户自动注册）、token 会话存储与滑动续期、月度签到（Redis Bitmap 连续天数统计）。

## 明确不负责什么
- 不负责密码登录——`LoginFormDTO.password` / `tb_user.password` / `utils.PasswordEncoder` 字段齐全但**逻辑缺失**，前端 `Login2.vue` 仅演示
- 不负责登出——`POST /user/logout` 返回 `fail("功能未完成")`，未实现
- 不负责用户详情资料的维护（`UserInfo` 只读查询）
- 不负责图片上传（`UploadController`）

## 输入 / 输出（Result 无 code 字段）

### POST /user/code?phone=
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| phone | String | 是 | 大陆手机号（`RegexUtils.isPhoneInvalid` 校验） |

成功：`{ success: true }`（验证码 6 位数字，存 `login:code:{phone}`，TTL 2min，`log.debug` 打印）
失败：`手机号格式不正确！`

### POST /user/login（body: LoginFormDTO）
| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| phone | String | 是 | — |
| code | String | 是（当前只走验证码分支） | 与 Redis 中的验证码一致 |
| password | String | 否 | 当前忽略 |

成功：`{ success: true, data: token }`（token 随机 UUID；用户信息存 `login:token:{token}` Hash，TTL 360 **小时**）
失败：`手机号格式错误！` / `验证码错误！`

### GET /user/me
成功：`{ success: true, data: UserDTO }`（来自 `UserHolder.getUser()`）
⚠ `/user/me` 在登录白名单内：匿名访问 data 为 null（后端不判空），见边界清单

### GET /user/info/{id}
成功：`{ success: true, data: UserInfo }`，无详情 → `data: null`（返回前清空 createTime / updateTime）

### GET /user/{id}
成功：`{ success: true, data: UserDTO }`，用户不存在 → `data: null`

### POST /user/sign
前置：必须登录
成功：`{ success: true }`（`sign:{userId}:{yyyyMM}` Bitmap 第 day-1 位；重复签到幂等）

### GET /user/sign/count
前置：必须登录
成功：`{ success: true, data: 截至今天的连续签到天数 }`，无签到记录 → `0`

## 前置条件
- `/user/code` `/user/login` `/user/me` 免登录；`sign` / `sign/count` 需登录（`LoginInterceptor`）
- 会话由 `RefreshTokenInterceptor`（order 0）写入 ThreadLocal，每次请求**滑动续期** 360h
- 手机号校验依赖 `RegexUtils`，token 为随机 UUID（非 JWT，无签名）

## 边界清单
- 手机号为空 / 格式非法
- 验证码错误、过期（TTL 2min）、未发码直接登录 → `验证码错误！`
- 新手机号登录 → 自动注册（nickname `user_` + 10 位随机数）
- **并发注册同一新手机号** → 依赖 `tb_user.uniqe_key_phone` 唯一索引兜底（README §10.1），后到者抛异常走全局处理——已知未处理
- token 不存在 / 过期 → 匿名访问需登录接口（如 `/blog/of/me`）→ NPE 走全局异常返回"服务器异常"（已知，README §6.1，属约束 §五待修项）
- 重复签到 → `setBit` 幂等
- 跨月签到 → key 按月隔离互不影响；连续签到中断后重新计数（`num & 1` 遇 0 停止）

## 状态机
无。

## 验收标准
- [ ] 手机号格式校验、验证码错误 / 过期、新用户自动注册均有用例
- [ ] 滑动续期用例：任意请求后 token TTL 刷新为 360h
- [ ] 签到统计用例：跨月隔离、中断重计、空记录返回 0
- [ ] 越权防护：`sign` / `sign/count` 未登录时必须被拦截
- [ ] 并发注册同一手机号有对应用例或明确记录行为
