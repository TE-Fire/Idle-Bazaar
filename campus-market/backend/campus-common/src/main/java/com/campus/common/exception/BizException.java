package com.campus.common.exception;

import lombok.Getter;

/**
 * 业务异常：可预期的失败（商品已售出、订单状态不对、无权限等）。
 * 与系统异常的区别是它不打堆栈，直接把 code/msg 返回给前端。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public BizException(ErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode);
    }
}
