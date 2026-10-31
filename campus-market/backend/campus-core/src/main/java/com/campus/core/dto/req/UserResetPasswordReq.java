package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 学生端找回密码：账号（手机号或邮箱）+ 该渠道收到的验证码 + 新密码。
 *
 * <p>没有 `scene` 字段：找回密码只有一个场景，渠道由 `account` 的形态推断。
 */
@Data
public class UserResetPasswordReq {

    @NotBlank(message = "请输入手机号或 QQ 邮箱")
    private String account;

    @NotBlank(message = "请输入验证码")
    private String code;

    @NotBlank(message = "请设置新密码")
    private String password;
}
