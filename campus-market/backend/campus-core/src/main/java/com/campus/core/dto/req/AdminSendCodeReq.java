package com.campus.core.dto.req;

import com.campus.core.enums.MailCodeScene;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 申请邮箱验证码（business/03 F-A0 注册 / F-A10 找回密码 / F-A1 邮箱验证码登录）。
 *
 * <p>三个场景共用一个入参、一个端点：`type` 决定场景（{@link MailCodeScene}），
 * 其余字段完全一致。分成三个 DTO 会让「发码」这件事的三份副本各自漂移。
 */
@Data
public class AdminSendCodeReq {

    /**
     * 场景：1 注册 / 2 找回密码 / 3 登录（与后端 {@link MailCodeScene}、前端 `MAIL_CODE_SCENE` 同源）。
     *
     * <p>只标 `@NotNull` 不标范围——越界值由 `MailCodeScene.fromCodeOrNull` 转成 1000 参数错误，
     * 若在枚举里抛异常会被兜底成 5000。
     */
    @NotNull(message = "缺少验证码场景")
    private Integer type;

    @NotBlank(message = "邮箱不能为空")
    private String email;

    private String captchaKey;

    private String captchaCode;
}
