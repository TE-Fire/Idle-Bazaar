package com.campus.core.enums;

import lombok.Getter;

/**
 * 邮箱验证码的业务场景。三者的发码动作完全一致，只有发信文案和「要不要先查库判据」不同，
 * 故合成一个 `sendCode` 入口，用本枚举区分。
 *
 * <p>code 与前端 `MAIL_CODE_SCENE` 一一对应，改值要同步改前端。
 */
@Getter
public enum MailCodeSceneEnum {

    REGISTER(1, "注册"),
    RESET(2, "找回密码"),
    LOGIN(3, "登录");

    private final Integer code;
    /** 直接进邮件文案 */
    private final String desc;

    MailCodeSceneEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 未登记的值返回 null，由调用方转成 1000 参数错误。
     *
     * <p>不要改成抛异常：`type` 由前端传入，抛异常会被兜底成 5000。
     * 库里读出来的字段才用抛异常版（见 {@link AdminStatusEnum#fromCode}）。
     */
    public static MailCodeSceneEnum fromCodeOrNull(Integer code) {
        if (code != null) {
            for (MailCodeSceneEnum scene : values()) {
                if (scene.code.equals(code)) {
                    return scene;
                }
            }
        }
        return null;
    }
}
