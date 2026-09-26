package com.campus.core.errcode;

import com.campus.common.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户模块错误码（段位 40xx，登记于 tech/01 5.5）。
 * C 端用户与管理员同属「用户域」，故管理员登录类错误码也放这里。
 */
@Getter
@AllArgsConstructor
public enum UserErrorCode implements IErrorCode {

    USER_BANNED(4001, "账号已被封禁"),
    USER_AUTH_NOT_PASSED(4002, "学号认证未通过"),
    USER_CREDIT_TOO_LOW(4003, "信用分过低，该操作暂不可用"),
    ADMIN_ACCOUNT_LOCKED(4004, "连续失败次数过多，账号已锁定 10 分钟"),
    ADMIN_BAD_CREDENTIAL(4005, "账号或密码错误"),
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
