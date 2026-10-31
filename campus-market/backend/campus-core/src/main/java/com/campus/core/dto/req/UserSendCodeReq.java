package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学生端发送验证码。一个端点覆盖三种场景，`scene` 决定用途、`account` 决定走短信还是邮箱。
 *
 * <p>不拆成「发短信」「发邮件」两个端点：前端只有一个「获取验证码」按钮，
 * 拆开会让「账号是手机号还是邮箱」这个判断在两端各写一遍，提示容易分叉。
 */
@Data
public class UserSendCodeReq {

    /** 手机号或 QQ 邮箱；注册场景只接受邮箱 */
    @NotBlank(message = "请输入手机号或 QQ 邮箱")
    private String account;

    /** 与 `MailCodeSceneEnum` 同源：1 注册 / 2 找回密码 / 3 登录 */
    @NotNull(message = "缺少验证码场景")
    private Integer scene;
}
