package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 强制下架入参（管理端 `/admin/item/{id}/offline`）。
 *
 * <p>`reason` 必填：处置依据要能复盘，卖家申诉时要看得见「为什么被下架」
 * （business/03 F-A2 判据：处置动作必须留痕——谁、何时、依据、结果）。
 * 预设六项见前端 `OFFLINE_REASONS`，后端不校验取值是否在这六项里——
 * 加一项理由要发一次后端，不如让前端下拉与后端文案都由同一份字典下发时再收口。
 *
 * <p>`note` 是补充的具体依据，选填。落到 `item_audit.remark`，与 `reason` 分开存：
 * 结构化原因用于统计，自由文本用于人工复盘。
 */
@Data
public class ItemOfflineReq {

    @NotBlank(message = "请选择下架原因")
    private String reason;

    private String note;
}
