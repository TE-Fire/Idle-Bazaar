# 05 - Sa-Token：登录与鉴权，以及背后发生的事

对应代码：`campus-core/com.campus.core.satoken`（MultiAccountConfig / StpInterfaceImpl / StpAdminUtil / StpUserUtil）、
`campus-core/service/AdminAuthService`、`campus-admin-api/config/AdminWebConfig`、
`campus-admin-api/controller/{AuthController, AdminUserController, PermDemoController}`、
`campus-common/exception/GlobalExceptionHandler`、`campus-common/constant/PermCodes`。
版本：sa-token **1.38.0**（`sa-token-spring-boot3-starter` + `sa-token-redis-jackson`，token 落 Redis）。
写这篇的起因：管理端"认证已完成、鉴权只铺了一半"，需要能把"调哪个 API"和"框架在背后做了什么"
对上的一份笔记，同时标出当前进度里的缺口。

## 零、当前项目进度（读之前先对齐坐标）

| 模块 | 状态 |
|---|---|
| 管理端登录（用户名密码 / 邮箱验证码两种，已合并为一个端点） | 已完成 |
| 失败 5 次锁定 10 分钟（DB `fail_count` + Redis 快路径） | 已完成 |
| 图形验证码、邮箱验证码（注册 / 找回 / 登录三场景隔离） | 已完成 |
| 登录态拦截（`/admin/**` 必须登录，`/admin/auth/**` 放行） | 已完成 |
| 权限点校验（`@SaCheckPermission` + `StpInterface` 查库） | **只覆盖 5 个接口** |
| 角色校验（`getRoleList` / `@SaCheckRole`） | 数据源已实现，**无消费方** |
| 学生端登录（`StpUserUtil` / `/api/**` 拦截） | 未接入 |
| 邮箱验证码错误次数上限 | 未做（安全缺口） |

一句话概括：Sa-Token 的**登录**链路在本项目是完整跑通的，**鉴权**链路骨架通了但只铺了几个点。

## 一、定位：它管什么，不管什么

管两件事：**登录态的颁发与校验**；**权限 / 角色的声明式校验**。

不管：密码比对、验证码、账号锁定、注册流程。这些都仍是业务代码——本项目的
`AdminAuthService.login()` 业务部分有六十多行（验证码 → 锁定态 → 查库 → 状态 → BCrypt → 失败计数），
**只在最后一行把 loginId 交给 Sa-Token**：

```java
StpAdminUtil.login(admin.getId());
```

这条分界线要记住：Sa-Token 不知道密码是什么、这个人是不是被锁了、验证码对不对。
它的职责从"确认这个人是谁之后"开始。

## 二、数据来源：认证看 Redis，鉴权看 MySQL

### 2.1 两个数据源，一条接缝

| | 认证（登录态） | 鉴权（权限 / 角色） |
|---|---|---|
| 数据 | token → loginId；loginId → 在线 token 列表 | loginId → role_id → perm_code 列表 / role_code |
| 存放 | **Redis**（`sa-token-redis-jackson` 提供的 SaTokenDao） | **MySQL**（`admin_user` / `admin_role_perm` / `admin_role`） |
| 谁读 | 框架自己：StpLogic → SaTokenDao | **你写的** `StpInterfaceImpl`，框架只负责回调 |
| 缓存 | token key 带 TTL，随请求滚动续期 | **无缓存**，每次鉴权现查库 |
| 落脚点 | `satoken-admin:admin:token:{token}`、`…:session:{loginId}` | `admin_user.role_id`、`admin_role_perm.perm_code` |

**接缝只有一个：loginId。** 登录时 `StpAdminUtil.login(admin.getId())` 把 `admin_user.id` 存进 Redis；
鉴权时框架拿这个 id 回调你的 `StpInterface` 去 MySQL 查权限。前半段框架管，后半段你管——
这也是为什么改角色不需要 `kickout`：权限每次现查，下次请求就是新的。

### 2.2 "权限数据在 Redis" 这个印象从哪来

这是常见误解。很多教程里的 `StpInterface` 实现**自己去查 Redis 缓存**——那是用户代码的选择，不是框架行为。
框架只做一件事：需要权限列表时向 `StpInterface` 要。列表从哪来、缓不缓存，框架既不关心也不介入。

字节码证据（sa-token-core 1.38.0，`javap -c StpLogic`）：

```
getPermissionList(Object loginId)
    → SaManager.getStpInterface().getPermissionList(loginId, loginType)   // 直接委托：无缓存、不碰 Redis
getLoginIdNotHandle(String token)
    → getSaTokenDao().get(splicingKeyTokenValue(token))                   // 这才是读 Redis 的那一步
```

### 2.3 一次鉴权的完整调用链（标注数据出处）

```
checkPermission("settings.edit")
  ├─ getLoginId()                    请求取 token → Redis GET …:token:{token} → loginId
  ├─ hasPermission(loginId, perm)
  │    ├─ getPermissionList(loginId) → 你的 StpInterfaceImpl → MySQL 两次查询
  │    └─ hasElement(list, perm)     list.contains(perm) || SaFoxUtil.vagueMatch(...)（支持 * 通配）
  └─ 不匹配 → throw NotPermissionException(11051) → 本项目转 1002
```

两句话概括：**Redis 回答"你是谁"，MySQL 回答"你能干什么"**。角色校验 `getRoleList` 同理，
也是经 `StpInterfaceImpl` 查 `admin_role.role_code`。

### 2.4 关于通配

`hasElement` 的默认实现是 `list.contains(perm)` 或 `SaFoxUtil.vagueMatch(pattern, perm)`，
所以权限码**支持 `*` 模糊匹配**（`user.*` 能放行 `user.ban`）。本项目没用到：
`PermCodes` 全是精确码，`admin_role_perm` 里存的也是精确码。

## 三、四个对象

| 对象 | 是什么 |
|---|---|
| **StpLogic** | 一套登录体系的发动机：持有 loginType、配置，负责发 token / 验 token / 踢人。默认全局一个（`StpUtil` 背后就是它）；多账号时自建多个 |
| **token** | 登录凭证，一串无意义字符串（本项目 uuid）。**不是 JWT**——本身不携带信息，只是 Redis 里一把钥匙的名字 |
| **TokenSign** | 一条"在线记录"：token 值 + device + tag，挂在账号会话里 |
| **账号会话 Session** | loginId 在 Redis 的档案，含该账号全部 TokenSign，是"多端共存 / 按设备踢人"的数据基础 |

Redis 实物（2026-10-01 本机 `cm-redis`）：

```
satoken-admin:admin:token:1a80379c-…    →  "2104"                     TTL 1969s
satoken-admin:admin:session:2101        →  {"loginType":"admin","loginId":2101,
                                             "tokenSignList":[{"value":"71d2f427-…",
                                                               "device":"default-device"}]}
```

key 形状 = `{tokenName}:{loginType}:token|session:{…}`。
注意 `token` 那条是**正向索引**（token → 谁），`session` 那条是**反向索引**（谁 → 有哪些 token），
踢人、多端管理全靠后者。

## 四、登录：`login(id)` 背后

### 4.1 本项目的完整登录时序

以用户名密码登录为例（`POST /admin/auth/login`，`type=1`）：

| 步 | 谁做的 | 内容 |
|---|---|---|
| 1 | 业务 | `captchaService.verify(...)` 校验图形验证码 |
| 2 | 业务 | `LoginTypeEnum.fromCodeOrNull(type)`，非法值 → 1000 |
| 3 | 业务 | 用户名登录时先查 Redis 锁 `cm:admin:lock:{username}`（快路径） |
| 4 | 业务 | 按 type 查库：`selectByUsername` 或 `selectByEmail` |
| 5 | 业务 | 状态枚举判断：停用 → 4005；待分配角色 → 4006 |
| 6 | 业务 | DB `lock_until` 兜底（Redis 被清空也拦得住） |
| 7 | 业务 | 凭据校验：BCrypt 比对，或调 `verifyMailCode` 校验邮箱码 |
| 8 | 业务 | 失败则 `fail_count++`，满 5 次写 `lock_until` + Redis 锁标记 |
| 9 | 业务 | 成功：清零 `fail_count`、写 `last_login_at` |
| 10 | **Sa-Token** | `StpAdminUtil.login(admin.getId())` |
| 11 | 业务 | 查角色与权限码，组装 `AdminLoginVO`（含 `tokenName` / `tokenValue` / `permCodes`） |

第 10 步之前全是业务；第 10 步之后全是框架。

### 4.2 `login(id)` 内部做了什么

1. 按 `tokenStyle` 生成 token（本项目 uuid）。
2. 写正向索引：`{tokenName}:{loginType}:token:{token} = loginId`，TTL = `timeout`（管理端 7200s）。
3. 写反向索引：取出 `…:session:{loginId}`，往 `tokenSignList` 追加一条 TokenSign（device=default-device）。
4. 按 `isReadCookie`／`isReadHeader` 决定如何把 token 送出去（本项目 Cookie 默认开，但前端实际用 Header）。
5. 触发登录监听事件（可用于审计日志）。

可选形态：`login(id, device)` 指定设备；`login(id, SaLoginParameter)` 临时指定 timeout、是否写 Cookie 等。

### 4.3 为什么响应里要回 `tokenName` + `tokenValue`

Sa-Token 默认把 token 写进 Cookie，但本项目前端是**手动带 Header**：

```ts
// packages/api/src/http.ts
config.headers[tokenName] = tokenValue        // satoken-admin: <uuid>
```

原因是管理端要显式控制"带不带"，也方便把 token 存进 sessionStorage 做页面刷新恢复。
所以后端在 `AdminLoginVO` 里把 `tokenName` 也下发（`MultiAccountConfig.ADMIN_TOKEN_NAME`），
前端不至于把 tokenName 写死。

## 五、登录态校验：`checkLogin()` 背后

本项目在 `AdminWebConfig` 注册了全局拦截：

```java
registry.addInterceptor(new SaInterceptor(handle -> StpAdminUtil.checkLogin()))
        .addPathPatterns("/admin/**")
        .excludePathPatterns("/admin/auth/**");
```

一次 `checkLogin()` 内部：

1. **取 token**：先按 `tokenName` 找 Cookie，再找同名 Header（本项目走 Header）。都拿不到 →
   `NotLoginException`。
2. **查正向索引**：Redis `…:token:{token}` 不存在 → `NotLoginException`。
3. **判有效期**：`timeout` 与 `activeTimeout`（本项目 -1，关闭）。
4. **续期**：`autoRenew` 默认开，每次有效请求把 TTL 续满 timeout。所以"2 小时"指**不操作 2 小时**，
   不是"登录后 2 小时"——这也是上面那条 token TTL 显示 1969s 而不是 7200s 的原因（自上次请求起算）。
5. 通过后可取 `getLoginIdAsLong()` / `getTokenValue()`。

异常 → `GlobalExceptionHandler` → `NotLoginException` 转 **1001**。

## 六、登出、踢人、多端

| 动作 | API | 背后 | 本项目 |
|---|---|---|---|
| 当前设备登出 | `logout()` | 删该 token 的正向索引，从 session 的 tokenSignList 移除 | `AuthController.logout()` |
| 把某账号踢下线 | `stpLogic.kickout(id)` | 该账号所有 token 标记为 KICK_OUT 状态，下次校验抛 `NotLoginException` | `AdminAccountService.resetPassword()` 改密后把旧会话全部下线。<br>注：**`assignRole()` 没有踢下线**——权限每次查库，改角色下一次请求就生效，不需要踢 |

配置联动：

- `isConcurrent=false`：新登录踢掉同账号旧 token（单端登录）。
- `isShare=true`：多次登录共用同一 token；`false`（本项目）每次登录新发 token。

本项目 `isConcurrent=true` + `isShare=false`，效果是"允许多端在线、每次登录各发一张票"，
所以 Redis 里能看到同一 loginId 对应多个 token key——这也是将来做"按设备踢下线"的前提。

## 七、鉴权：注解与 StpInterface 回调

### 7.1 三段执行链，缺一不可

```java
@SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
@GetMapping("/pending")
public Result<List<AdminUserVO>> pending() { … }
```

1. **`SaInterceptor` 必须在容器里**（`AdminWebConfig`）。注解本身不会自动生效——它们是
   "给拦截器调用的注解处理器"。本项目踩过：没注册时未登录直接 GET `/admin/admins/pending` 也返回 200。
2. 拦截器对 handler 方法扫注解 → 执行 `@SaCheckPermission` → 调对应 StpLogic 的 `checkPermission(value)`。
3. StpLogic 需要"这个人的权限列表" → **回调**容器内唯一的 `StpInterface` 实现：
   `StpInterfaceImpl.getPermissionList(loginId, loginType)` → 查 `admin_role_perm` →
   列表含该 code 放行；否则抛 `NotPermissionException` → **1002**。

关键认知：`StpInterface` 是**回调式扩展点**——你实现它，框架调用它，业务代码基本不碰它。
本项目唯一主动调用它的地方是 `/admin/auth/info`（给前端刷新菜单权限）：

```java
List<String> perms = StpAdminUtil.stpLogic.getPermissionList();
```

### 7.2 一次真实请求的完整链路

以审核员（无 `settings.edit`）访问 `GET /admin/admins/pending` 为例：

| 步 | 位置 | 发生什么 |
|---|---|---|
| 1 | 前端 | axios 带上 `satoken-admin: <token>` 请求头 |
| 2 | SaInterceptor | `checkLogin()`：查 Redis 正向索引，通过则续期 |
| 3 | SaInterceptor | 扫方法注解，发现 `@SaCheckPermission(settings.edit)` |
| 4 | StpLogic | `checkPermission("settings.edit")` |
| 5 | StpInterfaceImpl | `getPermissionList(loginId, "admin")` → 查 `admin_user`（取 role_id）→ 查 `admin_role_perm` |
| 6 | StpLogic | 比对列表，不含 `settings.edit` → 抛 `NotPermissionException` |
| 7 | GlobalExceptionHandler | 转 `1002 无权限` |
| 8 | 前端 | 请求层统一 toast（含 traceId） |

错误码映射（`GlobalExceptionHandler`）：

| 异常 | HTTP 码 | 业务 code |
|---|---|---|
| `NotLoginException` | 200（body 里带 code） | 1001 未登录 |
| `NotPermissionException` / `NotRoleException` | 200 | 1002 无权限 |

### 7.3 注解与命令式 API

| 注解 | 语义 |
|---|---|
| `@SaCheckLogin` | 已登录（拦截器已全局校验，个别方法可再加） |
| `@SaCheckPermission("user.ban")` | 持有权限码 |
| `@SaCheckRole("super")` | 持有角色 |
| `@SaCheckPermission({"a","b"})` | 多个默认 AND；`mode = SaMode.OR` 改或 |
| `@SaIgnore` | 类上统一鉴权时单独放行 |

命令式等价（本项目 `StpAdminUtil` 已包装）：
`checkPermission(code)` 抛异常；`hasPermission(code)` 返回布尔，用于返回给前端做按钮显隐
（`PermDemoController./mine`）。

### 7.4 `getPermissionList` 的性能取舍

当前每次鉴权查 2 次库（`admin_user` + `admin_role_perm`）。注释里已说明可接受：管理员数量是个位数，
且 `admin_role_perm` 全表只有 36 行（16+6+10+4）。

好处是**角色变更立即生效、零失效代码**：`assignRole()` 里连 `kickout` 都没调，改完下一次请求读到就是新权限。

若将来要加缓存，运行时写入点有两处，都得考虑失效：`assignRole()`（改角色）与
`register()`（新账号 role_id=0，此时是无权限状态）。详见 §十四。

## 八、前端如何配合：三层拦截，但都不是防线

| 层 | 位置 | 作用 |
|---|---|---|
| 请求头 | `packages/api/src/http.ts` | 每个请求带 `satoken-admin: <token>`；登录成功后 `setToken()` 写入 |
| 路由守卫 | `apps/admin/src/router/index.ts` | `to.meta.perm` 不满足 → 跳转 `/403`（菜单级） |
| 按钮指令 | `apps/admin/src/directives/perm.ts` | `v-perm="PERM_CODES.USER_BAN"` 不满足 → 不渲染（按钮级） |
| 菜单过滤 | `BasicLayout.vue` | 无权限的菜单项直接不进侧边栏 |
| 权限刷新 | `stores/user.ts` | 刷新页面时调 `/admin/auth/info` 重建 `permCodes` |

这四层全是**体验优化**。唯一能拦住越权请求的是**后端 `@SaCheckPermission`**——
改前端 localStorage、直接 curl 都能绕过。`PermMatrixView.vue` 的注释里也强调过这点。

## 九、本项目的权限模型与覆盖现状

四个角色 × 16 个权限点（`db/schema.sql` 种子数据，`business/03` F-A1 矩阵）：

| 角色 | role_code | 权限点 |
|---|---|---|
| 超级管理员 | SUPER (2001) | 全部 16 个 |
| 审核员 | REVIEWER (2002) | dashboard/report/item.view/review.* —— **无 user.ban、无 settings.edit** |
| 运营 | OPERATOR (2003) | item.*/user.verify/order.*/content.edit/log.view —— **无 review.judge** |
| 只读 | READONLY (2004) | dashboard.view/report.view/item.view/user.view |

覆盖现状与风险：

- `@SaCheckPermission` 目前只在 `AdminUserController` 3 个方法、`PermDemoController` 2 个方法上。
  商品 / 订单 / 审核等业务接口还没写，写了就必须挂。
- `getRoleList` 无消费方：没有 `@SaCheckRole`，业务也不读角色列表。要么用起来要么删——留着是假象。
- **真实风险**：拦截器只补登录态，权限全靠方法注解。新增接口漏挂注解 = "登录即可访问"，
  静默放权，编译不报错、测试不失败。建议新增业务 controller 时把"挂注解"列为评审项；
  更硬的做法是启动时扫一遍 `/admin/**` 的 handler，没挂注解的打 WARN。

## 十、多账号体系：为什么本项目有两个 StpLogic

学生端与管理端登录态必须互不相干：各自 tokenName（`satoken-user` / `satoken-admin`）、
各自超时（7 天 / 2 小时）、各自 Cookie 与 Redis 前缀。`MultiAccountConfig` 的做法：

1. `@PostConstruct` 手工 `new StpLogic("admin")`，`setConfig` 后注册**两处**：
   - `StpAdminUtil.setStpLogic(logic)` —— 静态工具自持，业务侧调用走它；
   - `SaManager.putStpLogic(logic)` —— 注册进框架，**注解处理**从这里找。
   两处指向同一对象。只 set 不 put，注解会拿到默认逻辑，表现为"明明登录了却 1001"。
2. 不能做成 `@Bean`：框架的 `SaBeanInject` 按类型找唯一 StpLogic，容器里两个直接启动失败
   （报错里提示的 `-parameters` 是干扰项）。
3. 类名不能叫 `SaTokenConfig`：与框架类撞名，注解处理轮与 Lombok 一起崩（踩过）。
4. 多账号时注解必须写 `type`，否则按默认 loginType 找逻辑。

## 十一、自己动手验证（命令可复制）

先起应用（本机端口偏移，Agent 的 Bash 里必须带 `--server.port`）：

```bash
java -jar campus-admin-api/target/campus-admin-api-1.0.0-SNAPSHOT.jar \
     --spring.profiles.active=dev --server.port=8080
```

登录（图形验证码开启时，先从 Redis 取码：`docker exec cm-redis redis-cli -a Campus@2026
--no-auth-warning GET cm:captcha:<captchaKey>`）：

```bash
curl -s -X POST http://localhost:8080/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"type":1,"usernameOrEmail":"super","passwordOrCode":"Campus@2026",
       "captchaKey":"<key>","captchaCode":"<code>"}'
# 响应里取 tokenName / tokenValue
```

然后：

```bash
# 1) 带 token 访问受保护接口
curl -s http://localhost:8080/admin/admins/pending -H "satoken-admin: <token>"

# 2) 不带 token → 1001
curl -s http://localhost:8080/admin/auth/info                       # → 1001

# 3) 看 Redis 里长什么样
docker exec cm-redis redis-cli -a Campus@2026 --no-auth-warning KEYS "satoken-admin*"
docker exec cm-redis redis-cli -a Campus@2026 --no-auth-warning GET "satoken-admin:admin:token:<token>"

# 4) 越权验证：换成 readonly 登录，再调同一个接口 → 1002
curl -s http://localhost:8080/admin/admins/pending -H "satoken-admin: <readonly-token>"

# 5) 踢下线：改该管理员角色后，原 token 再请求 → 1001（KICK_OUT）
```

## 十二、API 与配置速查

| 目的 | API（经 StpAdminUtil / StpUserUtil） |
|---|---|
| 登录 | `login(id)` / `login(id, device)` / `login(id, SaLoginParameter)` |
| 当前 loginId | `getLoginIdAsLong()` |
| 当前 token | `getTokenValue()` |
| 登录态 | `isLogin()`（布尔）/ `checkLogin()`（抛异常） |
| 登出 | `logout()` |
| 踢下线 | `stpLogic.kickout(id)` |
| 权限校验 | `checkPermission(code)` / `hasPermission(code)` |
| 权限列表 | `stpLogic.getPermissionList()`（回调你的 StpInterface） |
| 角色列表 | `stpLogic.getRoleList()`（本项目暂无消费方） |
| 账号会话 | `stpLogic.getSessionByLoginId(id)`，`.set(k,v)` / `.get(k)` 挂业务数据 |

| 配置 | 本项目 | 含义 |
|---|---|---|
| `tokenName` | satoken-admin / satoken-user | Cookie 名、Header 名、Redis key 前缀 |
| `timeout` | 7200 / 604800 | token 有效期（秒）；autoRenew 默认开，滚动续期 |
| `activeTimeout` | -1 | 无操作强制下线（秒）；-1 关闭 |
| `isConcurrent` | true | 同账号多端共存 |
| `isShare` | false | 每次登录新发 token（true = 多端共用） |
| `tokenStyle` | uuid | token 生成风格 |
| `isReadCookie` / `isReadHeader` | 默认开 | token 读取位置 |

## 十三、坑清单（本项目踩过的）

1. 没注册 `SaInterceptor` → 注解完全无效，未登录也能访问。
2. 只 `setStpLogic` 不 `putStpLogic` → 注解拿到默认逻辑，明明登录了却 1001。
3. StpLogic 做成 `@Bean` → 两个实例导致启动失败。
4. 配置类命名撞 `SaTokenConfig` → 注解处理轮崩、Lombok 失效。
5. 多账号注解漏写 `type` → 找错 StpLogic。
6. 业务侧把"密码校验 / 锁定 / 验证码"指望给 Sa-Token → 它不管这些，必须自己写。

## 十四、要不要把权限预加载到 Redis（含 SETNX 防重）

结论：**现在不做**。要做的话用**懒加载回填**，不用启动预加载，也不用 SETNX。

### 14.1 SETNX 在这里防的是个不存在的问题

- 权限数据是**幂等快照**：加载一次和加载十次结果相同。重复加载的代价只是多一次查询，
  没有副作用需要防。
- 裸 `SETNX` 没有 TTL：加载过程崩了或应用被 kill，锁 key 永久留下，**下次启动永远加载不了**
  （死锁）。要安全就得 `SET key val NX EX 30`，多了过期与续期的复杂度。
- SETNX 真正该用的场景是**非幂等或昂贵**的动作（累加计数、发通知、重建大索引），
  或者防止多实例同时触发一次耗时几分钟的重建造成雪崩。这里都不是。

### 14.2 启动预加载的三个代价

| 代价 | 说明 |
|---|---|
| **失效逻辑** | 一旦缓存，角色变更就必须主动失效。运行时写入点有两处：`assignRole()`（改角色）与 `register()`（新账号 role_id=0）。现在"每次查库"让这些变更**立即生效且零代码** |
| **Redis 变成鉴权硬依赖** | Redis 挂了 / 被清空，就得回落到 DB 查询——**DB 路径仍然要留**，缓存只是多了一条路径，没删掉任何一条 |
| **启动顺序与多实例** | 多副本部署时谁加载、加载失败是否阻塞启动，都要额外约定 |

### 14.3 三种做法对比

| 方案 | 失效 | 自愈（Redis 被清空） | 复杂度 |
|---|---|---|---|
| 现状：每次查库 | 不需要 | 天然 | 最低 |
| 启动预加载 + SETNX | 需主动失效 | 需重启或补加载 | 高 |
| **懒加载回填**（miss 时查库并写回） | 写入点删 key | 自动回补 | 中 |
| 存进 SaSession | 靠 `kickout` 失效 | 重新登录即可 | 中，但改角色要补 kickout |

推荐顺序：**现状 > 懒加载 > 存 Session > 启动预加载**。
真到需要缓存那天，用懒加载即可——它没有 loader、没有锁、没有启动顺序问题，
且只缓存真正被用到的角色。

## 速查

- 登录：`login(id)` → 写 Redis **正向**（token→id）+ **反向**（session→tokenSignList）+ 下发凭证。
- 校验：`checkLogin()` → Cookie/Header 取 token → 查正向索引 → autoRenew 滚动续期。
- 鉴权：`@SaCheckPermission` → 必须有 SaInterceptor → 回调 `StpInterface` 查库比对 → 未命中 1002。
- 登出：`logout()` 删当前票；踢人：`kickout(id)` 标记该账号全部票为 KICK_OUT。
- 分界线：密码、验证码、锁定、注册归业务；`login(id)` 之后的一切归框架。
