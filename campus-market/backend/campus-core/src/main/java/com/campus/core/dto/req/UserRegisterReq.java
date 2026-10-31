package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 学生端注册（notes/26）。
 *
 * <p>手机号与 QQ 邮箱都必填：手机号是交易联系方式，邮箱是唯一可真实校验的凭据。
 * 只验邮箱不验手机号——短信是演示假码，用它「验证」手机号只会给出虚假的安全感。
 */
@Data
public class UserRegisterReq {

    @NotBlank(message = "请输入手机号")
    private String phone;

    @NotBlank(message = "请输入 QQ 邮箱")
    private String email;

    @NotBlank(message = "请输入昵称")
    private String nickname;

    @NotBlank(message = "请设置密码")
    private String password;

    /** 邮箱收到的 6 位数字验证码 */
    @NotBlank(message = "请输入邮箱验证码")
    private String mailCode;
}
