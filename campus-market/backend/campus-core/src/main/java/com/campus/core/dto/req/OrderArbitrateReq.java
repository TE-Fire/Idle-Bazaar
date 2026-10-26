package com.campus.core.dto.req;

import lombok.Data;

/**
 * 订单纠纷裁决入参（`/admin/order/{id}/arbitrate`）。
 *
 * <p>**只两种结果**（business/03 F-A6 本期口径）：库里订单终态只有已退款 / 已完成，
 * 没有「部分退款」状态，学生端也没有对应流程，收了这个参数也落不了库。
 *
 * <p>`result` 用语义化取值而不是申诉表的 2/3：那套码是复审视角（维持/撤销），
 * 搬到订单上是「维持纠纷 = 卖家赢」，读起来正好反直觉。Service 内部再映射。
 */
@Data
public class OrderArbitrateReq {

    /** REFUND_BUYER（退款买家） / PAY_SELLER（放款卖家） */
    private String result;

    private String note;
}
