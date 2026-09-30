package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 找回密码第二步：验证码 + 新密码（business/03 F-A10） */
@Data
public class AdminResetReq {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "邮箱验证码不能为空")
    private String mailCode;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需 8–32 位")
    private String password;
}
