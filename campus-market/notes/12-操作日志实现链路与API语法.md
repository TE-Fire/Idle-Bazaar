# 操作日志实现链路与 API 语法

本笔记记录操作日志功能从「写动作埋点」到「异步落库」再到「管理端查询」的完整链路，重点不在业务字段含义，而在**实现它需要的思路与 API 语法**。读侧（A9 查询页）只做只读查询，逻辑简单，本文略去细讲；本文的主角是写侧那一套 `@OpLog` 基础设施——它全部由 Spring / AspectJ 内置能力拼装，不依赖任何第三方框架。先建立两条贯穿全篇的判断：**落库必须异步，且异步之前必须在请求线程上把所有要记的东西抓成不可变快照**；**日志失败绝不能影响业务结果**。这两条决定了后面每一个类为什么存在、为什么长那样。

## 整体思路：一条写入链路分三段

一个写接口加上 `@OpLog` 注解后，会触发一条固定的处理流。`OpLogAspect` 是入口，它在**请求线程**上完成所有「上下文抓取」：取登录管理员、把入参序列化脱敏、用 SpEL 把 `#auditId` 这样的表达式求成真实业务 id、量方法耗时、判成功失败。抓完之后把这些值装进一个 `OpLogRecord` 不可变对象，交给 `OpLogWriter`。`OpLogWriter` 决定「此刻提交还是等事务提交后再提交」，然后把任务丢进 `opLogExecutor` 线程池，由池里的线程真正执行 `INSERT`。

把链路拆成切面与写器两段，是因为最容易出错的两件事分属不同线程：`OpLogAspect` 里的上下文抓取必须在请求线程做（登录态、IP、UA 都是 ThreadLocal，离开请求线程就取不到），而落库在另一个线程。一旦把这两段混在一个类里，改一处就可能误伤另一处，所以切面只负责「抓」，写器只负责「写」。

异步落库带来一个硬约束：线程池线程里读不到任何请求上下文。这就是 `OpLogRecord` 存在的根本原因——它是一份在请求线程上抓好、之后绝不再改的快照（用 Java 的 `record` 类型表达「不可变」语义）。切面把 adminId、module、opType、bizId、描述、脱敏后的变更内容、成败、错误码、耗时、客户端 IP、UA、traceId 全部塞进它，写器那边只读这份快照，不碰任何上下文。

切面标注在 Controller 的写方法上，而不是 Service 方法上。原因在于一个 Service 方法常被两个入口复用（例如 `ItemAdminService#forceOffline` 同时服务于商品页和审核队列），标注在 Service 上会丢掉「从哪个入口来」这一信息；而权限点与入口一一对应，标注在 Controller 上才能既记到入口来源、又和 `@SaCheckPermission` 成对出现。一个连带结论是：权限被拒的访问记不进本表——`@SaCheckPermission` 由 `SaInterceptor` 在 `preHandle` 早于方法切面校验，越权请求根本进不到切面，这条线索只能从访问日志与异常日志反查。

落库时机的判断依据不只是成败，还有「当前线程有没有活动事务」这一维度，两条路的具体语义在「线程池与事务边界：OpLogWriter」一节展开。一句话概括：**成功且线程上确实有事务在进行时，把写操作注册为事务提交后的回调**，事务回滚就不留「从没发生过的操作」；**其余情况一律当场交给线程池立即写**——失败的尝试本身就是该留下的记录，它不在业务事务里，回滚影响不到它。

## 实现所需的 API 语法

下面按实现顺序列出关键 API，每个都给出最小可用片段与用途。这些全是 Spring / AspectJ / JDK 自带，无需额外依赖。

### 自定义注解 @OpLog

注解本身只是「标记 + 配置载体」，真正起作用的是切面。元注解决定它能在哪、活到什么时候：

```java
@Target(ElementType.METHOD)          // 只能标在方法上
@Retention(RetentionPolicy.RUNTIME)  // 运行时保留，切面才能通过反射读到
@Documented
public @interface OpLog {
    String module();                  // 必填：item / review / content / system
    String type();                    // 必填：APPROVE / REJECT / ...
    String bizId() default "";        // 选填：业务 id 的 SpEL 表达式，如 "#auditId"
    String desc() default "";
    boolean saveDiff() default false; // 是否记录脱敏后的入参快照
}
```

`bizId` 之所以是 `String` 而不是 `Long`，是因为它存的是「表达式文本」而非值；真正的值要靠 SpEL 在运行时求出来（见下节）。

### AspectJ 切面：拦截带注解的方法

Spring AOP 用 AspectJ 的注解来声明切面与切点。`@Around` 是环绕通知，能同时拿到「方法执行前」和「执行后」，因此成功与失败两条路能共用同一段抓取代码，避免只改一边导致两边记的字段不一致：

```java
@Aspect
@Component
public class OpLogAspect {

    @Around("@annotation(opLog)")   // 切点：任何带 @OpLog 的方法；opLog 是注入的注解实例
    public Object around(ProceedingJoinPoint pjp, OpLog opLog) throws Throwable {
        // ... 抓取快照 ...
        try {
            Object result = pjp.proceed();        // 真正执行业务方法
            emit(opLog, ..., true, null);          // 成功
            return result;
        } catch (Throwable t) {
            emit(opLog, ..., false, errorCodeOf(t)); // 失败：仍记一条 result=0
            throw t;                               // 异常原样抛回，绝不能被切面吞掉
        }
    }
}
```

`ProceedingJoinPoint` 是被切方法的上下文：`.getArgs()` 拿入参数组，`.getSignature()` 拿方法签名（强转成 `MethodSignature` 后可取 `Method` 对象）。`pjp.proceed()` 是放行业务方法的开关——不调用它业务就不执行，调用多次就是重试。

### SpEL 求 bizId：表达式解析 + 形参名发现

这是本套设施里最不直观、也最需要理解的一段。`@OpLog(bizId = "#auditId")` 里的 `"#auditId"` 不是变量替换，而是一段 **SpEL 表达式**，要在运行时把 `auditId` 这个形参的真实值算出来。两个类配合完成这件事。

`SpelExpressionParser` 负责「解析并执行表达式」；`MethodBasedEvaluationContext` 提供「表达式能看见的变量环境」（目标对象、方法、入参、形参名）；`ParameterNameDiscoverer` 负责把编译后端掉的形参名（`arg0`）还原成真名（`auditId`）——它依赖编译期 `-parameters` 开关（父 pom 的 maven-compiler-plugin 已开，关掉则 bizId 静默为 null，切面只打 warn 不抛异常）：

```java
private final SpelExpressionParser spelParser =
        new SpelExpressionParser();
private final ParameterNameDiscoverer parameterNameDiscoverer =
        new DefaultParameterNameDiscoverer();

private Long resolveBizId(OpLog opLog, ProceedingJoinPoint pjp) {
    String expression = opLog.bizId();
    if (expression.isBlank()) return null;
    try {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                pjp.getTarget(), signature.getMethod(),
                pjp.getArgs(), parameterNameDiscoverer);
        Object value = spelParser.parseExpression(expression).getValue(context);
        return value == null ? null
                : (value instanceof Number n ? n.longValue()
                                             : Long.valueOf(value.toString().trim()));
    } catch (Exception e) {
        log.warn("[oplog] bizId 表达式求值失败 expr={}，本条不记业务 id", expression, e);
        return null;                       // 记不到 id 是内容缺失，不能把主请求打挂
    }
}
```

判断的要点：**表达式求值失败只记 null 不抛异常**。记不到业务 id 只是日志少一个维度，把业务请求一起打挂才是事故。`getValue(context)` 返回 `Object`，这里统一按数字兜底，兼顾 `@PathVariable Long id` 直接是 Long、或 `#req.itemId` 是 String 两种情况。

入参快照要带形参名，同样靠 `parameterNameDiscoverer.getParameterNames(signature.getMethod())`；取不到名字时退化成 `arg0`/`arg1` 也不丢字段。Servlet 对象、文件上传、校验错误这些入参序列化无意义（或会爆栈），在 `arguments()` 里被显式排除。

### 不可变快照 OpLogRecord

用 Java 的 `record` 而非普通类，是因为字段一旦抓取就不应再改——`record` 的不可变语义正好，也省掉 getter/setter 样板。字段名与实体 `SysOperationLog` 一一对应，由写器负责搬运：

```java
public record OpLogRecord(
        Long adminId, String module, String opType, Long bizId,
        String description, String afterValue, boolean success,
        String errorCode, int costMs, LocalDateTime occurredAt,
        String ip, String ua, String traceId) {}
```

### 线程池与事务边界：OpLogWriter

这一节要解决的问题是：**这条日志什么时候真正写进数据库，与业务事务的成败建立什么关系**。诉求有两个方向相反的方面——业务回滚了，「从没发生过的操作」不该留痕；业务失败了，「有人试过并失败」恰恰要留下。一个时机满足不了两边，所以 `record()` 分两条路。

```java
public void record(OpLogRecord record) {
    if (record.success() && TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override public void afterCommit() { submit(record); }
                });
        return;
    }
    submit(record);
}

private void submit(OpLogRecord record) {
    try {
        opLogExecutor.execute(() -> write(record));   // 交给池线程，立即返回
    } catch (Exception e) {
        // 兜的是 execute 自身抛出的运行时异常（执行器未初始化或已销毁）。
        // 队列满不会走到这里——OpLogRejectedHandler 只打 ERROR 然后返回，不抛。
        log.error("[oplog] 提交异步落库失败，本条丢弃 ...");
    }
}
```

**`if` 的两个条件用 `&&` 连接，缺一不可**。`record.success()` 是快照里的成败标记；`TransactionSynchronizationManager.isSynchronizationActive()` 是静态无参方法，问的是「当前线程此刻是否处于一个允许注册事务回调的活动事务中」。这个类内部用 ThreadLocal 存放当前线程的事务资源与同步器列表，因此它是线程绑定的——事务在哪个线程上开启，就只有那个线程看得见。除了决定分支，这个判断还有一层作用：`registerSynchronization` 在同步未激活时会抛 `IllegalStateException`，所以它同时是前置守卫。

`registerSynchronization` 也是静态方法，作用是把参数对象加进当前线程的同步器列表，**此时不执行任何数据库操作**。`TransactionSynchronization` 是接口而非抽象类，声明了 `beforeCommit`、`afterCommit`、`beforeCompletion`、`afterCompletion(int status)`、`flush`、`suspend`、`resume` 等方法；从 Spring 5 起这些方法全部带 default 空实现，所以 `new TransactionSynchronization() { ... }` 这个匿名内部类只覆写 `afterCommit` 一个就合法。匿名内部类在方法体内引用形参 `record`，要求 `record` 满足 effectively final——自 Java 8 起不要求显式写 `final`，只要求从未重新赋值，此处成立。`return;` 不能省：注册完必须结束方法，否则会继续执行下面的 `submit`，同一条日志落两次。

事务收尾时同步器的回调顺序是 `beforeCommit` → 真正 commit → `afterCommit` → `afterCompletion`。**回滚时只有 `afterCompletion(STATUS_ROLLED_BACK)`，`afterCommit` 不会被调用**——这正是不把失败记录也注册成回调的原因：注册了就永远不会执行，而失败线索恰恰最该留下。

成功记录之所以「等提交后才写」，靠的不是「写了再撤销」，而是**在提交之前根本没有写过**。注册回调只是一次内存中的登记，真正的 `INSERT` 要等事务提交成功、`afterCommit()` 被回调时，才由 `submit` 转给线程池。事务若回滚，这条日志连「写进数据库」这一步都没发生，不存在「要不要删掉」的问题。

失败记录之所以不会被回滚抹掉，是因为它**从头到尾都不在业务事务里面**。事务是绑定在「请求线程 + 它借到的那条连接」上的局部状态，`ROLLBACK` 是发给那条连接的命令，只撤销该连接上尚未提交的改动。日志的 `INSERT` 跑在线程池线程上，用的是连接池里**另一条**连接，且是自动提交模式，插进去即落盘。请求线程回滚自己的连接时，这条日志早已持久化——不是「回滚时特意跳过了它」，而是**回滚这个动作作用不到它**。

判断依据有两个维度，因此组合出四种情况，而不是「成功一条、失败一条」那么简单。第二个维度——当前线程有没有活动事务——与业务成败无关，取决于注解标注在哪一层。标注在 Controller 上时，方法内的事务在 `pjp.proceed()` 返回前就已提交并解绑，`isSynchronizationActive()` 恒为 false；标注在带 `@Transactional` 的 Service 方法上时，`@OpLog` 切面与事务通知处于同一代理链，先后由 `@Order` 决定，只有切面位于事务通知内层时该值才为 true（本项目没有这种标注，故不展开）。

| 业务结果 | 注解标注位置 | 进入哪个分支 | 真正写库的时机 |
|---|---|---|---|
| 成功 | Service 事务方法 | 注册 afterCommit | 事务提交成功之后 |
| 成功 | Controller | 立即 `submit` | 当场（业务此时已提交完毕） |
| 失败 | Service 事务方法 | 立即 `submit` | 当场（池线程另一条连接，随后回滚影响不到它） |
| 失败 | Controller | 立即 `submit` | 当场 |

四种情况并非四套逻辑：只有第一行是延迟写，其余三行都汇到同一个 `submit`，代码结构实际是「一个延迟分支 + 一个兜底出口」。**本项目当前落在第 2 行与第 4 行**——`@OpLog` 全部标注在 Controller 方法上，成功记录也是立即写。它之所以等价于「提交后才写」，是因为它写的时候业务已经提交完毕，靠的是注解位置带来的时序，不是 `afterCommit`。那段延迟逻辑是为将来把注解标注到 Service 事务方法上预留的正确语义。

`submit` 用 `opLogExecutor.execute(Runnable)` 把任务交给线程池后立即返回，不阻塞调用方；lambda `() -> write(record)` 匹配 `Runnable`（无参、无返回值），`write` 返回 void 且内部自行 catch，因此合法。线程池线程执行 `write` 时从连接池另取一条连接做单条 `INSERT`，没有事务包裹，这就是失败记录得以存活的实际原因。

线程池本身单独建一个 `@Bean`，而不是用 `@Async` 默认执行器——因为日志这种「可以丢但不该拖累别人」的任务若混进共用池，库一慢就把共用队列占满，反过来影响别的异步任务。规格上 core 1 / max 2 / 有界队列 512，拒绝策略是「丢弃 + ERROR」而非退回请求线程（`CallerRunsPolicy` 会让用户请求替日志多等一次数据库往返，方向反了）：

```java
@Bean(OpLogExecutorConfig.EXECUTOR_BEAN_NAME)
public ThreadPoolTaskExecutor opLogExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(512);
    executor.setThreadNamePrefix("op-log-");
    executor.setRejectedExecutionHandler(new OpLogRejectedHandler());
    executor.setWaitForTasksToCompleteOnShutdown(true);  // 关停时把队列里日志写完再退
    executor.setAwaitTerminationSeconds(10);
    return executor;
}
```

写器用 `@Qualifier("opLogExecutor")` 按名字取这个池，避免和未来别的池混淆。一个连带细节：这是项目第一个 `Executor` Bean，会使 Spring 默认的 `applicationTaskExecutor` 退让——当前项目没有 `@Async`，影响面为零，但将来若用 `@Async` 必须显式指定执行器。

### 变更内容脱敏：Jackson 树模型

`after_value` 存的是入参 JSON 快照，必须先脱敏再落库。脱敏走 Jackson 的**树模型**（`JsonNode`）而非正则改文本，是因为树能区分「键名」和「值」，正则会连键名一起改坏，且能逐层下钻覆盖嵌套结构：

```java
public String toJson(Object value) {
    try {
        JsonNode masked = mask(objectMapper.valueToTree(value)); // 对象 → 树
        return truncate(objectMapper.writeValueAsString(masked));
    } catch (Exception e) {
        return null;   // 序列化失败就记不到内容，绝不抛异常影响主流程
    }
}

private JsonNode mask(JsonNode node) {
    if (node instanceof ObjectNode obj) {
        List<String> names = new ArrayList<>();
        obj.fieldNames().forEachRemaining(names::add);   // 先收集键，避免边遍历边改
        for (String name : names) {
            if (isSensitiveKey(name)) obj.put(name, "***");
            else obj.set(name, mask(obj.get(name)));      // 递归下钻
        }
        return obj;
    }
    if (node instanceof ArrayNode arr) { /* 逐元素递归 */ }
    if (node instanceof TextNode t) { /* 手机号/邮箱按位替换 */ }
    return node;
}
```

要点：先收集键名再改，是因为直接在 `fieldNames()` 迭代里 `put` 会改动正在遍历的 map；脱敏命中「键名小写子串包含 password/token/phone/...」时整值置 `***`，同时对自由文本里的手机号、邮箱再按位替换，覆盖「键名看不出、但值里带了隐私」的情况。任何一步失败返回 null，因为「记不到内容」远好于「记错内容」或「带崩主流程」。

## 小结

这套设施的思路可以收束为一句话：**在请求线程上用 AspectJ 拦截写接口、用 SpEL 把业务 id 求出来、把所有上下文抓成不可变快照，再交给一个独立线程池异步落库，落库时机按成败与有无活动事务分流，内容先脱敏**。涉及的 API 全落在四个包里——AspectJ（`@Aspect`/`@Around`/`ProceedingJoinPoint`）、Spring 表达式（`SpelExpressionParser`/`MethodBasedEvaluationContext`/`ParameterNameDiscoverer`）、Spring 事务与并发（`TransactionSynchronizationManager`/`ThreadPoolTaskExecutor`）、Jackson 树模型（`JsonNode`）。读侧查询只是常规 `selectPage` + 条件拼装，不在本笔记重点之内。

---

*初稿说明：本笔记聚焦思路与 API 语法，刻意未展开的部分——读侧 `OperationLogService` 的查询细节、`OpLogMasker` 的正则细节、各 Controller 的具体 `@OpLog` 标注清单——待你通读后提出看不懂的细节再补全。*
