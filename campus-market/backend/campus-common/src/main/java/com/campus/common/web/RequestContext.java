package com.campus.common.web;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 一次请求的上下文（tech/01 §5.4 访问日志、§8.3 操作日志的取数来源）。
 *
 * <p>字段是按「操作日志要落库什么」反推出来的：IP、UA、traceId、耗时在
 * tech/01 §8.3 里被明确列为落库字段，所以这里必须取得到，不能只留一个 uri。
 *
 * <p><b>只放请求级信息，不放登录态</b>：本类在 Filter 里创建，而 Filter 早于
 * Sa-Token 的拦截器执行，此时 token 还没解析，拿不到登录人。登录人走
 * {@code AdminContext}（core 层，懒加载回退 sa-token）。
 *
 * <p>为什么带一个 attributes 袋子：上层（如 AdminContext）要给请求挂数据时，
 * 不希望再起一个 ThreadLocal——两个 ThreadLocal 意味着两个清理点，漏一个就是串号。
 * 袋子由 {@link RequestContextHolder#clear()} 单点清空，代价是弱类型，
 * 所以袋子只给封装类（AdminContext）用，业务代码不要直接读写。
 */
@Getter
public class RequestContext {

    private final String traceId;

    private final String method;

    /** 不含 query 的路径，如 /admin/items */
    private final String uri;

    /** query 字符串（不带 ?），无参数时为 null；敏感参数不要放在 query 上 */
    private final String query;

    /** 客户端 IP：有 X-Forwarded-For 时取首段 */
    private final String clientIp;

    private final String userAgent;

    /** 请求开始时刻（纳秒）：只用 nanoTime 算耗时，不用它算时间 */
    private final long startNano;

    private final Map<String, Object> attributes = new HashMap<>();

    public RequestContext(String traceId,
                          String method,
                          String uri,
                          String query,
                          String clientIp,
                          String userAgent,
                          long startNano) {
        this.traceId = traceId;
        this.method = method;
        this.uri = uri;
        this.query = query;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.startNano = startNano;
    }

    /** 耗时（毫秒），在 finally 里调用 */
    public long costMs() {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    /** 带 query 的完整路径，日志用 */
    public String pathWithQuery() {
        return query == null ? uri : uri + "?" + query;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}
