package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学生端登录（notes/26）。
 *
 * <p>`account` 是手机号或 QQ 邮箱，后端按形态分流：验证码登录走短信或邮箱验证码，
 * 密码登录查同一张表。合成一个入参是刻意的——两种方式只差「凭据怎么校验」，
 * 拆成两个端点会让「登录要写 last_login_at」这类动作有两份实现。
 */
@Data
public class UserLoginReq {

    @NotBlank(message = "请输入手机号或 QQ 邮箱")
    private String account;

    /** 1 验证码登录 / 2 密码登录 */
    @NotNull(message = "缺少登录方式")
    private Integer type;

    /** type=1 时为 6 位验证码，type=2 时为密码 */
    @NotBlank(message = "请输入密码或验证码")
    private String credential;
}
