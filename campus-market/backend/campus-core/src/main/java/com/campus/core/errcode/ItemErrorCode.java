package com.campus.core.errcode;

import com.campus.common.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品模块错误码（段位 11xx，登记于 tech/01 5.5）。
 * 归商品模块自己维护；实现 IErrorCode 后可直接进 BizException / Result / BizAssert。
 */
@Getter
@AllArgsConstructor
public enum ItemErrorCode implements IErrorCode {

    ITEM_NOT_FOUND(1101, "商品不存在"),
    ITEM_NOT_ON_SALE(1102, "商品已售出或已下架"),
    ITEM_SOLD_OUT(1103, "手慢了，商品已被他人买走"),
    ITEM_PENDING_REVIEW(1104, "商品正在审核中"),
    ITEM_CATEGORY_MISMATCH(1105, "仅支持对比同一分类下的商品"),

    /* ── 1106 起是管理端专用（business/03 A-B4） ── */

    ITEM_CANNOT_OFFLINE(1106, "当前状态不能下架（仅待审核 / 在售可下架）"),
    ITEM_CANNOT_DELETE(1107, "在售或已售出的商品不能删除，请先下架"),

    /** 1108 起是学生端专用：区分「资源不存在」与「存在但不是你的」，
     *  后者一律返回 1108 而不是 1101，避免把所有权信息泄漏成是否存在的信息 */
    ITEM_NOT_OWNER(1108, "只能操作自己发布的商品"),

    /** 编辑只允许在售 / 已下架：待审核与已处置态再改，会让队列里的那条单据失去指向 */
    ITEM_CANNOT_EDIT(1109, "当前状态不能编辑（仅在售或已下架可编辑）"),
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
