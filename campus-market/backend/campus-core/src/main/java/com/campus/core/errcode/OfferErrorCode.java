package com.campus.core.errcode;

import com.campus.common.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 议价模块错误码（段位 70xx，登记于 tech/01 5.5）。
 *
 * <p>独立于订单段位 20xx：议价发生在下单之前，失败原因与订单状态无关，
 * 混在一起会让前端「议价失败」和「订单失败」共用一套提示，文案没法写准。
 */
@Getter
@AllArgsConstructor
public enum OfferErrorCode implements IErrorCode {

    OFFER_NOT_FOUND(7001, "议价单不存在"),
    /** 卖家没勾「可议价」的商品不出价：标签是卖家给的许可，不是买家单方面能发起的动作 */
    OFFER_NOT_NEGOTIABLE(7002, "该商品不接受议价"),
    /** 出价须低于标价，且不低于标价的 10%——下限拦的是「1 元试探」这类骚扰性出价 */
    OFFER_PRICE_INVALID(7003, "出价应低于标价，且不低于标价的 10%"),
    OFFER_DUPLICATE(7004, "你对该商品已有进行中的议价，等卖家答复即可"),
    OFFER_STATUS_INVALID(7005, "议价单当前状态不允许该操作"),
    OFFER_NOT_OWNER(7006, "无权操作他人的议价"),
    /** 卖家同意后 10 分钟内未付款 */
    OFFER_EXPIRED(7007, "议价已过付款时限，请重新发起"),
    /** 仅卖家可答复；仅买家可撤回 */
    OFFER_ONLY_SELLER(7008, "只有卖家可以答复议价"),
    OFFER_ONLY_BUYER(7009, "只有买家可以撤回议价"),
    ;

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    private final int code;
    private final String msg;
}
