package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 卖家处理退款申请。
 *
 * <p>`agree = false` 即拒绝，订单随即冻结转纠纷中，由管理端裁决（`notes/13` 第四节）。
 */
@Data
public class RefundHandleReq {

    @NotNull
    private Boolean agree;

    /** 同意或拒绝的说明，回写 cancel_reason 供复盘 */
    @Size(max = 255)
    private String note;
}
