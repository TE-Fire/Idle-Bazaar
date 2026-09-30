package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 找回密码第一步：申请邮箱验证码（business/03 F-A10） */
@Data
public class AdminSendCodeReq {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    private String captchaKey;

    private String captchaCode;
}
