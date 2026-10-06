package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 申诉裁决入参（`/admin/review/appeal/{appealId}/judge`）。
 *
 * <p>`result`：2 维持 / 3 撤销，与 `appeal.status` 的终态取值一致（不做 1 —— 那是待复审）。
 * 「维持」= 原处置有效、商品不动；「撤销」= 原处置作废、商品按原处置反向恢复。
 *
 * <p>`note` 是复审结论，落到 `appeal.handle_note`，卖家在 C 端能看到。
 */
@Data
public class AppealJudgeReq {

    /** 2 维持 3 撤销 */
    @NotNull(message = "请选择复审结论")
    private Integer result;

    private String note;
}
