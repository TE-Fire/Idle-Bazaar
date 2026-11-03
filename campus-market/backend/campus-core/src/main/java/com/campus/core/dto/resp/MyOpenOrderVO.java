package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 当前用户针对某商品的未完成订单摘要（notes/13 第七节）。
 *
 * <p>商品详情页用它来引导买家：若存在未完成订单，把「立即购买」切换成「查看我的订单」，
 * 避免重复下单。{@code status} 用 {@code OrderStatusEnum#name()} 的字符串（如 {@code WAIT_PAY}），
 * 前端按自己那套状态文案渲染。
 */
@Data
public class MyOpenOrderVO {

    /** 未完成订单 id */
    private Long orderId;

    /** 订单状态名（OrderStatusEnum.name()），如 WAIT_PAY / WAIT_MEET / WAIT_PICKUP */
    private String status;
}
