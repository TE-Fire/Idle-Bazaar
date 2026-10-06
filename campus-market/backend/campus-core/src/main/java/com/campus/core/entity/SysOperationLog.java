package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 操作日志（`sys_operation_log`，tech/01 §5.4）。**只追加，不可删改**。
 *
 * <p>与访问日志（`AccessLogFilter`，打文件）分开：本表只记**有业务语义**的写动作，
 * 由 `@OpLog` 切面异步落库，管理端 A9 可查。全量 HTTP 日志是噪音，不进这张表。
 *
 * <p><b>为什么没有 `deleted` / `update_time`</b>：日志只追加，不需要逻辑删除与更新审计。
 * `AuditMetaObjectHandler` 用的是 strictInsertFill，实体没这两个字段就不会硬塞，
 * 所以直接复用全局填充器不需要额外配置。
 *
 * <p><b>为什么 `createTime` 要显式赋值</b>：表上虽然有 `DEFAULT CURRENT_TIMESTAMP`，
 * 但本行是**异步**写的——等线程池排到队再落库，数据库时间就成了「写日志的时刻」而不是
 * 「发生操作的时刻」，排队越久偏差越大。切面在请求线程上取 `LocalDateTime.now()` 放进
 * 快照，这里照抄。同理 `costMs` 是业务方法自身的耗时，不是请求总耗时
 * （后者由 `RequestContext#costMs()` 给访问日志用）。
 *
 * <p><b>`adminName` / `roleCode` 是刻意的冗余</b>（表注释：「避免删号后查不到」）：
 * 管理员被删或被改角色后，历史日志仍要能还原「当时是谁、以什么角色做的」。
 * 所以在写入时刻按 `adminId` 查补一次，不做延迟关联。
 */
@Data
@Accessors(chain = true)
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人 id（切面在请求线程上取，异步线程取不到登录态） */
    private Long adminId;

    /** 操作人姓名（写入时按 adminId 查补的冗余） */
    private String adminName;

    /** 操作时角色码（写入时按 adminId 查补的冗余） */
    private String roleCode;

    /** 模块：item / review / content / system */
    private String module;

    /** 操作类型：APPROVE / REJECT / FORCE_OFF / CATEGORY_UPDATE ... */
    private String opType;

    private Long bizId;

    private String description;

    /** 变更前（脱敏）；需要业务侧回查旧值，一期留空，见 `@OpLog#saveDiff` */
    private String beforeValue;

    /** 变更后（脱敏）：本注解采集的是**入参**的脱敏快照 */
    private String afterValue;

    /** 1 成功 / 0 失败 */
    private Integer result;

    /** 失败错误码（业务码或异常类名），成功时为 null */
    private String errorCode;

    private String ip;

    private String ua;

    /** 与访问日志、响应体 traceId 对齐，便于从一条日志跳到整条链路 */
    private String traceId;

    /** 业务方法耗时（毫秒） */
    private Integer costMs;

    /** 操作发生时刻（请求线程上取，不是落库时刻） */
    private LocalDateTime createTime;
}
