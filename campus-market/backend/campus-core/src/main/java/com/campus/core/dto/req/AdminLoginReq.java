package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端登录入参（对应原型 A1：账号 + 密码 + 图形验证码）
 */
@Data
public class AdminLoginReq {

    @NotBlank(message = "账号不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码 key（前端拿图时一起返回）；开关关闭时可为空 */
    private String captchaKey;

    private String captchaCode;
}
