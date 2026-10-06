package com.campus.core.oplog;

import java.time.LocalDateTime;

/**
 * 一次操作日志落库所需的全部内容——**在请求线程上抓好的不可变快照**。
 *
 * <p>为什么要有这个中间对象：落库是异步的（`opLogExecutor`），而
 * `AdminContext`（依赖 Sa-Token 的 request）与 `RequestContextHolder`（ThreadLocal）
 * 在异步线程里都取不到。切面必须先把 ip / ua / traceId / 登录人 / 耗时 /
 * 变更内容全部取出来放进来，再把这个记录交给线程池——异步线程只读它，不碰上下文。
 *
 * <p>用 record 而不是普通类：字段一旦抓取就不该再改，record 的 final 语义正好，
 * 也省掉一份 getter/setter。字段名与 `SysOperationLog` 一一对应，
 * 由 {@link OpLogWriter} 负责搬运。
 *
 * @param adminId     操作人 id（null = 没取到登录态，整条丢弃，见切面）
 * @param module      `@OpLog#module()`
 * @param opType      `@OpLog#type()`
 * @param bizId       `@OpLog#bizId()` 求值结果，可为 null
 * @param description `@OpLog#desc()`
 * @param afterValue  入参脱敏 JSON，`saveDiff = false` 或序列化失败时为 null
 * @param success     方法是否正常返回
 * @param errorCode   失败错误码（可能被截断到 32 字符），成功时为 null
 * @param costMs      业务方法自身耗时
 * @param occurredAt  操作时刻（请求线程上取，不是落库时刻）
 * @param ip          客户端 IP，无请求上下文时为 null
 * @param ua          User-Agent，无请求上下文时为 null
 * @param traceId     链路 id，无请求上下文时为 null
 */
public record OpLogRecord(
        Long adminId,
        String module,
        String opType,
        Long bizId,
        String description,
        String afterValue,
        boolean success,
        String errorCode,
        int costMs,
        LocalDateTime occurredAt,
        String ip,
        String ua,
        String traceId) {
}
