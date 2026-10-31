package com.campus.core.service;

import com.campus.core.enums.MailCodeSceneEnum;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 验证码邮件发送（注册 / 找回密码 / 登录）。
 *
 * <p>正文是品牌主题的 HTML（模板 {@code resources/mail/verify-code.html}），同时附一份纯文本
 * 作为 alternative：个别客户端屏蔽 HTML 时至少还能看到验证码。
 *
 * <p>两条本地可跑通的兜底：① 开关关闭时只打日志，没配 SMTP 也能走完注册流程；
 * ② 发信异常降级为日志，不让注册 / 找回 500。`JavaMailSender` 用 `required = false`，
 * 未配 `spring.mail.host` 时容器里没有这个 Bean，硬注入会让应用起不来。
 *
 * <p>授权码不要写进仓库（dev 配置已 gitignore）。
 */
@Slf4j
@Service
public class MailService {

    private static final String TEMPLATE_PATH = "mail/verify-code.html";

    @Value("${campus.mail.enabled:false}")
    private boolean enabled;

    @Value("${campus.mail.from:}")
    private String from;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    /** 模板只读一次；读取失败置空，发信时退回纯文本 */
    private String template;

    @PostConstruct
    void loadTemplate() {
        try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
            template = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[mail] 未读到邮件模板 {}，正文退化为纯文本", TEMPLATE_PATH, e);
        }
    }

    /**
     * 发送验证码邮件。
     *
     * @param to    收件邮箱
     * @param code  6 位数字验证码
     * @param scene 场景，决定标题与正文文案
     */
    public void sendVerifyCode(String to, String code, MailCodeSceneEnum scene) {
        String subject = "【校园二手交易平台】" + scene.getDesc() + "验证码";
        String plain = "你正在进行" + scene.getDesc() + "操作，验证码：" + code
                + "\n\n10 分钟内有效，请勿告知他人。若非本人操作请忽略本邮件。";

        if (!enabled || mailSender == null) {
            // 开关关闭（本地联调）：验证码落日志，联调时从控制台取
            log.info("[mail] 开关关闭，{} 验证码 {} → {}（正文：{}）", scene.getDesc(), code, to, subject);
            return;
        }

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            String html = renderHtml(code, scene, to);
            if (html == null) {
                helper.setText(plain);
            } else {
                helper.setText(plain, html);
            }
            mailSender.send(msg);
            log.info("[mail] 已发送{}验证码至 {}", scene.getDesc(), to);
        } catch (Exception e) {
            // 邮件发不出去不能让注册/找回流程 500：降级为日志，保证流程能走完
            log.error("[mail] 发送失败，{} 验证码 {} → {}", scene.getDesc(), code, to, e);
        }
    }

    /** 模板渲染：占位符直接替换，不引模板引擎 */
    private String renderHtml(String code, MailCodeSceneEnum scene, String email) {
        if (template == null) {
            return null;
        }
        return template
                .replace("{{desc}}", scene.getDesc())
                .replace("{{tip}}", tipOf(scene))
                .replace("{{code}}", code)
                .replace("{{email}}", escape(email))
                .replace("{{year}}", String.valueOf(LocalDate.now().getYear()));
    }

    /** 三个场景各一句针对性提示，比统一的「请勿告知他人」更有用 */
    private static String tipOf(MailCodeSceneEnum scene) {
        return switch (scene) {
            case REGISTER -> "注册完成后，手机号将用于登录与面交联系，请确认填写的是常用号码。";
            case RESET -> "若非本人操作，请忽略本邮件；重置生效前旧密码仍可登录。";
            case LOGIN -> "如非本人登录，请忽略本邮件，并尽快在账号安全中更换密码。";
        };
    }

    /** 邮箱已过格式校验，仍做转义：HTML 里的 & 与尖括号不该由用户输入决定 */
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
