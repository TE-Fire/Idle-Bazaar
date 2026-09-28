package com.campus.core.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送（business/03 F-A10 找回密码、F-A0 注册）。
 *
 * <p>两条设计：
 * <ol>
 *   <li>**开关关闭时只打日志**。本地没配 SMTP 授权码也能跑通全流程，
 *       验证码从控制台看——否则每次调试都要真发一封邮件，QQ 邮箱还会限流。</li>
 *   <li>**注入用 `required = false`**。`spring-boot-starter-mail` 只在配了
 *       `spring.mail.host` 时才创建 `JavaMailSender`，没配就是 null；
 *       硬注入会让应用在本地直接起不来。</li>
 * </ol>
 *
 * <p>配置占位见 `application-dev.yml` 的 `campus.mail.*` 与 `spring.mail.*`：
 * **授权码不要写进仓库**，dev 配置已被 gitignore。
 */
@Slf4j
@Service
public class MailService {

    @Value("${campus.mail.enabled:false}")
    private boolean enabled;

    @Value("${campus.mail.from:}")
    private String from;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    /**
     * 发送验证码邮件。
     *
     * @param to   收件邮箱
     * @param code 6 位数字验证码
     * @param scene 场景（注册 / 找回密码），邮件标题与正文会区分
     */
    public void sendVerifyCode(String to, String code, String scene) {
        String subject = "【校园二手交易平台】" + scene + "验证码";
        String body = "你正在进行" + scene + "操作，验证码：" + code + "\n\n"
                + "10 分钟内有效，请勿告知他人。若非本人操作请忽略本邮件。";

        if (!enabled || mailSender == null) {
            // 开关关闭（本地联调）：验证码落日志，联调时从控制台取
            log.info("[mail] 开关关闭，{} 验证码 {} → {}（正文：{}）", scene, code, to, subject);
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("[mail] 已发送{}验证码至 {}", scene, to);
        } catch (Exception e) {
            // 邮件发不出去不能让注册/找回流程 500：降级为日志，保证流程能走完
            log.error("[mail] 发送失败，{} 验证码 {} → {}", scene, code, to, e);
        }
    }
}
