package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 学生端发送短信验证码 */
@Data
public class UserSendCodeReq {

    @NotBlank
    private String phone;
}
