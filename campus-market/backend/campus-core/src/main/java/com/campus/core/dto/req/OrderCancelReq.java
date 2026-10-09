package com.campus.core.dto.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 取消订单 / 发起退款（同一入口，语义按付款与否分流，见 `OrderService#cancel`）。
 */
@Data
public class OrderCancelReq {

    /** 取消原因，写进 cancel_reason 作为违约判定依据；可空 */
    @Size(max = 255)
    private String reason;
}
