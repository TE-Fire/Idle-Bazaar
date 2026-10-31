package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.core.dto.resp.VerifyCodeVO;
import com.campus.core.enums.MailCodeSceneEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.redis.UserRedisKeys;
import com.campus.core.support.QqMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 学生端 QQ 邮箱验证码（注册 / 找回密码 / 邮箱登录）。
 *
 * <p>与管理端 {@link AdminAccountService} 的邮箱码同构，差别只在「账号表是 app_user」和
 * 「未注册要明确报错」：学生端登录本来就提示手机号未注册，找回再隐藏反而不一致。
 *
 * <p>演示期不接 SMTP 也能走完流程——`campus.user.mail.echo=true` 时验证码随响应回显，
 * 为 false 时只进日志。上线前把开关关掉即可，节流、TTL、一次性的口径都不动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMailService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration THROTTLE_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;
    private final MailService mailService;
    private final AppUserMapper appUserMapper;
    private final CodeAttemptGuard codeAttemptGuard;
    private final SecureRandom random = new SecureRandom();

    /** 回显开关：关闭时 devCode 为 null（生产形态） */
    @Value("${campus.user.mail.echo:true}")
    private boolean echoEnabled;

    /**
     * 发码：格式 → 节流 → 场景判据 → 生成 → 存 Redis → 发信。
     *
     * <p>节流必须在查库之前：先查库会让响应耗时随邮箱是否注册而变，等于开了枚举侧信道。
     */
    public VerifyCodeVO sendCode(String rawEmail, MailCodeSceneEnum scene) {
        String email = QqMail.normalize(rawEmail);
        BizAssert.isTrue(QqMail.isQqMail(email), UserErrorCode.EMAIL_INVALID);

        Boolean fresh = stringRedisTemplate.opsForValue()
                .setIfAbsent(UserRedisKeys.buildMailThrottleKey(email), "1", THROTTLE_TTL);
        BizAssert.isTrue(Boolean.TRUE.equals(fresh), UserErrorCode.MAIL_CODE_TOO_FREQUENT);

        boolean registered = appUserMapper.selectByEmail(email) != null;
        switch (scene) {
            // 注册页是公开入口，填了已注册的邮箱要立刻知道，别等提交才报
            case REGISTER -> BizAssert.isTrue(!registered, UserErrorCode.EMAIL_ALREADY_REGISTERED);
            case RESET, LOGIN -> BizAssert.isTrue(registered, UserErrorCode.EMAIL_NOT_REGISTERED);
            default -> throw new IllegalStateException("未处理的邮箱验证码场景：" + scene);
        }

        String code = sixDigitCode();
        stringRedisTemplate.opsForValue()
                .set(UserRedisKeys.buildMailCodeKey(scene.name(), email), code, CODE_TTL);
        mailService.sendVerifyCode(email, code, scene);

        VerifyCodeVO vo = new VerifyCodeVO();
        vo.setResendAfter((int) THROTTLE_TTL.toSeconds());
        vo.setChannel(2);
        if (echoEnabled) {
            vo.setDevCode(code);
        }
        return vo;
    }

    /**
     * 校验：先比对、后删除。错 5 次作废，逼用户重新发码（重发要过 60 秒节流）。
     *
     * <p>场景必须一致：注册码不能用来登录、登录码不能用来改密码，key 里带场景就是这个目的。
     */
    public void verifyCode(String email, MailCodeSceneEnum scene, String code) {
        String scope = "mail:" + scene.name() + ":" + email;
        String key = UserRedisKeys.buildMailCodeKey(scene.name(), email);
        String cached = stringRedisTemplate.opsForValue().get(key);
        BizAssert.notNull(cached, UserErrorCode.MAIL_CODE_INVALID);

        boolean matched = cached.equals(code == null ? "" : code.trim());
        if (!matched && codeAttemptGuard.recordFail(scope)) {
            stringRedisTemplate.delete(key);
            log.warn("[user] 邮箱 {} 的{}验证码错误次数达上限，已作废", email, scene.getDesc());
        }
        BizAssert.isTrue(matched, UserErrorCode.MAIL_CODE_INVALID);

        stringRedisTemplate.delete(key);
        codeAttemptGuard.clear(scope);
    }

    private String sixDigitCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
