package com.campus.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求上下文 + 访问日志（tech/01 §5.4 访问日志，替代原 TraceIdFilter）。
 *
 * <p>三件事合成一个 Filter，而不是拆成 traceId / 访问日志两个：
 * 上下文的 set 与 clear 必须成对出现在同一个 finally 里，拆开就会出现
 * 「谁负责 clear」的歧义（外层清了、内层还在用）。
 *
 * <p><b>为什么是 Filter 而不是 Interceptor</b>：Filter 在 Servlet 层，能覆盖
 * 404、鉴权失败、抛异常的全部路径；Interceptor 只拦匹配到 Handler 的请求，
 * 401 与 Filter 层异常根本不打照面，而 401/500 恰恰是最需要留痕的。
 *
 * <p>打印范围：method / uri+query / status / 耗时 / IP。
 * 不打印请求体与响应体——打印请求体要用 ContentCachingRequestWrapper 包装
 * （限大小 + 脱敏），且登录接口的密码、验证码就在 body 里，收益远小于风险。
 *
 * <p>traceId 已由 logback pattern 的 {@code %X{traceId}} 输出，这里不再重复打，
 * 避免同一行出现两次。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** 慢请求阈值：本地开着 SQL debug 会比线上慢，1s 是宁可漏报也不刷屏的折中 */
    private static final long SLOW_MS = 1000L;

    /**
     * 不打日志的路径：Knife4j 文档与静态资源。
     * 本地每刷一次 /doc.html 会带出十几个 webjars 请求，不排除会把业务日志淹掉。
     */
    private static final String[] SKIP_PREFIXES = {
            "/doc.html",
            "/webjars/",
            "/v3/api-docs",
            "/favicon.ico",
            "/actuator/"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String prefix : SKIP_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long startNano = System.nanoTime();
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);
        RequestContextHolder.set(new RequestContext(
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                resolveClientIp(request),
                request.getHeader("User-Agent"),
                startNano
        ));

        Throwable failure = null;
        try {
            chain.doFilter(request, response);
        } catch (Throwable t) {
            // 记下来后原样抛出（精确重抛，不改异常类型）。只在正常路径打日志，
            // 等于把 500 这一批最该排查的请求全丢了。
            failure = t;
            throw t;
        } finally {
            logAccess(response.getStatus(), failure);
            MDC.remove(TRACE_ID);
            RequestContextHolder.clear();
        }
    }

    /**
     * 访问日志。异常用 warn 不用 error：error 留给真正的系统故障，
     * 而「用户传了个不存在的 id」这类业务失败不该和磁盘写满同级别。
     */
    private void logAccess(int status, Throwable failure) {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null) {
            return;
        }
        long cost = ctx.costMs();
        String path = ctx.pathWithQuery();
        if (failure != null) {
            log.warn("访问异常 {} {} {}ms ip={} ex={}",
                    ctx.getMethod(), path, cost, ctx.getClientIp(), failure.getClass().getSimpleName());
        } else if (status >= 500) {
            log.warn("服务端错误 {} {} {} {}ms ip={}", ctx.getMethod(), path, status, cost, ctx.getClientIp());
        } else if (cost >= SLOW_MS) {
            log.warn("慢请求 {} {} {} {}ms ip={}", ctx.getMethod(), path, status, cost, ctx.getClientIp());
        } else {
            log.info("{} {} {} {}ms ip={}", ctx.getMethod(), path, status, cost, ctx.getClientIp());
        }
    }

    /** 上游传来的 traceId 直接沿用，便于跨服务串联；没有再生成 16 位 */
    private static String resolveTraceId(HttpServletRequest request) {
        String upstream = request.getHeader(HEADER_TRACE_ID);
        if (upstream != null && !upstream.isBlank()) {
            return upstream;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 客户端 IP：有 X-Forwarded-For 时取首段。
     * 注意 XFF 是请求头、可伪造，这里只用于排查与操作日志留痕，
     * 不能当作安全判据（限流 / 风控要走可信代理链，另算）。
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
