# 11 - 操作日志：@OpLog 与异步落库

> 上游依据：`tech/01` §5.4（日志）· `tech/03` §4.4 / CR 清单 11b
> 对应代码：写侧 `backend/campus-core/src/main/java/com/campus/core/oplog/`（`OpLog` / `OpLogAspect` /
> `OpLogRecord` / `OpLogWriter` / `OpLogMasker`）· `core/config/OpLogExecutorConfig` ·
> `core/entity/SysOperationLog` · `core/mapper/SysOperationLogMapper`；
> 读侧 `core/service/OperationLogService` · `core/dto/req/OperationLogReq` · `core/dto/resp/OperationLogVO` ·
> `campus-admin-api/.../controller/OperationLogController`
> 承载页面：管理端 A9 操作日志 `/settings/logs`（权限点 `log.view`）——
> **2026-10-06 已接真接口**（`GET /admin/log/list`），`module` / `opType` 的中文标签由前端维护
> 起因为什么现在做：`tech/03` 的 CR 清单第 11b 条要求「管理端带 `@SaCheckPermission` 的写接口必须同时挂 `@OpLog`」，
> 而此前该注解只有规格、没有实现，Controller 类注释里留着一句「待该注解落地后统一补」。

---

## 一、先分清两类日志

项目里「日志」是两个东西，混在一起谈必然吵架（`tech/01` §5.4 已定调）：

| | 访问日志 | 操作日志 |
|---|---|---|
| 实现 | `AccessLogFilter`（common，`OncePerRequestFilter`） | `@OpLog` + AOP |
| 覆盖 | **所有**请求，无差别 | 只有**有业务语义**的写动作 |
| 输出 | 日志文件（logback 按天滚动） | `sys_operation_log` 表 |
| 回答的问题 | 「这个接口几毫秒、返回几百」 | 「谁、什么时候、把什么、改成了什么、成没成」 |
| 生命周期 | 保留 30 天 | **只追加，不可删改** |

**不要用操作日志记读操作**：全量 HTTP 日志是噪音，会把这表撑成访问日志的劣化复制品。
也不要指望访问日志回答「谁改了配置」——它不认识登录人（Filter 早于 Sa-Token 解析）。

---

## 二、类结构：为什么切成四个类

```
OpLog（注解）
  └─ OpLogAspect  ── 请求线程 ──▶ OpLogRecord（不可变快照）
                                      │
                        OpLogWriter ◀──┘  （事务边界 + 提交线程池）
                                      │
                            op-log-1 线程 ──▶ OpLogMasker（脱敏已在切面完成）
                                          └─▶ SysOperationLogMapper.insert
```

| 类 | 只做一件事 | 放在这里的理由 |
|---|---|---|
| `OpLog` | 声明埋点信息 | 注解在 admin-api 的 Controller 上用，静态无状态 |
| `OpLogAspect` | **在请求线程上抓快照**：登录人、上下文、bizId、脱敏入参、耗时、成败 | 最容易出错的「抓取时机」集中在一个类里 |
| `OpLogRecord` | 承载快照（record，不可变） | 交给线程池的**不能含任何上下文引用**，用不可变类型把这约束写进类型里 |
| `OpLogWriter` | 事务边界 + 异步落库 + 查补操作人 | 第二个易错点（异步边界与降级）单独一个类，改它不会碰到切面 |
| `OpLogExecutorConfig` | 线程池与拒绝策略 | 配置与策略放在 `config`，与其它框架配置一致 |
| `OpLogMasker` | 脱敏 | 被切面调用，也可以被别的地方复用 |

**为什么不把落库写在切面里**：切面要 `@Around` 包住业务方法，任何一点耗时都在请求关键路径上。
拆开后，切面只做内存操作，落库与两次查补全在线程池线程上——**这条边界就是整个设计的价值所在**。

---

## 三、四个必须解决的技术问题

### 3.1 上下文抓取时机：异步线程什么都取不到

`AdminContext.getAdminId()` 依赖 Sa-Token 的 `request`，`RequestContextHolder` 是 `ThreadLocal`
（`AccessLogFilter` 在 `finally` 里 `remove()`）。两者在 `op-log-1` 线程里**都是 null**。
两个类的注释本来就写着「异步场景不用本类」，所以：

> **规则：所有要落库的值必须在 `pjp.proceed()` 所在的线程上取完，线程池只接收 `OpLogRecord`。**

实测证据（`op-log-1` 线程上的 SQL 日志）：

```
2026-10-06 21:31:41.574 [op-log-1] ... SysOperationLogMapper.insert - ==> Parameters:
  2107463809259638785(Long), 2101(Long), 超级管理员(String), SUPER(String), item(String),
  OFF_SHELF(String), 999999999999999999(Long), 强制下架(String),
  {"id":"999999999999999999","req":{"reason":"校外引流 138****8000",...}}(String), 0(Integer),
  1101(String), 127.0.0.1(String), Python-urllib/3.13(String), 8001c0c7c1924d72(String), 159(Integer), ...
```

`admin_name` / `role_code` 那两列是**在线程池线程上按 `adminId` 查补**的（`AdminUserMapper` →
`AdminRoleMapper` 各一次主键查询）——它们不依赖请求上下文，只需要一个 id，所以可以搬走。
其余（ip / ua / traceId）都在请求线程上抓好。

**为什么 `admin_name` / `role_code` 要冗余**：表注释写了「避免删号后查不到」。管理员被删号或改角色后，
历史日志要能还原「当时是谁、以什么角色做的」，所以是写入时刻的**快照**，不是延迟关联。

### 3.2 事务边界：回滚了就不该留痕

`OpLogWriter#record` 先看当前线程有没有活动的事务同步，**且这条记录是不是成功记录**：

```java
if (record.success() && TransactionSynchronizationManager.isSynchronizationActive()) {
    registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { submit(record); }   // 回滚 -> 这条成功记录不留
    });
    return;
}
submit(record);   // 无事务上下文，或本记录是失败尝试 -> 立即提交
```

成功记录挂 `afterCommit`，是为了让「同一事务里前面的步骤成功了、后面失败导致整体回滚」时，
那些**中途成功**的步骤不留痕——回滚了就不该留。
**失败记录走的是立即提交，不挂 `afterCommit`**：失败常常正是回滚的起因，若也挂上去，
回滚会把「这次失败尝试」一并抹掉，正好违反第 3 条「失败也要记」。
（这个取舍是压测事务挂法时暴露出来的：最初版本不看 `success` 就挂，实测 B 用例的失败行被回滚吃掉了，
见 §七。修正后 B 用例的失败行正常落库。）

有两种挂法：

| 挂法 | 进入切面时的事务状态 | 成功记录 | 失败记录 |
|---|---|---|---|
| Controller（**本项目用法**） | Service 的 `@Transactional` **已提交完毕** | 直接提交 | 直接提交 |
| Service（`@Transactional` 方法上） | 事务**进行中** | 注册 `afterCommit`（回滚则丢弃） | **立即提交** |

所以「必须事务提交后再写」在这里是**结构性满足**的，不是靠调用方小心。
`tech/01` 给的另一条路是发 `OP_LOG` 消息、MQ 消费落库——**没有采用**：RocketMQ 目前无任何业务代码引用、
本机也没起 broker，为一个横切能力引入运行时依赖不划算。真要换，只改 `OpLogWriter#record` 一处。

### 3.3 异步线程池：为什么不能省，以及一个连带效果

规格（`tech/01` §5.4 第 2 条）：自定义池、队列有界、拒绝要能降级。

| 参数 | 值 | 理由 |
|---|---|---|
| core / max | 1 / 2 | 写日志是极短的单条 INSERT；小并发让插入顺序贴近发生顺序，也不把库打满 |
| 队列 | **有界** 512 | 无界队列在库卡住时会一直堆到 OOM；有界是「拒绝策略能生效」的前提 |
| 拒绝策略 | 自定义：**丢弃 + ERROR** | 见下 |
| 线程名 | `op-log-` | console pattern 里有 `%thread`，排查时一眼认出 |
| 关停 | 等最多 10 秒写完队列 | 否则每次重启都丢掉最后一批操作记录 |

**为什么不用 `CallerRunsPolicy`**：它把任务退回请求线程**同步执行**，等于
「数据库慢 → 队列满 → 用户请求多等一次数据库往返」——与「日志不能影响主流程」正好相反。
也不抛 `AbortPolicy`：日志失败没有任何理由让业务接口报错。丢掉的日志不可恢复，
所以 ERROR 里带上排队数量，用于区分瞬时尖峰（偶发一条）与库真卡住（持续刷屏）。

**连带效果（值得记住）**：这是本项目**第一个 `Executor` Bean**。Spring Boot 的
`TaskExecutionAutoConfiguration` 只在「容器里没有 `Executor` 类型 Bean」时才创建
`applicationTaskExecutor`，现在它退让了。当前影响为零（项目无 `@Async`、无异步 MVC 接口），
但**将来要用 `@Async` 时必须显式写执行器名**，不能再依赖那个不存在的默认池。

### 3.4 失败也记：用 `@Around` 而不是两个通知

```java
try {
    Object result = pjp.proceed();
    emit(..., success = true, errorCode = null);
    return result;
} catch (Throwable t) {
    emit(..., success = false, errorCodeOf(t));   // BizException 取 code，其它取异常类名
    throw t;                                      // ★ 原样抛回，绝不吞
}
```

不用「`@AfterReturning` + `@AfterThrowing`」两个通知，是为了让成功与失败**共用同一段快照代码**——
否则以后只改了一边，会出现「成功记了 after_value、失败没记」这种不对称，排查时更费解。

`errorCode` 口径：`BizException` → `getCode()`（如 `1101`）；其它异常 → 异常类名（截断到列宽 32）。
两列都不是给人读的，是给「同一错误码反复出现」这条线索用的。

---

## 四、脱敏：两道口径

`OpLogMasker` 的做法是先把入参序列化成 Jackson 的 `JsonNode` 树，**逐层按「键名」判**，
再对**字符串值**按「值形」兜一层。

| 口径 | 处理 | 覆盖什么 |
|---|---|---|
| 按键名 | 命中即整值替 `***` | `password` / `passwd` / `pwd` / `secret` / `token` / `captcha` / `mailCode` / `smsCode` / `phone` / `mobile` / `telephone` / `email` / `mail` / `idCard` / `idNo` / `studentNo` / `studentId` / `bankCard`（**小写子串**匹配，所以 `newPassword`、`captchaCode`、`bankCardNo` 都命中） |
| 按值形 | 按位替换 | 手机号 → `138****8000`；邮箱 → `a***@qq.com` |

**为什么要第二道**：键名猜不出内容。实测用例里的「下架原因」是个自由文本字段（`reason`），
键名毫无敏感特征，但里面就夹着手机号：

```json
{"id":"999999999999999999","req":{"reason":"校外引流 138****8000","note":"联系方式 a***@qq.com 与 学号信息"}}
```

**为什么走 JsonNode 树而不是正则改 JSON 文本**：树能分清「键」和「值」。
正则改文本会把键名一起改掉（`"phone"` → `"***"`）——表里留着一条谁也看不懂的记录，
等于花了脱敏的成本拿到零排查价值。树还能自然下钻，`{"items":[{"phone":"..."}]}` 同样覆盖。

**刻意没有收进敏感名单的键名**：`name` / `code` / `username`。
字典的 `name`、成色的 `code`（A–D）是日志的主要可读内容，一刀切会把日志变成
`{"name":"***"}` 这种废数据；而它们本身不构成个人信息。
**这条名单的用法是「宁可多收一个业务字段，也别漏一个个人信息字段」**——漏了是泄露事故，
多收了只是日志难读。

落库前按列宽截断（`after_value` = 2000）：超长会让 MySQL 严格模式直接抛
`Data too long`，**整条 insert 失败**——一个超长的 User-Agent 就能让日志消失。

---

## 五、bizId 的 SpEL 与它的边界

`bizId = "#id"` / `"#auditId"` 用 `MethodBasedEvaluationContext` 对方法形参求值，
形参名依赖编译期 `-parameters`（父 pom 的 `maven-compiler-plugin` 已开；
关掉它 SpEL 会解析失败，表现为 bizId 静默为 null，切面只打 warn）。

三条边界：

1. **表达式写错不抛异常**，只打 warn。记不到 bizId 是内容缺失，把业务请求打挂才是事故。
2. **`biz_id` 是 bigint**，所以主键不是数字的业务对象塞不进去。成色的主键是业务码 A–D，
   它的两个写接口（`GRADE_SAVE` / `GRADE_STATUS`）**刻意不写 bizId**，定位信息在 `after_value` 里。
3. **值统一按数字取**：`@PathVariable Long id` 直接是 Long；若有人写 `#req.itemId` 而该字段是
   String，`Long.valueOf` 也能兜住。

---

## 六、边界与已知缺口（写清楚，别让人以为是 bug）

| # | 缺口 | 说明 |
|---|---|---|
| 1 | **权限拒绝记不到** | `@SaCheckPermission` 由 `SaInterceptor` 在 `preHandle` 校验，早于任何方法级切面。「谁试图越权」（1002）**不在本表**，要看访问日志与 `GlobalExceptionHandler` 的记录。这是方法级 AOP 的固有边界，不是漏做 |
| 2 | **`before_value` 一期留空** | 切面不做回查，只能拿到入参（= 变更后）。审核 / 下架这类需要完整复盘的场景，业务侧本来就有更准的留痕（`item_audit` 记处置人、原因、首审耗时）。真要补 before，得在业务侧读旧值时显式落，属于单独一件事 |
| 3 | **`/admin/auth/**` 未挂** | 登录 / 注册 / 找回没有权限点，也排除在 `SaInterceptor` 之外，不在 CR 11b 的范围内。若要做「管理员登录审计」，那是另一件事（且登录失败时没有 adminId，落库口径也不同） |
| 4 | **队列满时丢弃不可恢复** | 两条降级分支已压测（见 §七）：队列满会**丢弃并打 ERROR**，不重试也不落盘，极端尖峰下会丢操作记录。要补的是一条「丢弃率过高」的告警，不在本次范围 |
| 5 | **写与查之间有异步时间差** | 日志由 `opLogExecutor` 线程池落库，**写完立刻查可能查不到**（实测最多约 1 秒）。这不是查询接口的 bug、也不是丢日志——压测脚本里必须轮询等待，页面上刷新即可。见 §7.3 |

---

## 七、实测（2026-10-06，8089 临时实例，`super` 账号）

| 用例 | 操作 | 期望 | 实测 |
|---|---|---|---|
| 成功路径 | `POST /admin/dict/category/1221/status {"enabled":0}` | result=1 | `code 0`；日志行 `result=1`、`admin_name=超级管理员`、`role_code=SUPER`、`biz_id=1221`、`cost_ms=99` |
| 成功路径（还原） | 同一接口 `{"enabled":1}` | result=1 | `cost_ms=49`；**分类 1221 已还原为启用** |
| 失败路径 | `POST /admin/item/999999999999999999/offline` | 落库且 result=0 | 接口 `code 1101 商品不存在`；日志行 `result=0`、`error_code=1101`、`cost_ms=159` |
| 脱敏（键名） | 无（本批 DTO 无敏感键） | — | 由 `OpLogMasker` 单测同源逻辑覆盖，见 §四 |
| 脱敏（值形） | 下架原因写 `校外引流 13800138000` | 号码被遮 | `after_value` 实录 `校外引流 138****8000`；`note` 里邮箱 → `a***@qq.com` |
| 未登录 | 同一接口不带 token | 不落库 | 接口 `code 1001`；**表里没有对应行**（拦截器早于切面） |
| 异步 | 三处 insert 的线程名 | 在池线程上 | 全部为 `[op-log-1]`，无一在 Tomcat 请求线程 |

`before_value` 在三条 INSERT 的列清单里**根本没出现**——MyBatis-Plus 默认不写 null 字段，
DB 侧就用列默认值 `NULL`。看到「列少了」不是 bug。

### 7.1 拒绝策略压测：把队列打满

做法：用 MySQL 会话持 `LOCK TABLES sys_operation_log WRITE` + `SLEEP`，**堵住 INSERT**，
再对同一写接口并发压测——池里 1~2 个线程全卡在写库上，队列很快填满 512。

| 轮次 | 请求数 / 并发 | 业务响应 | 落库 | 丢弃 | 账 |
|---|---|---|---|---|---|
| 第 1 轮 | 800 / 24 | `{1101: 800}`，**零 5xx** | 569 | 231 | 569 + 231 = 800 ✅ |
| 第 2 轮 | 700 / 24（锁与压测同一次调用，时序更紧） | `{1101: 700}`，**零 5xx** | 554 | 146 | 554 + 146 = 700 ✅ |

日志实录（丢弃那一刻）：

```
[oplog] 队列已满（排队 512/512，活动线程 2），本条操作日志丢弃
```

第 2 轮还加了一条 2 秒采样线程，记录表内行数随时间的变化：

```
72.6s=2  → 77.9s=9 → 82.6s=18 → 87.0s=25 → 91.7s=51
```

**锁内行数几乎不涨、锁一释放阶梯式回灌**——证明确实是「写库被堵 → 队列填满 → 触发拒绝」，
而不是别的路径把日志弄丢了。两轮合计丢弃 377 条，与 ERROR 日志逐条对得上。

**关键结论**：队列打满时业务接口**照常返回**（`1101` 是商品不存在的正常业务码），
没有任何 5xx 或连接异常。「日志不影响主流程」这条在第 2 条降级分支上同样成立。

### 7.2 事务挂法验证：失败记录会不会被回滚吃掉

临时把 `@Transactional(rollbackFor = Exception.class)` 挂到 `DictAdminController#updateCategoryStatus`
（**验证完已移除**），跑三条：

| 用例 | 操作 | 接口返回 | 落库行 |
|---|---|---|---|
| A1 成功 | 停用分类 1221 | `code 0` | `result=1`、`cost_ms=283` |
| A2 成功 | 启用分类 1221（还原） | `code 0` | `result=1`、`cost_ms=57` |
| B 失败 | 启停不存在的分类 999999 | `code 6001 字典项不存在` | `result=0`、`error_code=6001`、`cost_ms=36` |

**B 行存在 = 失败记录没被事务回滚抹掉**，正是 §3.2 那条修正（失败走立即提交、不挂 `afterCommit`）的直接证据。
事务方法下成功记录（A1/A2）走 `afterCommit`、失败记录（B）走立即提交，两条路都验到了。

### 7.3 查询接口与页面（2026-10-06，`GET /admin/log/list`）

接口端实测 27 项全绿（脚本 `oplog-page-test.py`）。挑几条值得记住的：

| 用例 | 结果 |
|---|---|
| 默认分页 | `total` / `id` / `bizId` 都是**字符串**（Long 转 String 规则），比较前必须 `int()` |
| `createTime` | `2026-10-06 23:00:37`（**不带 T**），全局 Jackson 定的格式，前端不用再处理 |
| `beforeValue` | JSON 里**根本不出现**（一期恒 null + `non_null`），前端判空要写 `!x` |
| `module=content` / `item` | 只回对应模块 |
| `result=0` / `=1` | 只回失败 / 成功；失败行都带 `errorCode` |
| `keyword` | 命中 `adminName` 与 `description`（三列 `or`，整体用 `and(...)` 包住，否则会把模块/时间条件冲掉） |
| 日期区间 | 传 `yyyy-MM-dd` 日期，Service 展开成 `00:00:00` / `23:59:59`；起止反了报 **1000** |
| `result=5` | 报 **1000**（不静默返回空表——空表会被当成「真的没有失败记录」） |
| 未知 `module` | 返回空列表（**不做白名单**，见 `OperationLogService` 注释） |
| `pageSize=99999` | 收敛到 100，不报错 |
| `pageNum=99999` | `list` 空、**`total` 仍如实**（total 是匹配总数，不是当前页行数） |
| `readonly` 查日志 | **1002**（没有 `log.view`） |
| 不带 token | **1001** |

**踩到的两个坑（都不是接口问题）**：

1. **造完数据立刻查会查不到** —— 日志是异步落库的，脚本第一版因此偶发失败，加了轮询才稳定。
   §6 第 5 条把它记成已知行为了。
2. **「不带 token 打接口返回 1001」不能证明接口存在** —— `SaInterceptor` 拦在 Spring 的
   静态资源兜底之前，`/admin/zzz-not-exist` 不带 token 也是 1001。判「接口在不在」**必须带 token**：
   不存在的是 1003，存在的是 1002 或 0。

**收尾**：测试产生的 `sys_operation_log` 行已全部删除（`rows_left=0`），分类 1221 未被改动
（两次写操作都是幂等设启用 / 必然失败的下架，不动业务数据）；用户的 8080 / 8082 全程未被触碰。

**顺手发现的一处不一致（已拍板：保留现状）**：`dict_category` 的 `1211 / 1212 / 1213`
（教材教辅下的三个二级分类）当前是**停用**态，而 `db/schema.sql:574-576` 的种子数据写的是启用。
这与「停用一级分类会连带停用子级、启用不连带」的历史测试残留吻合。属数据问题，非本次改动引入；
**2026-10-06 用户决定保留现状，不还原**。

---

## 八、速查

**挂一个 `@OpLog`**：

```java
@PostMapping("/{id}/offline")
@SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.ITEM_OFFLINE)
@OpLog(module = "item", type = "OFF_SHELF", bizId = "#id", desc = "强制下架", saveDiff = true)
public Result<Void> offline(@PathVariable("id") Long id, @Valid @RequestBody ItemOfflineReq req) { ... }
```

| 字段 | 怎么填 |
|---|---|
| `module` | `item` / `review` / `content` / `system`（`order` 预留） |
| `type` | 大写下划线，同模块内唯一。已有：`OFF_SHELF` `DELETE` `APPROVE` `REJECT` `FORCE_OFF` `REPORT_HANDLE` `APPEAL_JUDGE` `CATEGORY_*` `GRADE_*` `MEET_POINT_*` `COLLEGE_*` `CAMPUS_*` `ASSIGN_ROLE` |
| `bizId` | SpEL 取形参，如 `#id`；主键不是数字就留空 |
| `desc` | 给运营看的一句话，如「强制下架」。**不做插值** |
| `saveDiff` | 需要复盘变更内容就开；值 = 入参脱敏快照 |

**自查三条**：
1. 新增的写接口挂了 `@SaCheckPermission` 吗？挂了就必须同时挂 `@OpLog`（CR 11b）。
2. 注解挂在 **Controller** 上吗？（挂 Service 会丢掉入口信息）
3. 落库前要不要多收一个敏感键名进 `OpLogMasker` 的黑名单？

**排查看哪里**：

```bash
# 某个管理员最近做了什么
docker exec cm-mysql mysql -uroot -p'Campus@2026' --default-character-set=utf8mb4 campus_market -e \
 "SELECT admin_name,role_code,module,op_type,biz_id,description,result,error_code,create_time
  FROM sys_operation_log WHERE admin_id=2101 ORDER BY create_time DESC LIMIT 20;"

# 某个业务对象被谁动过（走 idx_biz = module + biz_id）
... -e "SELECT * FROM sys_operation_log WHERE module='item' AND biz_id=4001 ORDER BY create_time;"

# 有没有被丢掉的日志（队列满 / 线程池关闭）
grep -E "\[oplog\]" logs/campus-market.log
```
