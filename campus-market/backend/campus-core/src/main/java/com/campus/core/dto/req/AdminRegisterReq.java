package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理端注册入参（business/03 F-A0）
 *
 * <p>**没有 roleId 字段是刻意的**：角色只能由超级管理员分配，
 * 多一个字段就等于开放「注册即审核员」的口子。
 */
@Data
public class AdminRegisterReq {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    /** 邮箱收到的 6 位数字验证码 */
    @NotBlank(message = "邮箱验证码不能为空")
    private String mailCode;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需 8–32 位")
    private String password;

    /** 姓名，可为空（登录后在右上角显示） */
    private String realName;

    /** 图形验证码：防机器人批量注册 */
    private String captchaKey;

    private String captchaCode;
}
