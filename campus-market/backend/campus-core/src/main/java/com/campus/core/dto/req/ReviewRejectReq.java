package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商品审核驳回入参（`/admin/review/item/{auditId}/reject`）。
 *
 * <p>`reason` 必填的理由与 {@link ItemOfflineReq} 一致：驳回要写明依据，
 * 卖家据此修改后重提（business/03 F-A2）。预设六项见前端 `OFFLINE_REASONS`，前后端共用。
 *
 * <p>与 {@link ItemOfflineReq} 字段完全相同但不复用它：两者是**不同的业务动作**
 * （驳回回到「审核未过」、下架进入平台处置终态），各自一个类型能让 Swagger 文档
 * 和前端的请求体语义对得上，代价只是两个字段的重复。
 */
@Data
public class ReviewRejectReq {

    @NotBlank(message = "请选择驳回原因")
    private String reason;

    private String note;
}
