package com.campus.core.errcode;

import com.campus.common.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审核模块错误码（段位 30xx，登记于 tech/01 5.5）。
 */
@Getter
@AllArgsConstructor
public enum ReviewErrorCode implements IErrorCode {

    REVIEW_HANDLED(3001, "该单据已被其他管理员处置"),
    REVIEW_APPEAL_SAME_HANDLER(3002, "申诉复审不能由原处置人处理"),
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
