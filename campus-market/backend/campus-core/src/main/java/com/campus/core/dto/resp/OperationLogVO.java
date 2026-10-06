package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志一行（管理端 A9 `/settings/logs`）。
 *
 * <p>字段直接对齐 `sys_operation_log` 的列，**不做中文翻译**：`module` / `opType`
 * 输出英文码，中文标签由前端维护（2026-10-06 决策）。这与 `tech/01` §5.3
 * 「枚举输出 code，不让前端硬编码中文」的取舍方向相反，是有意为之——
 * 操作类型是会随业务持续增加的长尾（每加一个写接口就可能加一个 `type`），
 * 后端每加一个都要改 VO 翻译层，前端加一个标签只是改个映射表。
 * 前端映射表用 `Record<string, string>` + 兜底显示原码，漏配也不会显示成空白。
 *
 * <p>`adminName` / `roleCode` 是**写入时刻的冗余快照**（表注释：「避免删号后查不到」），
 * 所以这里查到的是「当时是谁、以什么角色做的」，不是这两个字段现在的值。
 *
 * <p>`afterValue` / `beforeValue` 在写入前已由 `OpLogMasker` 脱敏，页面直接展示即可；
 * `beforeValue` 一期恒为 null（切面不回查旧值，见 `@OpLog#saveDiff`），
 * 全局 Jackson 配了 `non_null`，所以它在 JSON 里根本不出现——前端判空要写 `!x`。
 */
@Data
public class OperationLogVO {

    private Long id;

    private Long adminId;

    /** 操作人姓名（写入时刻的快照，删号后仍可还原） */
    private String adminName;

    /** 操作时的角色码（写入时刻的快照） */
    private String roleCode;

    /** 模块英文码：item / review / content / system */
    private String module;

    /** 操作类型英文码：APPROVE / REJECT / CATEGORY_UPDATE ... */
    private String opType;

    /** 业务对象 id；仅当主键是数字时才有（成色等业务码主键留空） */
    private Long bizId;

    /** 注解上写的中文说明（如「强制下架」），与 `opType` 互补 */
    private String description;

    private String beforeValue;

    /** 入参的脱敏快照；`saveDiff=false` 的接口为 null */
    private String afterValue;

    /** 1 成功 / 0 失败 */
    private Integer result;

    /** 失败错误码（业务码或异常类名），成功时为 null */
    private String errorCode;

    private String ip;

    private String ua;

    /** 与访问日志、响应体 traceId 对齐，可从一条日志跳到整条链路 */
    private String traceId;

    /** 业务方法耗时（毫秒），不是请求总耗时 */
    private Integer costMs;

    /** 操作发生时刻（不是落库时刻） */
    private LocalDateTime createTime;
}
