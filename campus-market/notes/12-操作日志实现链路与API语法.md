# 操作日志实现链路与 API 语法

本笔记记录操作日志功能从「写动作埋点」到「异步落库」再到「管理端查询」的完整链路，重点不在业务字段含义，而在**实现它需要的思路与 API 语法**。读侧（A9 查询页）只做只读查询，逻辑简单，本文略去细讲；本文的主角是写侧那一套 `@OpLog` 基础设施——它全部由 Spring / AspectJ 内置能力拼装，不依赖任何第三方框架。先建立两条贯穿全篇的判断：**落库必须异步，且异步之前必须在请求线程上把所有要记的东西抓成不可变快照**；**日志失败绝不能影响业务结果**。这两条决定了后面每一个类为什么存在、为什么长那样。

## 整体思路：一条写入链路分三段

一个写接口加上 `@OpLog` 注解后，会触发一条固定的处理流。`OpLogAspect` 是入口，它在**请求线程**上完成所有「上下文抓取」：取登录管理员、把入参序列化脱敏、用 SpEL 把 `#auditId` 这样的表达式求成真实业务 id、量方法耗时、判成功失败。抓完之后把这些值装进一个 `OpLogRecord` 不可变对象，交给 `OpLogWriter`。`OpLogWriter` 决定「此刻提交还是等事务提交后再提交」，然后把任务丢进 `opLogExecutor` 线程池，由池里的线程真正执行 `INSERT`。

把链路拆成切面与写器两段，是因为最容易出错的两件事分属不同线程：`OpLogAspect` 里的上下文抓取必须在请求线程做（登录态、IP、UA 都是 ThreadLocal，离开请求线程就取不到），而落库在另一个线程。一旦把这两段混在一个类里，改一处就可能误伤另一处，所以切面只负责「抓」，写器只负责「写」。

异步落库带来一个硬约束：线程池线程里读不到任何请求上下文。这就是 `OpLogRecord` 存在的根本原因——它是一份在请求线程上抓好、之后绝不再改的快照（用 Java 的 `record` 类型表达「不可变」语义）。切面把 adminId、module、opType、bizId、描述、脱敏后的变更内容、成败、错误码、耗时、客户端 IP、UA、traceId 全部塞进它，写器那边只读这份快照，不碰任何上下文。

切面挂在 Controller 的写方法上，而不是 Service。原因在于一个 Service 方法常被两个入口复用（例如 `ItemAdminService#forceOffline` 同时服务于商品页和审核队列），挂 Service 会丢掉「从哪个入口来」这一信息；而权限点与入口一一对应，挂在 Controller 才能既记到入口来源、又和 `@SaCheckPermission` 成对出现。一个连带结论是：权限被拒的访问记不进本表——`@SaCheckPermission` 由 `SaInterceptor` 在 `preHandle` 早于方法切面校验，越权请求根本进不到切面，这条线索只能从访问日志与异常日志反查。

落库的时机要分成功与失败两条路，且两条路刻意不对称。**成功时若当前有活动事务，注册 `afterCommit`，等事务真提交才写**：万一外层把事务回滚了，「从没发生过的操作」就不该留在日志里。**失败时立即提交、不挂 afterCommit**：失败的事务必然回滚，挂上去反而会把「这次失败」一起抹掉，而我们要的恰恰是「谁反复失败」这条线索。两种挂法下语义都成立：注解挂在 Controller（当前用法）时事务早已提交，两条分支等价；挂在 `@Transactional` 的 Service 方法上时两条分支各自正确。

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

写器先把「成功且处于活动事务」的情况注册成 `afterCommit`，其余立即提交，再统一 `execute` 进线程池：

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
        opLogExecutor.execute(() -> write(record));   // 后台线程落库
    } catch (Exception e) {
        log.error("[oplog] 提交异步落库失败，本条丢弃 ...");  // 拒绝策略触发时此处捕获
    }
}
```

`TransactionSynchronizationManager` 是 Spring 提供的「当前线程事务状态」访问器。`isSynchronizationActive()` 判断当前线程有没有活动事务同步，`registerSynchronization` 注册一个回调，事务提交后才触发。这两个 API 是「日志随事务成败」能力的关键。

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

这套设施的思路可以收束为一句话：**在请求线程上用 AspectJ 拦截写接口、用 SpEL 把业务 id 求出来、把所有上下文抓成不可变快照，再交给一个独立线程池异步落库，落库时机跟随事务成败，内容先脱敏**。涉及的 API 全落在四个包里——AspectJ（`@Aspect`/`@Around`/`ProceedingJoinPoint`）、Spring 表达式（`SpelExpressionParser`/`MethodBasedEvaluationContext`/`ParameterNameDiscoverer`）、Spring 事务与并发（`TransactionSynchronizationManager`/`ThreadPoolTaskExecutor`）、Jackson 树模型（`JsonNode`）。读侧查询只是常规 `selectPage` + 条件拼装，不在本笔记重点之内。

---

*初稿说明：本笔记聚焦思路与 API 语法，刻意未展开的部分——读侧 `OperationLogService` 的查询细节、`OpLogMasker` 的正则细节、各 Controller 的具体 `@OpLog` 标注清单——待你通读后提出看不懂的细节再补全。*
