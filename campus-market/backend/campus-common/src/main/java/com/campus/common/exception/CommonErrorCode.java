package com.campus.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用错误码（只放跨模块的通用段，业务段归各模块自己维护，见 tech/03 W1）。
 *
 * <p>段位规划（全平台登记，新模块在此占段后才能扩枚举）：
 * 通用 1000 / 商品 1100 / 订单 2000 / 审核 3000 / 用户 4000 / 系统 5000。
 * 本枚举只持有 通用段 10xx 与 系统段 50xx。
 */
@Getter
@AllArgsConstructor
public enum CommonErrorCode implements IErrorCode {

    OK(0, "成功"),

    /* ── 通用 10xx ─────────────────────────── */
    PARAM_ERROR(1000, "参数错误"),
    UNAUTHORIZED(1001, "未登录或登录已失效"),
    FORBIDDEN(1002, "无权限执行该操作"),
    NOT_FOUND(1003, "资源不存在"),
    IDEMPOTENT_REPEAT(1004, "重复提交，请稍后再试"),

    /* ── 系统 50xx ─────────────────────────── */
    SYSTEM_ERROR(5000, "系统繁忙，请稍后重试"),
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
