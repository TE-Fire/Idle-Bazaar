package com.campus.core.enums;

import lombok.Getter;

/**
 * 登录方式。两种共用 `AdminLoginReq` 与 `AdminAuthService.login`，只有「凭据怎么校验」不同。
 *
 * <p>code 与前端 `LOGIN_TYPE` 一一对应，改值要同步改前端。
 */
@Getter
public enum LoginTypeEnum {

    BY_USERNAME(1, "用户名登录"),
    BY_EMAIL(2, "邮箱登录");

    private final Integer code;
    private final String desc;

    LoginTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 未登记的值返回 null，由调用方转成 1000 参数错误。
     *
     * <p>**不要改成抛异常**：`type` 是前端传来的，抛 `IllegalArgumentException`
     * 会被全局兜底成 5000 系统异常，而它本质上是参数错误。
     * （库里读出来的字段才用抛异常版，见 {@link AdminStatusEnum#fromCode}。）
     */
    public static LoginTypeEnum fromCodeOrNull(Integer code) {
        if (code != null) {
            for (LoginTypeEnum type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
        }
        return null;
    }
}
