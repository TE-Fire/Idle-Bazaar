package com.campus.common.exception;

import lombok.Getter;

/**
 * 业务异常：可预期的失败（商品已售出、订单状态不对、无权限等）。
 * 与系统异常的区别是它不打堆栈，直接把 code/msg 返回给前端。
 *
 * <p>入参只认 {@link IErrorCode} 契约（各模块错误码枚举都实现了它），
 * 不再允许到处传裸的 int code。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    /** 抛出时用的错误码枚举，日志与排查时可还原枚举名 */
    private final IErrorCode errorCode;

    public BizException(IErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BizException(IErrorCode errorCode, String detail) {
        super(detail);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public static BizException of(IErrorCode errorCode) {
        return new BizException(errorCode);
    }
}
