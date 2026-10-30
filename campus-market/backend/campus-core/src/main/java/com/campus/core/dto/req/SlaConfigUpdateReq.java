package com.campus.core.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * SLA 阈值全量写入（`PUT /admin/settings/sla`）。
 *
 * <p>**全量六键**而不是部分更新：六个值是给报表算达标率用的一套口径，只改一半会出现
 * 「首审放宽到 4 小时、P95 还是 6 小时」这种自相矛盾的组合，不如让前端整表单提交。
 *
 * <p>上下界取的是「再离谱也还能解释」的范围：小时数 ≤ 168（一周）、达标率 0–100、
 * 单条处置 ≤ 600 秒。超出即参数错误，不静默夹取。
 */
@Data
public class SlaConfigUpdateReq {

    @NotNull(message = "请填写首审响应小时数")
    @Min(value = 1, message = "首审响应至少 1 小时")
    @Max(value = 168, message = "首审响应不超过 168 小时")
    private Integer firstResponseHours;

    @NotNull(message = "请填写 P95 小时数")
    @Min(value = 1, message = "P95 至少 1 小时")
    @Max(value = 168, message = "P95 不超过 168 小时")
    private Integer p95Hours;

    @NotNull(message = "请填写达标率下限")
    @Min(value = 1, message = "达标率至少 1%")
    @Max(value = 100, message = "达标率不超过 100%")
    private Integer targetRate;

    @NotNull(message = "请填写积压红牌小时数")
    @Min(value = 1, message = "积压阈值至少 1 小时")
    @Max(value = 168, message = "积压阈值不超过 168 小时")
    private Integer backlogHours;

    @NotNull(message = "请填写单条平均处置秒数")
    @Min(value = 1, message = "单条处置至少 1 秒")
    @Max(value = 600, message = "单条处置不超过 600 秒")
    private Integer avgHandleSeconds;

    @NotNull(message = "请填写申诉复审小时数")
    @Min(value = 1, message = "申诉复审至少 1 小时")
    @Max(value = 168, message = "申诉复审不超过 168 小时")
    private Integer appealHours;
}
