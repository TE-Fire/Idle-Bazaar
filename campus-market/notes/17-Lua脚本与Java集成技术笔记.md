# Lua 脚本与 Java 集成技术笔记

## Lua 在 Java 技术栈中的位置

Lua 在 Java 栈里的角色从来不是"另一种写业务的语言"，而是**把一小段需要原子性、需要贴着数据执行的逻辑，搬到数据旁边去跑**。这个定位决定了它的一切特征：脚本短小（通常几行到几十行）、无状态、执行时间以毫秒计、失败要有降级路径。反过来说，凡是需要复杂业务编排、多轮 IO、长时间计算的场景，都不该用 Lua——它的设计目标里没有这些。

Java 服务集成 Lua 的典型场景有三类，按普遍程度排列：

**Redis 服务端脚本**是绝对主流的用法。把"读状态—判断—写状态"打包成一段脚本交给 Redis 单线程执行，原子性由 Redis 的执行模型保证，同时省掉多次往返的网络开销。典型应用是并发控制（占位、库存扣减、限流）与原子性的小状态迁移。本项目订单付款的占位抢占与回滚释放（`lua/occupy.lua`、`lua/release.lua`）就是这一类：判定商品是否可售与写入锁定标记必须一气呵成，拆成两次调用就会出现竞态。

**网关与代理层**是第二类。OpenResty（nginx + LuaJIT）、Kong、APISIX 这类组件把 Lua 作为扩展语言，用在鉴权、限流、请求改写、灰度分流上。对 Java 团队而言通常是消费方而非编写方，了解即可。

**进程内嵌入式**是第三类，通过 LuaJ 这类纯 Java 实现的 Lua 解释器，把脚本跑在 JVM 里，用于规则热更新、表达式计算等"逻辑要可配置"的场景。它没有外部依赖，部署最轻，但版本兼容与性能都要打折扣，生产上远不如 Redis 路线常见。

判断标准可以收得很紧：**需要原子性就用 Redis 脚本，需要可配置就用进程内脚本，两者都不需要就别引入 Lua。**

## 写给 Java 开发者的语法要点

Lua 的语法对 Java 开发者不算陌生——关键字表意直白、`if/for/while` 结构与 Java 基本同构。真正的差异集中在五个点上，每一个都直接对应着后文集成时的某个坑。

**table 是唯一的数据结构**，同时扮演数组、哈希表与对象三种角色。`t = {1, 2, 3}` 是数组，`t = {a = 1}` 是哈希，`t = {name = "x", greet = function(self) ... end}` 就是对象加方法。Java 开发者要习惯"一切容器都是它"，也要随之接受两个后果：数组下标从 1 开始（`t[1]` 是第一个元素，`#t` 取长度），以及 table 无法在 Java 侧直接映射——这是后文返回值处理的难点。

**真假判定只有 nil 和 false 两个假值**。数字 0、空字符串 `""` 在 Lua 里都是真，这与 Java 的严格布尔表达式截然相反。写 `if count then` 判断"count 是否存在"是安全的，但写成 `if flag then` 期待 0 为假就会静默出错——这类 bug 不会报错，只会让分支走向相反的方向。

**函数可以返回多个值**，`return a, b, c` 直接对应 Java 侧数组或元组的解构；反过来，Java 方法只能返回一个值，多值必须封装，这是双向转换中最容易丢失信息的地方。

**数字只有一种类型**。Lua 5.1（Redis 内嵌版本）与 LuaJ 的数字都是双精度浮点，没有 int 与 long 之分。这意味着超过 2^53 的整数（雪花 ID、以分为单位的大额）在 Lua 里会丢失精度。脚本里做 ID 相等判断尚且安全——ARGV 传入的是字符串，字符串比较不经过数字转换——但一旦写 `tonumber(id)` 就落坑：19 位的雪花 ID 转成 double 后尾数变形，相等判断会莫名失败。

**局部变量要显式声明**，不写 `local` 就是全局变量，在常驻进程（Redis 脚本引擎、嵌入式解释器）里意味着跨调用残留状态。字符串用模式匹配而非正则，`string.find`、`string.gsub` 的 pattern 语法与正则在量词、分组上都有差异，短脚本够用，复杂匹配建议挪回 Java。

## Java 集成 Lua 的两条路线

集成方式按"脚本在哪运行"分为进程内与进程外两条路线，两者的 API 形态、失败语义、适用场景完全不同。

**进程内：LuaJ 路线。** 引入 `org.luaj:luaj-jse` 依赖即可，无需任何外部进程。典型代码是拿到 `Globals`、加载脚本、调用并取回 `LuaValue`：

```java
Globals globals = JsePlatform.standardGlobals();
LuaValue chunk = globals.load("function add(a, b) return a + b end");
LuaValue result = chunk.call(LuaValue.valueOf(3), LuaValue.valueOf(4));
int sum = result.checkint(); // 7
```

这条路线的工程细节有四个。其一，`Globals` 不是线程安全的，高并发服务必须每线程一个或池化，共享一个实例会偶发难以复现的崩溃。其二，LuaJ 实现的是 Lua 5.2 语义，与 Redis 内嵌的 Lua 5.1 存在小差异（如整数子类型），同一份脚本不能假设两端行为一致。其三，它可以通过 `luajava.bindClass` 调用 Java 类，看起来很强大，但这会让脚本获得 JVM 内的全部能力，安全边界形同虚设，只应在完全可信的配置场景使用。其四，性能上 LuaJ 作为解释器比 Redis 内的 Lua 快不到哪去，优势仅仅是"没有外部依赖"。

**进程外：Redis 路线。** 脚本通过 `EVAL` 发给 Redis，在服务端执行。Spring Data Redis 提供的入口是 `RedisScript` 加模板方法：

```java
RedisScript<Long> script = RedisScript.of(scriptText, Long.class);
Long result = stringRedisTemplate.execute(script, List.of(key1, key2), arg1, arg2);
```

`RedisScript.of` 只做"持有一段脚本文本加一个结果类型"的容器，真正的发送由模板完成。项目里的做法是把脚本抽成 `.lua` 文件，由 `LuaScripts.load("lua/occupy.lua", Long.class)` 在类初始化时读入——文件缺失在启动期就暴露，而不是等到第一个请求才失败。

**两条路线怎么选，结论先行：Java 服务集成 Lua 的默认答案是 Redis 路线。** 原子性需求天然贴着数据走，Redis 的执行模型免费提供；进程内路线的唯一优势是零外部依赖，而这个优势只有在系统里本来就有 Redis 的前提下才不值得为此多养一个脚本运行时。

## 参数传递：KEYS 与 ARGV 两个列表

Redis 脚本的入参被强制分成 `KEYS` 与 `ARGV` 两部分。Java 侧 `execute` 的第一个列表是 keys，其余是 args；发送时 Redis 要求先声明 `numKeys`，协议上 keys 与 args 拼成同一个数组，前 numKeys 个元素是 key，其余是 argv。Lua 脚本里对应 `KEYS[1]`、`KEYS[2]` 与 `ARGV[1]`、`ARGV[2]`。

这个划分不是风格问题，而是**正确性要求**。Redis Cluster 按 key 计算 slot 决定路由，只有显式列在 `KEYS` 里的 key 才会被纳入 slot 校验；更直接的后果是跨 slot 的 key 写在脚本里，集群模式下直接报错。所以纪律只有一条：**所有 key 走 KEYS，脚本内绝不出现字面量 key**。本项目的两个脚本都遵守这条——holder 哈希的键名同样由 `KEYS[2]` 传入，脚本里只出现下标。

另一个容易混淆的点是**类型：进入 Lua 的一切参数都是字符串**。`String.valueOf(ttl)`、`String.valueOf(orderId)` 这类显式转换不是防御性代码，而是协议事实——`KEYS`/`ARGV` 经序列化器后以 bulk string 到达 Lua 环境，Java 侧的 `Long` 不会自动变成 Lua 数字。需要数字时 Lua 侧自行 `tonumber`，但如前文所述，大整数务必保持字符串形态传递与比较。

同理，Java 对象不能直接作为参数，必须先 JSON 序列化再传入，脚本内用 `cjson.decode` 还原。这会带来一个隐性成本：脚本里操作 JSON 的代码可读性差且难调试，**能用扁平字符串参数表达的就不要传 JSON**——脚本的参数列表本身就是它的接口，越扁平越稳。

## 返回值处理与类型转换

Lua 的返回值经 Redis 转换为 RESP 协议回复，再由 Spring Data Redis 按 `RedisScript` 声明的结果类型反序列化为 Java 对象。这条链路上每一跳都有规则，踩坑几乎都发生在"以为会自动转换"的地方。

Lua 到 Redis 的转换规则（RESP2，即 Redis 6 及以下的事实标准）是：

| Lua 侧类型 | Redis 回复 | Java 侧（resultType） | 注意 |
|---|---|---|---|
| number | integer | `Long` | **含小数截断**（向零取整），超大数不可表示 |
| string | bulk string | `String` / `byte[]` | 二进制安全 |
| table（数组型，键 1..n 连续） | multi-bulk | `List` | **遇到 nil 截断**，nil 之后的元素丢失 |
| table（map 型或稀疏） | 报错 | — | RESP3（Redis 7）才支持 |
| true | integer 1 | `Long` | RESP3 才有真正的布尔 |
| false | nil | `null` | 不是 `false`，也不是 0 |
| nil（顶层返回） | nil | `null` | 需在 Java 侧兜底 |

三个最值得记住的结论。**其一，Lua 返回数字必被截断成整数**——`return 1.5` 到 Java 侧是 1，脚本里做除法再返回的设计会静默失真。**其二，Lua 返回 false 到 Java 侧是 null 而非 false**——用 `Boolean` 接收并直接 `if` 判断会把"脚本说否"和"脚本没返回"混为一谈，本项目的 `release` 因此约定了"0 表示未释放"的数字协议，而不是布尔语义。**其三，数组型 table 里的 nil 会截断后续元素**——`return {1, nil, 3}` 到 Java 侧只有一个元素，构造返回数组时要保证连续。

Java 侧的接收则由 `RedisScript.of(text, Long.class)` 的第二个参数决定。整数回复映射 `Long`，bulk string 映射 `String`，multi-bulk 映射 `List`。返回 nil 时框架给到 `null`，因此调用处必须显式兜底——`occupy` 的 `r == null ? OCCUPY_MISS : r` 就是把"脚本没返回"与"缓存缺失"合并成同一个降级语义。

还有一个工程选择值得说明：**参数与返回值全为字符串与数字时，用 `StringRedisTemplate` 而非默认模板**。默认模板的 JSON 序列化会给普通字符串值也加上 `@class` 类型信息，让 Lua 收到的值带着转义噪音；纯字符串状态机不需要这层"通用性"，`StringRedisTemplate` 的字符串序列化器让 Java 与 Lua 之间看到的值完全一致。

## 异常处理：三个层级的边界

Lua 集成里的异常要分三个层级看，每一层级的失败语义不同，处理方式也不同。

**Lua 层**有两种主动报错手段。`error("msg")` 抛出脚本错误，终止执行；`redis.pcall` 则以保护模式调用 Redis 命令，命令出错时不中断脚本，而是返回一个带 `err` 字段的 table 供脚本自行分支。后者适合"某个 key 不存在时走另一条路"这类可预期失败，前者用于真正的不可恢复错误。此外 `redis.error_reply(x)` 与 `redis.status_reply(x)` 可以强制指定返回给调用方的回复类型，用于构造协议层的错误信号。

**Redis 服务端层**把脚本错误变成一个 error reply。客户端驱动（Lettuce）将其转为运行时异常抛出——`RedisCommandExecutionException` 一类。关键认知是：**脚本执行失败不会作为业务结果返回，而是以异常形式穿透到 Java 调用栈**。如果调用点在 `@Transactional` 方法内且没有捕获，事务会随异常回滚。这意味着"脚本失败"与"业务失败"在事务语义上完全等价，必须有意识地分流。

**Java 层**的分流策略就是显式 catch 加降级。本项目的两个脚本是标准范式：`occupy` 捕获一切异常，记 warn 日志后返回 `OCCUPY_MISS`——Redis 前置不可用时放行给 MySQL 条件更新兜底，功能不受影响，只是并发压力回到数据库；`release` 同样捕获，但降级语义是"返回 0，等待 TTL 自愈"——释放失败最坏结果是占位标记在 TTL 到期后自然消失，商品短暂不可售，可接受。**降级路径必须逐脚本想清楚**：哪些失败可以放行、哪些失败必须让事务回滚、哪些失败只需要日志，这三类答案对应三种完全不同的代码结构。

写脚本时还有一条与异常无关但同样重要的纪律：**脚本必须短。** Redis 以单线程执行脚本，一段耗时百毫秒的脚本会阻塞所有客户端。经验上限是百微秒级，超过即应拆分为多次调用或在 Java 侧完成。附带的效果是，脚本应当只做"判定加最小写入"，把组装、计算、通知留给 Java。

## 常见集成模式对比

把前文的线索汇到一张表里。四种模式的差别不在语法，而在**执行位置**——它决定了原子性来源、失败语义与调试方式：

| 模式 | 执行位置 | 原子性来源 | 适用场景 | 主要代价 | 本项目对应 |
|---|---|---|---|---|---|
| Redis EVAL/EVALSHA | Redis 服务端 | 单线程执行模型 | 并发控制、原子状态迁移、限流 | 能力受限（无网络 IO）、调试难、阻塞风险 | occupy / release |
| 进程内 LuaJ | JVM 内 | 无（需自行加锁） | 规则热更、表达式计算、零外部依赖 | `Globals` 线程安全、性能、版本锁定 5.2 | 无 |
| 网关层 Lua（OpenResty/Kong） | nginx 进程 | 单线程执行模型 | 鉴权、限流、流量改写 | 需维护另一套技术栈 | 无（消费方） |
| 外部 lua 进程 | 独立进程 | 无 | 已被前两取代 | 进程间通信开销 | 无 |

其中 EVAL 与 EVALSHA 的取舍值得单独一说，因为它是 Redis 路线内部唯一的性能议题。`EVAL` 每次发送完整脚本文本，带宽随脚本长度增长；`EVALSHA` 先用 `SCRIPT LOAD` 把脚本载入服务端拿到 sha 摘要，之后只传 40 字节的摘要，省带宽也省解析。代价是服务端重启或 `SCRIPT FLUSH` 后 sha 失效，需处理 `NOSCRIPT` 错误并回退到重新加载。Spring Data Redis 的高层 API 固定走 `EVAL`，没有内置的 sha 管理——对 occupy/release 这种几十行的脚本完全无感，只有当脚本达到数百行、QPS 极高时才值得自行实现 sha 缓存与兜底。另一个演进方向是 Redis 7 的 Functions 机制（`FUNCTION LOAD`），把脚本作为函数库管理，比单脚本更结构化，但兼容性要求 Redis 7+，本项目维持单脚本已足够。

## 落到本项目的三条纪律

回到 `campus-market` 的实践，前文的通用规则已经固化为三条可检查的纪律，后续新增脚本照此办理。

**其一，脚本一律外置。** 所有 `.lua` 文件放 `campus-core/src/main/resources/lua/`，经 `LuaScripts` 在类初始化时读入，业务代码中不再出现内联脚本文本。文件缺失在启动期失败，把"脚本忘了提交"这类事故拦截在部署阶段。

**其二，key 走 KEYS、参数全字符串。** 两个脚本的 key 均由 KEYS 传入，脚本内无字面量 key；`orderId`、TTL 等值一律 `String.valueOf` 后作为 ARGV，Lua 侧只做字符串比较，不做 `tonumber`——雪花 ID 在 double 里活不过精度检查。

**其三，返回值协议加降级语义写进脚本头注释。** occupy 约定 1（抢到）/ 2（同一订单重入）/ 0（冲突）/ -1（缓存缺失必须放行），release 约定 1（已释放）/ 0（holder 不符不碰）；调用侧统一 try-catch，失败降级为可接受语义而非异常穿透。脚本头注释把这些协议与"为什么"一次写清，因为 Lua 脚本没有类型系统兜底，协议即契约。

一句话收束：Lua 脚本是"短、原子、可失败降级"的补丁，不是业务主场。守住这个定位，它就是并发控制里最锋利的工具；越过这个定位，它就会变成最难排查的故障源。
