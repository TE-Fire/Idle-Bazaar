package com.campus.core.errcode;

import com.campus.common.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单模块错误码（段位 20xx，登记于 tech/01 5.5）。
 */
@Getter
@AllArgsConstructor
public enum OrderErrorCode implements IErrorCode {

    ORDER_NOT_FOUND(2001, "订单不存在"),
    ORDER_TIMEOUT(2002, "订单已超时取消"),
    ORDER_STATUS_INVALID(2003, "当前订单状态不允许该操作"),
    ORDER_NOT_OWNER(2004, "无权操作他人订单"),

    /** 占位失败：已被他人买走，或被平台下架 */
    ITEM_SOLD_OUT(2005, "商品已被买走，换个看看吧"),
    ITEM_NOT_ON_SALE(2006, "该商品当前不可购买"),
    /** 不允许卖家给自己下单，否则会出现「自己和自己面交」的订单 */
    BUY_SELF(2007, "不能购买自己发布的商品"),

    VERIFY_CODE_INVALID(2008, "核销码不正确"),
    /** 核销不鉴权谁扫的码（一单一码），但扫码人必须是这笔订单的当事人 */
    NOT_ORDER_PARTY(2009, "不是该订单的买卖双方，无权操作"),

    /* ── 交易互评（2026-10-30 新增，notes/13 O18）── */
    REVIEW_NOT_ALLOWED(2010, "仅已完成的订单可以评价"),
    /** 窗口从面交核销（verify_time）起算 30 天；库里没有单独的「收货时间」列 */
    REVIEW_EXPIRED(2011, "评价窗口已过（面交后 30 天内可评）"),
    REVIEW_TAG_INVALID(2012, "评价标签不合法，请从可选标签中选择"),
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
