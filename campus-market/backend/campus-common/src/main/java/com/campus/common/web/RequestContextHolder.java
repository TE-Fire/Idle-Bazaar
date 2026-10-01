package com.campus.common.web;

/**
 * {@link RequestContext} 的线程级载体（tech/01 §5.4）。
 *
 * <p>用 ThreadLocal 而不是把 request 一路传参：MyBatis-Plus 的 MetaObjectHandler、
 * 操作日志埋点这些位置拿不到 HttpServletRequest，又没有入参可传，只能从线程上取。
 * 这是本项目里 ThreadLocal 唯一绕不开的理由。
 *
 * <p><b>唯一清理点在这里</b>（{@link #clear()}），由 {@link AccessLogFilter} 的
 * finally 调用。清理用 {@code remove()} 而不是 {@code set(null)}：Tomcat 线程池复用线程，
 * 不 remove 会让下一个请求读到上一个请求的上下文——这不是内存泄漏级别的小事，
 * 是「A 用户的操作记到 B 用户名下」的安全 bug。
 *
 * <p>异步场景（@Async / 线程池 / MQ 消费者）取不到上下文，get() 返回 null。
 * 本项目当前没有这类入口，约定「异步任务不用上下文」；真要传递时用
 * TransmittableThreadLocal，不要用 InheritableThreadLocal——线程池复用下它拿到的是脏值，
 * 而且看起来还能用，比直接 null 更危险。
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> LOCAL = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    public static void set(RequestContext context) {
        LOCAL.set(context);
    }

    /** 无上下文时返回 null（异步线程、Filter 跳过的路径） */
    public static RequestContext get() {
        return LOCAL.get();
    }

    public static void clear() {
        LOCAL.remove();
    }

    /** 取不到时返回 "-"，避免日志里出现 null 字面量 */
    public static String traceId() {
        RequestContext ctx = LOCAL.get();
        return ctx == null ? "-" : ctx.getTraceId();
    }

    public static String clientIp() {
        RequestContext ctx = LOCAL.get();
        return ctx == null ? "-" : ctx.getClientIp();
    }

    public static String userAgent() {
        RequestContext ctx = LOCAL.get();
        return ctx == null ? "-" : ctx.getUserAgent();
    }
}
