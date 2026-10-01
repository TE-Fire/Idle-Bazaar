# 06 - OncePerRequestFilter：访问日志是怎么打出来的

对应代码：`campus-common/src/main/java/com/campus/common/web/{AccessLogFilter, RequestContext, RequestContextHolder}.java`、
`campus-admin-api/src/main/resources/{application.yml, logback-spring.xml}`、
`campus-common/src/main/java/com/campus/common/result/Result.java`（消费 MDC）。
版本：Spring Boot **3.5.7**（servlet 包名是 `jakarta.servlet.*`，不是 `javax.servlet.*`）。

写这篇的起因：翻代码问「日志是在哪打印的」——**找不到是正常的**，因为本项目里访问日志
没有任何一行写在 Controller 或 Service 里，也没有任何"注册 Filter"的代码。
下面第一节就回答这个疑问，后面再展开 `OncePerRequestFilter` 的用法。

---

## 零、为什么在代码里找不到打印日志的地方

三个原因叠在一起，所以搜 `log.info` 搜不到你想看的那行：

| 原因 | 说明 |
|---|---|
| ① 打印方法叫 `logAccess()`，不是散落在业务里的 `log.info` | 唯一打印点是 `AccessLogFilter` 的私有方法（第 106 行），由 `finally` 调用 |
| ② **Filter 是容器回调的，代码里没有任何"注册 / 调用"语句** | 只靠类上的 `@Component`；Spring Boot 启动时自动把它装进 FilterChain，没有任何配置类提到它 |
| ③ 日志**格式**不在 Java 里 | 前半段（时间 / 线程 / traceId / 级别 / 类名）由 `application.yml` 的 `logging.pattern.console` 与 `logback-spring.xml` 的 `FILE_PATTERN` 决定，Java 只输出 `%msg` 那一段 |

要定位就按这个清单看：

| 想看什么 | 去哪看 |
|---|---|
| 打印哪一行、什么级别 | `AccessLogFilter#logAccess`（第 106–122 行） |
| 什么时机打印 | `AccessLogFilter#doFilterInternal` 的 `finally`（第 95–99 行） |
| 耗时怎么算出来的 | `RequestContext#costMs()`（`System.nanoTime()` 差值） |
| 日志前半段的格式 | `application.yml:73`（控制台）、`logback-spring.xml` 的 `FILE_PATTERN`（文件） |
| filter 什么时候被装进容器 | 没有代码——`@Component` + `@Order` 即可，见第二节 |

实测的一行日志（`logs/campus-market.log`）：

```
2026-10-01 13:08:52.026 [http-nio-8080-exec-6] [36ae18ac35934b29] INFO  com.campus.common.web.AccessLogFilter - GET /admin/auth/perm-codes 200 20ms ip=0:0:0:0:0:0:0:1
```

拆开看：`[36ae18ac35934b29]` 来自 pattern 里的 `%X{traceId}`（MDC），
`GET /admin/auth/perm-codes 200 20ms ip=...` 才是 `logAccess()` 输出的 `%msg`。
所以 **traceId 不用在 logAccess 里重复打**——打了就会出现两次。

---

## 一、一次请求究竟经过哪些环节

```
Tomcat
  └─ FilterChain
       └─ AccessLogFilter          ← ① 建上下文、记起始时间（此时还不知道登录人）
            └─ DispatcherServlet
                 └─ SaInterceptor  ← ② 这里才解析 token，才拿得到 loginId
                      └─ Controller
                           └─ Service / Mapper
                  ← 返回
       ← finally：打印日志、清 MDC、清 ThreadLocal
```

| 顺序 | 执行者 | 能拿到什么 | 拿不到什么 |
|---|---|---|---|
| ① | Filter | request / response 原对象、header、URI | **登录人**（token 还没解析）、Handler 方法信息 |
| ② | Interceptor | 已解析的登录态、目标 Handler | 404 请求根本不进来 |
| ③ | Controller | 全部 | — |

这张表解释了两件事：为什么访问日志必须放 Filter（404 和鉴权失败也要留痕），
以及为什么**登录人不能放 Filter**（`AdminContext` 只能懒加载，见 `notes/05` 之外的基础设施注释）。

---

## 二、OncePerRequestFilter 是什么

### 2.1 继承链

```
jakarta.servlet.Filter          （接口：init / doFilter / destroy）
  └─ org.springframework.web.filter.GenericFilterBean   （init 参数、BeanNameAware、生命周期）
       └─ OncePerRequestFilter  （保证一次请求只执行一次 + 提供模板方法）
            └─ AccessLogFilter  （本项目）
```

直接实现 `Filter` 也可以，但要自己写 `init/destroy`、自己处理"一次请求被调多次"的问题。

### 2.2 它解决的核心问题：一次请求可能被 Filter 链调用多次

Servlet 规范里，`REQUEST` 分发之外还有 `FORWARD`、`INCLUDE`、`ERROR`、`ASYNC` 四种分发方式。
典型场景：Controller 抛异常 → 容器转发到 `/error` → Filter **又跑一遍**；
或 JSP/Servlet forward 到另一个地址 → 再跑一遍。
结果就是同一条请求打两行日志、traceId 被覆盖第二次。

`OncePerRequestFilter` 的做法（简化后的源码逻辑）：

```java
String alreadyFilteredAttributeName = getAlreadyFilteredAttributeName();  // 类名 + ".FILTERED"
if (request.getAttribute(alreadyFilteredAttributeName) != null || skipDispatch(request)) {
    doFilterNestedErrorDispatch / 直接放过;   // 已经跑过 → 不再执行
} else {
    request.setAttribute(alreadyFilteredAttributeName, Boolean.TRUE);     // 打标记
    try {
        doFilterInternal(request, response, filterChain);                 // ← 我们实现的模板方法
    } finally {
        request.removeAttribute(alreadyFilteredAttributeName);            // 收尾撤标记
    }
}
```

要点：
- 标记存在 **request 属性**上，所以只对同一次请求生效，天然线程安全。
- 默认只拦 `REQUEST` 分发（`shouldNotFilterErrorDispatch()` 默认 true → 错误分发不再执行）。
- 因此**它不能保证"一个请求只执行一次"在所有分发类型下都成立**，但覆盖了最容易踩的 forward / error 场景。

### 2.3 模板方法

`OncePerRequestFilter` 是抽象类，唯一必须实现的是：

```java
protected abstract void doFilterInternal(HttpServletRequest request,
                                         HttpServletResponse response,
                                         FilterChain filterChain) throws ServletException, IOException;
```

可选重写：

| 方法 | 用途 |
|---|---|
| `shouldNotFilter(HttpServletRequest)` | 返回 true 则整个 Filter 跳过（本项目用它排掉 Knife4j 文档路径） |
| `getAlreadyFilteredAttributeName()` | 自定义标记名（一般不动） |
| `shouldNotFilterAsyncDispatch()` | 是否跳过 ASYNC 分发（默认 true） |
| `shouldNotFilterErrorDispatch()` | 是否跳过 ERROR 分发（默认 true） |

### 2.4 怎么注册（本项目为什么没有注册代码）

```java
@Slf4j
@Component                          // ① 被扫成 Bean
@Order(Ordered.HIGHEST_PRECEDENCE)  // ② 排在所有 Filter 最前面
public class AccessLogFilter extends OncePerRequestFilter { ... }
```

Spring Boot 会自动把容器里所有 `Filter` 类型的 Bean **注册到 FilterChain**，
默认 URL 映射是 `/*`。所以不需要 `FilterRegistrationBean`，也不需要 `web.xml`。
之所以能被扫到，是因为启动类写的是 `@SpringBootApplication(scanBasePackages = "com.campus")`——
**如果哪天把扫描范围收窄到 `com.campus.admin`，这个 Filter 会静默失效**（日志没了，但不报错）。

需要"精确控制 URL 顺序 / 指定 dispatcher 类型"时才用 `FilterRegistrationBean`：

```java
@Bean
public FilterRegistrationBean<AccessLogFilter> accessLog(AccessLogFilter f) {
    FilterRegistrationBean<AccessLogFilter> bean = new FilterRegistrationBean<>(f);
    bean.addUrlPatterns("/admin/*", "/api/*");
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
}
```

### 2.5 Filter 与 HandlerInterceptor 怎么选

| 维度 | Filter（Servlet 层） | HandlerInterceptor（Spring MVC 层） |
|---|---|---|
| 生效范围 | 所有请求，**含 404 / 未匹配 Handler 的** | 只拦匹配到 Handler 的请求 |
| 拿到的对象 | 原始 `HttpServletRequest` | 同上 + `HandlerMethod`（能读注解） |
| 典型用途 | 访问日志、traceId、跨域、编码、XSS 过滤 | 登录态、权限点、接口耗时埋点 |
| 异常可见性 | 能捕获 Filter 链里的 Throwable | 看不到 Filter 层抛的异常 |
| 依赖注入 | 能注入 Bean，但**不要**在此阶段查库 | 可以 |

本项目两边都有：`AccessLogFilter`（日志 / 上下文）+ `SaInterceptor`（登录态，在 `AdminWebConfig`）。
不是重复，是不同层。

---

## 三、本项目的实现逐段拆解

### 3.1 跳过不该记的路径

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String uri = request.getRequestURI();
    for (String prefix : SKIP_PREFIXES) {   // /doc.html、/webjars/、/v3/api-docs、/favicon.ico、/actuator/
        if (uri.startsWith(prefix)) return true;
    }
    return false;
}
```

不排掉的话，本地每刷一次 Knife4j 文档页会带出十几个 `webjars` 请求，业务日志瞬间被淹没。
注意 `shouldNotFilter` 返回 true 时 **Filter 整体不执行**，所以这些请求也不会有 traceId 响应头——
它们是文档静态资源，不需要。

### 3.2 建上下文

```java
long startNano = System.nanoTime();
String traceId = resolveTraceId(request);   // 有上游 X-Trace-Id 就沿用，否则生成 16 位
MDC.put(TRACE_ID, traceId);
response.setHeader(HEADER_TRACE_ID, traceId);
RequestContextHolder.set(new RequestContext(traceId, method, uri, query, ip, ua, startNano));
```

- 耗时用 `System.nanoTime()`，不用 `currentTimeMillis()`：后者受系统时钟调整（NTP 校时）影响，可能算出负数。
- `MDC` 底层就是 ThreadLocal，logback 的 `%X{traceId}` 从它取值——这就是 traceId 会出现在日志前半段的原因。

### 3.3 catch(Throwable) 后原样重抛

```java
Throwable failure = null;
try {
    chain.doFilter(request, response);
} catch (Throwable t) {
    failure = t;      // 只记录，不改变异常
    throw t;          // 精确重抛：编译器按 try 块实际可能抛出的类型推导，不改异常类型
} finally {
    logAccess(response.getStatus(), failure);
    MDC.remove(TRACE_ID);
    RequestContextHolder.clear();
}
```

为什么必须 catch 再抛：如果只在"正常返回"后打印，**抛异常的请求一条日志都不会有**，
而 500 恰恰是最需要排查的一批。`failure` 记下来交给 `logAccess` 决定级别，异常本身原样上抛给容器。

### 3.4 打印与级别取舍

```java
if (failure != null)              log.warn("访问异常 ... ex={}", ...);
else if (status >= 500)           log.warn("服务端错误 ...");
else if (cost >= SLOW_MS)         log.warn("慢请求 ...");   // 阈值 1000ms
else                              log.info("{method} {path} {status} {cost}ms ip={}", ...);
```

| 情况 | 级别 | 理由 |
|---|---|---|
| 正常 / 4xx | `info` | 4xx 是客户端的问题，不是系统故障 |
| 抛异常 / 5xx | `warn` | 需要人看，但**不该和磁盘写满同级别**——`error` 留给真正救火的场景 |
| 超过 1s | `warn` | 本地开着 SQL debug 会比线上慢，1s 是"宁可漏报也不刷屏"的折中 |

### 3.5 finally 里的三件事必须一起做

```
logAccess()                    → 打印
MDC.remove(TRACE_ID)           → 否则下一个请求可能带着上一个 traceId
RequestContextHolder.clear()   → ThreadLocal 必须 remove()，不是 set(null)
```

Tomcat 线程池复用线程，不清干净就是 **A 用户的操作记到 B 用户名下**——这是安全 bug 不是小瑕疵。

---

## 四、踩过的坑

| 坑 | 现象 | 处理 |
|---|---|---|
| **类名撞 Spring 内置 Bean** | 类最初叫 `RequestContextFilter`，与 Spring Boot 自动配置的 `org.springframework.web.filter.RequestContextFilter` **bean 名同名** → 启动直接失败 | 改名 `AccessLogFilter`。**不要用 `spring.main.allow-bean-definition-overriding=true` 糊**（会顶掉框架 Bean，Sa-Token 取 request 可能受影响） |
| 包扫描范围 | Filter 在 `com.campus.common`，靠 `scanBasePackages = "com.campus"` 才生效 | 收窄扫描范围会让它静默失效 |
| ThreadLocal 清理 | 见 3.5 | `remove()` |
| 异步线程 | `@Async` / MQ 消费者里 `RequestContextHolder.get()` 是 null | 当前无此类入口，约定不用；需要时用 TransmittableThreadLocal，**别用 InheritableThreadLocal**（线程池下拿到脏值且看起来能用） |
| 打印请求体 | 需要 `ContentCachingRequestWrapper` 包装 + 限大小 + 脱敏 | **本项目不做**：登录接口的密码、验证码就在 body 里，收益小于风险 |

---

## 五、速查：想改什么 → 改哪里

| 需求 | 位置 |
|---|---|
| 加字段（如加 UA、加响应大小） | `RequestContext` 加字段 → `AccessLogFilter` 构造处传值 → `logAccess` 格式串 |
| 改跳过路径 | `AccessLogFilter.SKIP_PREFIXES` |
| 改慢请求阈值 | `AccessLogFilter.SLOW_MS`（当前 1000ms，二期可提成配置项） |
| 改日志前缀格式（时间 / traceId / 类名长度） | `application.yml:73`（控制台）、`logback-spring.xml` 的 `FILE_PATTERN`（文件） |
| 改 traceId 长度 / 生成规则 | `AccessLogFilter#resolveTraceId` |
| 打印请求体 | 引入 `ContentCachingRequestWrapper`，并先做脱敏（见第四节） |
| 让某段代码拿到当前请求信息 | `RequestContextHolder.get()`（可能 null）；登录人用 `AdminContext#getAdminId()` |

## 六、一句话记住

**Filter 是容器回调的，所以它"看起来没人调用"**——打印发生在 `AccessLogFilter` 的 `finally` 里，
格式在 yml / xml 里，注册靠 `@Component`。这三者分开，才导致"代码里找不到日志在哪打"。
