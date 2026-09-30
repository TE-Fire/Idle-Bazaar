package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 邮箱验证码登录入参（business/03 F-A1 的第二种登录方式）。
 *
 * <p>与密码登录 {@link AdminLoginReq} **并列，不合并成一个类**：两者的凭据形态完全不同
 * （长期密码 vs 一次性邮箱码），合并后「哪几个字段必填」要靠另一个字段是否为空来推导，
 * 读代码的人得先做一次推理才能看懂，Jackson 也没法直接表达这种互斥必填。
 *
 * <p>图形验证码**可选**：发码那一步已经强制过了（防刷邮件）。
 * 但提交登录这一步**建议也校验一次**——6 位数字码只有 100 万种组合，
 * 而邮箱验证码是「比对成功才删」，输错**不计数**，10 分钟有效期内可无限次重试。
 * 字段留在这里，是否校验由 {@code AdminAuthService#loginByMailCode} 决定。
 */
@Data
public class AdminMailLoginReq {

    /** 注册时用的 QQ 邮箱，与 admin_user.email 对应（uk_email 唯一） */
    @NotBlank(message = "邮箱不能为空")
    private String email;

    /** 邮箱收到的 6 位数字验证码：10 分钟有效、一次性 */
    @NotBlank(message = "邮箱验证码不能为空")
    private String mailCode;

    /** 图形验证码 key；后端开关关闭时可为空 */
    private String captchaKey;

    private String captchaCode;
}
