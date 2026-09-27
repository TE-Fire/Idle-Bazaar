# 笔记 · Jackson 序列化配置与时间格式

这份笔记对应 `campus-common/src/main/java/com/campus/common/config/JacksonConfig.java` 与 `constant/TimePattern.java`，是 tech/01「5.3 序列化（Jackson 四条硬规则）」的展开。起因是三个具体问题：配置类到底能管到 Jackson 的哪些部分；`java.time` 为什么被单独拎出来配置；以及 `DATETIME_PATTERN` 这种单个静态常量为什么不够用，能否改成一个时间格式常量类。下面按这三块记录，第四部分列出当前实现里还没覆盖的缺口——按约定先只记录，等实际出问题再改代码。

---

## 一、配置类能管到哪一层

### 1.1 Jackson 能序列化什么

Jackson 的核心能力是把 Java 对象与 JSON 双向转换，覆盖的类型大致是这几类：

| 类别 | 具体类型 | 备注 |
|---|---|---|
| Bean / Record | POJO、Record（2.12 起原生支持） | 按 getter / 字段 / 构造参数反射绑定 |
| 集合与数组 | List、Set、Map、数组 | Map 的 key 需要能转成 String |
| 标量 | 基本类型及包装类、String、枚举 | 枚举默认输出 `name()`，可用 `@JsonValue` 改成 `code` |
| 大数 | `BigDecimal`、`BigInteger` | 金额必须走 `BigDecimal`，禁止 double |
| 旧日期 API | `java.util.Date`、`Calendar` | 受全局 `simpleDateFormat` 控制 |
| 新日期 API | `java.time.*` | **依赖额外模块**，见第二部分 |
| 容器语义 | `Optional`（Jdk8Module） | 不注册会输出 `{present:true, ...}` 这种包装结构 |
| 树模型 | `JsonNode`、`ObjectNode` | 用于不规则结构，不做让你为难的类型绑定 |

关键点：**"能序列化"和"序列化成什么样子"是两件事**。上面绝大部分类型 Jackson 天然就认，配置类要解决的不是"能不能"，而是"输出格式是否符合前后端约定"。

### 1.2 定制的六个层次

配置可选的手段可以分成六层，从上到下粒度越来越细：

| 层次 | 配置手段 | 本项目是否用到 |
|---|---|---|
| 类型级 | `serializerByType` / `deserializerByType`、`SimpleModule.addSerializer` | ✅ Long、BigDecimal、LocalDateTime、LocalTime |
| 模块级 | `modules(...)`：`JavaTimeModule`、`Jdk8Module`、自定义 Module | ⚠️ 依赖 Boot 自动注册，未显式声明 |
| 特性开关 | `SerializationFeature` / `DeserializationFeature` / `JsonParser.Feature` | ✅ 忽略未知字段、禁用日期时间戳 |
| 全局格式 | `simpleDateFormat`、`timeZone`、`defaultDateFormat` | ✅ |
| 可见性 | `serializationInclusion`、命名策略、`@JsonIgnore`、`@JsonView` | ✅ NON_NULL |
| 结构级 | 多态 `@JsonTypeInfo`、Mixin、`FilterProvider` | ❌ 未使用 |

日常开发里 90% 的需求落在**类型级**（某个类型的所有字段统一改输出）和**字段级注解**（某一个字段单独改），后面第三部分的后半段专门讲这两者的优先级。

### 1.3 为什么写 Customizer 而不是自己 new ObjectMapper

这是配置类里最容易踩的一个坑，注释里也标了。Spring Boot 的 `JacksonAutoConfiguration` 会做一件很重要的事：扫描 classpath，发现 `jackson-datatype-jsr310` 就注册 `JavaTimeModule`，发现 `jackson-datatype-jdk8` 就注册 `Jdk8Module`，`jackson-module-parameter-names` 同理注册 `ParameterNamesModule`。

如果自己在配置类里写 `@Bean public ObjectMapper objectMapper() { return new ObjectMapper(); }`，Boot 的自动配置检测到已有同名 Bean 就会整体退让（`ObjectMapper` 的 `@ConditionalOnMissingBean`），于是那些自动注册的模块一个都不会挂上去。症状很有特点：编译正常、启动正常，但 `LocalDateTime` 字段开始报错或者输出成一坨奇怪的结构——<｜hy_place▁holder▁no▁813｜>的人往往以为是自己配错了日期格式，实际上是模块丢了。

正确做法是走 Boot 留的扩展点 `Jackson2ObjectMapperBuilderCustomizer`：它拿到的是 Boot 已经配好一半的 builder，你只往上叠加差异部分。**只加不改，就不会丢东西**。

同理，`spring.jackson.*` 配置项（如 `spring.jackson.date-format`、`default-property-inclusion`）也是 Boot 通过同一条链路应用的。一旦混用"配置项 + 自定义 ObjectMapper Bean"，优先级容易理不清，所以约定统一走 `JacksonConfig` 一处，`application.yml` 里不写 `spring.jackson.*`。

---

## 二、`java.time` 为什么需要单独配

### 2.1 模块不在核心包里

`java.time` 的支持不在 `jackson-databind` 里，而是独立 artifact `jackson-datatype-jsr310`（对应类是 `JavaTimeModule`）。这是历史原因：这套 API 是 Java 8 才引入的，而 Jackson 2.x 发布时要兼容 Java 6/7，只能做成可选模块。

Spring Boot 的 web starter 通常已经间接引入了它，所以项目里不用显式加依赖；但**能不能用只取决于 classpath 上有没有这个 jar**，属于"隐式生效"。一旦有人为了瘦身排掉了某个传递依赖，这条链路就断了，而且没有任何编译期提示。

### 2.2 时间戳开关

`JavaTimeModule` 挂上去之后，默认行为仍不是我们想要的。`SerializationFeature.WRITE_DATES_AS_TIMESTAMPS` 默认为 `true`，此时 `LocalDateTime` 会被序列化成数组 `[2026, 9, 27, 18, 22, 49]` 或小数秒数字。这对机器友好，对前端是灾难——小程序和浏览器拿到数字还得猜单位是秒还是毫秒。

所以配置里显式关掉：

```java
.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
```

关掉之后输出的是 ISO-8601 风格字符串，典型样子是 `2026-09-27T18:22:49`——注意中间那个 **`T`**，它是 ISO 标准的一部分，但不符合 tech/01 里"时间统一 `yyyy-MM-dd HH:mm:ss`"的约定，也更不符合用户看到的习惯。这就是为什么还需要下一步：显式注册带自定义格式的序列化器。

### 2.3 各类型的格式互不相通

这是最容易误解的一点：**注册 `LocalDateTime` 的序列化器，不会影响 `LocalDate`，更不会影响 `Instant`**。在 Jackson 内部它们是完全独立的注册项，各有各的默认值：

| 类型 | 默认输出（关闭时间戳后） | 是否已在项目中注册 |
|---|---|---|
| `LocalDateTime` | `2026-09-27T18:22:49` | ✅ `LocalDateTimeSerializer` + `LocalDateTimeDeserializer` |
| `LocalDate` | `2026-09-27` | ❌ 依赖默认，**恰好**与 `TimePattern.DATE` 一致 |
| `LocalTime` | `18:22:49.123`（可能带纳秒） | ✅ 截断为 `HH:mm:ss` |
| `Instant` | `2026-09-27T10:22:49.123Z` | ❌ 未注册 |
| `ZonedDateTime` / `OffsetDateTime` | 带偏移的 ISO 串 | ❌ 未注册 |
| `Duration` / `Period` | 秒数 / ISO 串 | ❌ 未注册 |

`LocalDate` 那一行值得留意：它现在是对的，但属于"碰巧默认等于约定"。一旦某天 Boot 版本变更调整了默认格式，这类隐性依赖不会报错，只会安静地输出错格式，然后在某个前端页面显示异常。凡是能显式钉死的地方尽量显式写。

项目里实体字段按 tech/03 约定一律用 `LocalDateTime`，所以 `Instant` 现在用不上；但只要引入 Sa-Token 的会话时间戳、MQ 的消息头时间或者第三方对接，就很可能遇到——这也是把 `Instant` 列进缺口清单的原因。

### 2.4 时区是另一个独立的坑

`LocalDateTime` 本身**不带时区信息**，它只是"某个地区的墙上时间"。因此把它格式化成字符串时，`DateTimeFormatter` 不需要、也不允许有 zone；反过来，把没有 zone 的 formatter 用在 `Instant` 或 `ZonedDateTime` 上，会直接抛 `UnsupportedTemporalTypeException`（formatter 找不到时区，无法决定该输出哪个偏移）。

这也是 `TimePattern` 类注释里那句警告的由来。给带时区类型配 formatter 时必须显式补 zone：

```java
DateTimeFormatter.ofPattern(DATETIME).withZone(DEFAULT_ZONE);
```

另外，`LocalDateTime` 不带时区意味着**序列化输出的时区由 Jackson 全局时区决定**（配置里的 `Asia/Shanghai`），而不是由字段本身决定。所以全局时区必须配，且要与 MySQL 连接参数、JVM 默认时区一致，否则会出现"库里查出来是对的，接口输出差 8 小时"这类经典问题。

### 2.5 当前注册的已知缺口

按上面的清单，`JacksonConfig` 目前补了两项（`LocalDateTime`、`LocalTime`），还差：

1. **`LocalDate`** —— 现在靠默认，建议显式注册 `DATE_FORMATTER`
2. **`Instant` / `ZonedDateTime` / `OffsetDateTime`** —— 完全未注册，出现即输出非预期格式
3. **时区硬编码** —— `ZoneId.of("Asia/Shanghai")` 直接写在配置类里，其他模块要用时区时容易各写各的，应收进 `TimePattern`

---

## 三、时间格式要不要做成常量类

答案是**要**，而且项目里已经是这么做的。下面记录推导过程。

### 3.1 为什么单个 DATETIME_PATTERN 不够

`yyyy-MM-dd HH:mm:ss` 只能满足"完整时间"这一种场景。真实使用里至少还有：

| 场景 | 需要的格式 | 例子 |
|---|---|---|
| 详情接口的时间戳 | `yyyy-MM-dd HH:mm:ss` | 商品发布时间 |
| 只关心日期的字段 | `yyyy-MM-dd` | 活动开始日期、生日 |
| 时间点字段 | `HH:mm:ss` | 每日开放时段 |
| 日志文件 / 审计 | `yyyy-MM-dd HH:mm:ss.SSS` | 排查并发问题需要毫秒 |
| 文件名、导出批次号 | `yyyyMMddHHmmss` | 不能含冒号，Windows 文件名非法 |
| 分库分表后缀 | `yyyyMMdd` | 按天归档 |

把它们全散落在各个 Service 里写 `"yyyyMMdd"` 字面量，问题不在重复书写，而在于**改的时候改不全**：某天要把日志精度提到毫秒，你不知道有多少处硬编码。集中到一个常量类之后，改一处即可，IDE 也能直接列出所有引用。

### 3.2 为什么同一份格式要写两遍

`TimePattern` 里每个格式都有两个成员：

```java
public static final String DATETIME = "yyyy-MM-dd HH:mm:ss";            // 给注解
public static final DateTimeFormatter DATETIME_FORMATTER =              // 给代码
        DateTimeFormatter.ofPattern(DATETIME);
```

看起来冗余，实际是两种使用场景的硬性约束不同：

| 形态 | 给谁用 | 为什么必须是它 |
|---|---|---|
| `String` | 注解：`@JsonFormat(pattern = TimePattern.DATE)` | 注解属性值必须是**编译期常量**，塞不进对象 |
| `DateTimeFormatter` | 代码：注册序列化器、手动 `format()` | 不可变且**线程安全**，静态复用省掉每次解析 pattern 的开销 |

关于第一条要补充一点：Java 注解的属性值在编译期就被写进 class 文件的常量池，只能是字面量、`static final` 常量表达式或枚举常量。`DateTimeFormatter.ofPattern(...)` 是运行期方法调用的结果，天然满足不了。所以不是"不想复用"，而是"技术上放不进去"。

关于第二条：`DateTimeFormatter.ofPattern()` 每次调用都要解析一遍 pattern 字符串，放到 hot path 上有实际开销；不过更重要的是它的不可变与线程安全特性——`SimpleDateFormat` 不线程安全是老生常谈，而 `DateTimeFormatter` 可以安全地共享，因此做成 `static final` 是正确的。

时区同理，建议一并收进去：

```java
public static final String ZONE = "Asia/Shanghai";
public static final ZoneId   DEFAULT_ZONE       = ZoneId.of(ZONE);
public static final TimeZone DEFAULT_TIME_ZONE  = TimeZone.getTimeZone(DEFAULT_ZONE);
```

`ZoneId` 给 `java.time` 用，`TimeZone` 给 Jackson builder 和旧 API 用，两者都需要。

### 3.3 局部覆盖的三种手段

全局规则满足不了某个字段时，按推荐顺序有三种做法：

| 手段 | 写法 | 适用场合 |
|---|---|---|
| 字段注解 | `@JsonFormat(pattern = TimePattern.DATE)` | **首选**。单个字段换格式 |
| 指定序列化器 | `@JsonSerialize(using = XxxSerializer.class)` | 需要非格式化逻辑（脱敏、单位换算） |
| VO 里用 String | 手动 `format()` 后赋值 | 导出、对接第三方，字段本身语义不是时间 |

优先级：**字段注解 > 全局类型注册**。所以即使全局把 `LocalDateTime` 钉成了带时分秒，某个只要日期的字段加一行 `@JsonFormat(pattern = TimePattern.DATE)` 就能单独覆盖，不影响别人。

### 3.4 一个容易被忽略的前提

`@JsonFormat` 能覆盖全局注册，**前提是全局注册的那个序列化器本身实现了 `ContextualSerializer`**。

Jackson 在序列化某个字段时，会调用已注册序列化器的 `createContextual(...)` 方法，让它有机会读取字段上的 `@JsonFormat` 并返回一份新的实例。`JavaTimeModule` 里的 `LocalDateTimeSerializer` 等原生序列化器都实现了这个接口，所以第 2.3 节里注册的格式能被注解正常覆盖。

但项目里的 `BigDecimal2Serializer` 是**自定义**的，直接继承 `JsonSerializer` 而没有实现 `ContextualSerializer`——这意味着**给 BigDecimal 字段加 `@JsonFormat` 不会有任何效果**。当前约定"金额统一 2 位"是全场景规则，暂时不需要例外，所以没有问题；日后若出现"某个字段要 4 位小数"的需求，不能指望加个注解解决，得另写序列化器或改用 VO 转换。这些隐性约束决定了**加一个配置手段之前，最好先确认它被落实的方式**——否则写下的注解会静默失效。

---

## 四、速查与待办

**加一个新时间格式的三步**：在 `TimePattern` 加 `String` 常量 → 加对应 `DateTimeFormatter` → 在字段上用 `@JsonFormat` 引用（或在 `JacksonConfig` 注册为某类型的默认）。

**排查"时间格式不对"的顺序**：字段上有没有 `@JsonFormat` → 该类型在 `JacksonConfig` 里注册了没有 → `WRITE_DATES_AS_TIMESTAMPS` 有没有关 → `JavaTimeModule` 在不在 classpath（有没有人自定义了 `ObjectMapper` Bean 把它挤掉）。

**待补项**（按约定，等实际碰到再动）：

| 项 | 触发时机 |
|---|---|
| 注册 `LocalDate` | 任何时候想消除隐式默认时 |
| 注册 `Instant` / `ZonedDateTime` | VO 或第三方对接出现带时区类型时 |
| 时区收进 `TimePattern` | 出现第二处需要时区的地方时 |
| `BigDecimal2Serializer` 支持 `@JsonFormat` | 出现非 2 位小数的金额字段时 |
