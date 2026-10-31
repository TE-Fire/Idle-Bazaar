package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生端下单。
 *
 * <p><b>不含 buyerId</b>：下单人取自登录态，客户端传什么都无效。
 * 同理也不含金额——`amount` 一律按下单时的商品标价，议价发生在聊天里、不进订单（`notes/13` 实体注释）。
 *
 * <p>面交点与约定时间可空：先下单后约地点是常态，强制填会让一半用户随手选一个。
 *
 * <p>`addressId` 仅送货上门（deliveryType=3）必填：买家从自己的收货地址里挑一个，
 * 自提由卖家在商品上预设、买家不可选，面交不走地址。校验在 Service 按商品 deliveryType 分流。
 */
@Data
public class OrderCreateReq {

    @NotNull
    private Long itemId;

    private Long meetPointId;

    /** 送货上门时买家选定的收货地址 id（user_address.kind=SHIP） */
    private Long addressId;

    /** 约定面交时间 */
    private LocalDateTime meetTime;
}
