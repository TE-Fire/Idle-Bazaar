package com.campus.core.oplog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志埋点（tech/01 §5.4）：把**有业务语义**的写动作异步落进 `sys_operation_log`。
 *
 * <p><b>挂在哪</b>：管理端 Controller 的写方法上，与 `@SaCheckPermission` 成对出现。
 * tech/03 CR 清单第 11b 条：「管理端带 `@SaCheckPermission` 的写接口有没有同时挂 `@OpLog`」。
 * 挂在 Controller 而不是 Service，是因为一个 Service 方法常被两个入口复用
 * （如 `ItemAdminService#forceOffline` 同时服务商品页与审核队列），
 * 挂 Service 会丢掉「从哪个入口来的」，而权限点与入口是一一对应的。
 *
 * <p><b>权限拒绝记不到</b>：`@SaCheckPermission` 由 `SaInterceptor` 在 `preHandle` 里校验，
 * 早于任何方法级切面。所以「谁试图越权」这条线索**不在**本表里，得看访问日志与 Sa-Token 的
 * 异常日志（`GlobalExceptionHandler` 已按 1002 记录）。本表记的是**过了鉴权之后**的成败。
 *
 * <p><b>事务提交之后再写</b>：切面用 `TransactionSynchronizationManager` 注册 `afterCommit`；
 * 事务回滚则整条日志丢弃——业务没发生，日志就不该有。挂在非事务方法上（Controller 就是）
 * 时事务上下文为空，直接提交，此时 Service 的事务已经提交完毕，语义同样正确。
 *
 * <p><b>失败也记</b>：方法抛异常时走 `result = 0` 落库，错误码取 `BizException#getCode()`，
 * 非业务异常取异常类名。否则「谁反复失败」这条线索就断了。
 *
 * <p><b>异步</b>：落库在 `opLogExecutor` 线程池里做（有界队列，队列满则丢弃并打 ERROR），
 * 不占请求线程。因此切面必须在**请求线程**上把所有要落库的值抓成快照——
 * `AdminContext` 与 `RequestContextHolder` 在异步线程里都取不到。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OpLog {

    /** 模块：`item` / `review` / `content` / `system`，对应前端日志页的模块筛选下拉 */
    String module();

    /** 操作类型：`APPROVE` / `REJECT` / `FORCE_OFF` / `CATEGORY_UPDATE` …… 同模块内不重复 */
    String type();

    /**
     * 业务对象 id 的 SpEL 表达式，取方法形参，如 `#id` / `#auditId`。留空则不记 bizId。
     *
     * <p>形参名依赖编译期 `-parameters`（父 pom 的 maven-compiler-plugin 已开启）——
     * 关掉它表达式会解析失败，表现为 bizId 静默为 null（切面只打 warn 不抛异常）。
     */
    String bizId() default "";

    /** 操作描述，写给人看的一句话，如「强制下架」。不做插值 */
    String desc() default "";

    /**
     * 是否记录操作的变更内容（落 `after_value`）。
     *
     * <p>**采集口径**：入参的脱敏 JSON 快照。切面不做回查，所以拿不到「变更前」的旧值——
     * `before_value` 一期留空。审核、下架这类需要完整复盘的场景，
     * 业务侧本来就有更准的留痕（`item_audit` 记处置人、原因、首审耗时，
     * 见各 Controller 类注释），`sys_operation_log` 在这一层是跨模块的索引，不是唯一证据。
     * 真要补 before 值，得在业务侧读旧值时显式落，属于单独一件事，不在本注解范围内。
     */
    boolean saveDiff() default false;
}
