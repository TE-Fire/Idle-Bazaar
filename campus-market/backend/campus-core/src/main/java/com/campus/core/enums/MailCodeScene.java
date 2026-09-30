package com.campus.core.enums;

import lombok.Getter;

/**
 * 邮箱验证码的业务场景：注册 / 找回密码 / 邮箱验证码登录。
 *
 * <p>三者在「怎么发码」上完全一致（图形验证码 → 归一化 → QQ 系校验 → 60 秒节流 →
 * 6 位码 → 存 Redis → 发信），只有**发信文案**和**要不要先查库判据**不同，
 * 所以合成一个 `sendCode(AdminSendCodeReq)` 入口，用本枚举区分。
 *
 * <p>为什么只给 `fromCodeOrNull` 而不是抛异常的 `fromCode`：`type` 是**前端传来的**，
 * 非法值必须变成 1000 参数错误，抛 IllegalArgumentException 会被兜底成 5000 系统异常。
 * （库里读出来的 status 用 {@link AdminStatus#fromCode} 抛异常是对的——那属于数据异常。）
 *
 * <p>code 与前端 `MAIL_CODE_SCENE` 一一对应，改值要同步改前端。
 */
@Getter
public enum MailCodeScene {

    REGISTER(1, "注册"),
    RESET(2, "找回密码"),
    LOGIN(3, "登录");

    private final Integer code;
    /** 发信文案的场景名，直接进邮件标题/正文 */
    private final String desc;

    MailCodeScene(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /** 未登记的值返回 null，由调用方转成参数错误；不要改成抛异常 */
    public static MailCodeScene fromCodeOrNull(Integer code) {
        if (code != null) {
            for (MailCodeScene scene : values()) {
                if (scene.code.equals(code)) {
                    return scene;
                }
            }
        }
        return null;
    }
}
