package com.campus.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局错误码，按模块分段（tech/01 5.5）：
 * 通用 1000 / 商品 1100 / 订单 2000 / 审核 3000 / 用户 4000 / 系统 5000
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    OK(0, "成功"),

    /* ── 通用 10xx ─────────────────────────── */
    PARAM_ERROR(1000, "参数错误"),
    UNAUTHORIZED(1001, "未登录或登录已失效"),
    FORBIDDEN(1002, "无权限执行该操作"),
    NOT_FOUND(1003, "资源不存在"),
    IDEMPOTENT_REPEAT(1004, "重复提交，请稍后再试"),

    /* ── 商品 11xx ─────────────────────────── */
    ITEM_NOT_FOUND(1101, "商品不存在"),
    ITEM_NOT_ON_SALE(1102, "商品已售出或已下架"),
    ITEM_SOLD_OUT(1103, "手慢了，商品已被他人买走"),
    ITEM_PENDING_REVIEW(1104, "商品正在审核中"),
    ITEM_CATEGORY_MISMATCH(1105, "仅支持对比同一分类下的商品"),

    /* ── 订单 20xx ─────────────────────────── */
    ORDER_NOT_FOUND(2001, "订单不存在"),
    ORDER_TIMEOUT(2002, "订单已超时取消"),
    ORDER_STATUS_INVALID(2003, "当前订单状态不允许该操作"),
    ORDER_NOT_OWNER(2004, "无权操作他人订单"),

    /* ── 审核 30xx ─────────────────────────── */
    REVIEW_HANDLED(3001, "该单据已被其他管理员处置"),
    REVIEW_APPEAL_SAME_HANDLER(3002, "申诉复审不能由原处置人处理"),

    /* ── 用户 40xx ─────────────────────────── */
    USER_BANNED(4001, "账号已被封禁"),
    USER_AUTH_NOT_PASSED(4002, "学号认证未通过"),
    USER_CREDIT_TOO_LOW(4003, "信用分过低，该操作暂不可用"),
    ADMIN_ACCOUNT_LOCKED(4004, "连续失败次数过多，账号已锁定 10 分钟"),
    ADMIN_BAD_CREDENTIAL(4005, "账号或密码错误"),

    /* ── 系统 50xx ─────────────────────────── */
    SYSTEM_ERROR(5000, "系统繁忙，请稍后重试"),
    ;

    private final int code;
    private final String msg;
}
