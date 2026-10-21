package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 学生端登录：手机号 + 短信验证码 */
@Data
public class UserLoginReq {

    @NotBlank
    private String phone;

    @NotBlank
    private String code;
}
