package com.campus.core.dto.req;

import lombok.Data;

/**
 * 订单列表查询条件（管理端 `/admin/order/list`）。
 *
 * <p>`keyword` 跨表匹配：订单号与商品标题在 `trade_order`（快照列），买卖双方昵称在 `app_user`——
 * 先按昵称查出用户 id 集合，再拼进主表的 IN 条件，不写跨表 JOIN（分页 count 会算错，见 notes/08）。
 *
 * <p>`status` 传枚举名（WAIT_PAY 等），与前端 `OrderStatus` 同形。
 */
@Data
public class AdminOrderQueryReq {

    /** 订单号 / 商品快照标题 / 买家或卖家昵称 */
    private String keyword;

    /** 订单状态枚举名；空 = 全部 */
    private String status;

    /** 只看纠纷：等价于 status=DISPUTING，独立开关是因为它是管理端最常用的入口 */
    private Boolean disputeOnly;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
