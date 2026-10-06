package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 举报裁决入参（`/admin/review/report/{reportId}/handle`）。
 *
 * <p>`result`：1 成立 / 2 不成立，与 `report.result` 同义。用 Integer 而不是布尔——
 * 库里存的就是 1/2，转成布尔再转回去多一层无意义的映射，且将来加「部分成立」时不用改类型。
 *
 * <p>`reason` 只在**成立**时必填（成立要处置商品，处置必填依据）。这个约束是**条件必填**，
 * Bean Validation 表达不了，放在 Service 里判，报 1000 参数错误。
 *
 * <p>不做独立的「受理」步骤（notes/09 问题 4）：裁决一步到位把 `status` 写成 3。
 */
@Data
public class ReportHandleReq {

    /** 1 成立 2 不成立 */
    @NotNull(message = "请选择裁决结论")
    private Integer result;

    /** 成立时的处置依据（与 OFFLINE_REASONS 同源）；不成立时可不填 */
    private String reason;

    /** 处置说明，会通知举报人与被举报人 */
    private String note;
}
