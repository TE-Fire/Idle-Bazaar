package com.campus.core.dto.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生端发起订单纠纷。
 *
 * <p>纠纷与申诉单在同一事务里落库（{@code OrderService#openDispute}）：订单转纠纷中(8)
 * 冻结，同时写一条 biz_type=3 的待复审申诉单——管理端审核队列按 appeal 取件，
 * 只冻订单不写单的话，纠纷在队列里不存在，谁也裁不了（notes/13 O12）。
 */
@Data
public class OrderDisputeReq {

    /** 纠纷理由，可省；不传按默认文案入申诉单 */
    @Size(max = 500)
    private String reason;
}
