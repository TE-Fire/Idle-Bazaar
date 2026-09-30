package com.campus.core.dto.req;

import com.campus.core.enums.MailCodeSceneEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 申请邮箱验证码。三场景共用一个入参、一个端点，`type` 决定场景；分成三个 DTO 会让副本各自漂移。 */
@Data
public class AdminSendCodeReq {

    /**
     * 1 注册 / 2 找回密码 / 3 登录（与 {@link MailCodeSceneEnum}、前端 `MAIL_CODE_SCENE` 同源）。
     * 只标 `@NotNull` 不标范围：越界值由 `fromCodeOrNull` 转成 1000，在枚举里抛异常会被兜底成 5000。
     */
    @NotNull(message = "缺少验证码场景")
    private Integer type;

    @NotBlank(message = "邮箱不能为空")
    private String email;

    private String captchaKey;

    private String captchaCode;
}
