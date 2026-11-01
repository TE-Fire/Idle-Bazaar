package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学生端下单。
 *
 * <p><b>不含 buyerId</b>：下单人取自登录态，客户端传什么都无效。
 *
 * <p><b>金额不接受客户端指定</b>，只有两条来源：默认按商品标价；传了 `offerId` 则按那笔议价的出价，
 * 且必须已由卖家同意、未过 10 分钟付款截止。客户端无法通过改请求把任意商品变成任意价格。
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

    /** 议价单 id：从聊天议价卡片点「去付款」时传入，金额按议价价 */
    private Long offerId;

    private Long meetPointId;

    /** 送货上门时买家选定的收货地址 id（user_address.kind=SHIP） */
    private Long addressId;

    /** 约定面交时间 */
    private LocalDateTime meetTime;
}
