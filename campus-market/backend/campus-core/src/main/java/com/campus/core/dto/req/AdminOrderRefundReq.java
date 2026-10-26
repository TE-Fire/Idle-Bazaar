package com.campus.core.dto.req;

import lombok.Data;

/**
 * 管理端强制退款入参（`/admin/order/{id}/refund`）。
 *
 * <p>`note` 选填但强烈建议填：管理端代卖家处置属于越权动作，落 `cancel_reason` 后
 * 买卖双方在订单详情里能看到「为什么被退」，这也是申诉复盘的唯一依据。
 */
@Data
public class AdminOrderRefundReq {

    private String note;
}
