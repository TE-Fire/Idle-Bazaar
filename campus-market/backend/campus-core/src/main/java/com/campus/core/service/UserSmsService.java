package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.resp.VerifyCodeVO;
import com.campus.core.enums.MailCodeSceneEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.redis.UserRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 学生端短信验证码（登录 / 找回密码）。
 *
 * <p>演示期不接短信服务商：验证码照常生成、存 Redis、打日志，并随发码响应回显
 * （开关 `campus.user.sms.echo`）。上线前把发送那一处换成真实短信商即可，
 * 节流、TTL、一次性校验的口径都不动。
 *
 * <p>**不服务注册场景**：注册唯一被验证的是邮箱，短信是演示假码，用它「验证」手机号
 * 只会给出虚假的安全感（notes/26 已决）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSmsService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration THROTTLE_TTL = Duration.ofSeconds(60);
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    private final StringRedisTemplate stringRedisTemplate;
    private final AppUserMapper appUserMapper;
    private final CodeAttemptGuard codeAttemptGuard;
    private final SecureRandom random = new SecureRandom();

    /** 回显开关：关闭时 devCode 为 null，验证码只进日志（生产形态） */
    @Value("${campus.user.sms.echo:true}")
    private boolean echoEnabled;

    /**
     * 发码：格式 → 场景 → 节流 → 查人 → 生成 → 存 Redis → 发送。
     *
     * <p>节流用 setIfAbsent 抢占，抢不到就拒。顺序与邮箱一致（节流先于查库），
     * 否则「手机号是否存在」会从响应耗时里漏出去。
     */
    public VerifyCodeVO sendCode(String phone, MailCodeSceneEnum scene) {
        requirePhone(phone);
        // 注册只走邮箱验证码；调用方拼错场景时立刻暴露，而不是悄悄发一条没用的短信
        BizAssert.isTrue(scene == MailCodeSceneEnum.LOGIN || scene == MailCodeSceneEnum.RESET,
                UserErrorCode.REGISTER_REQUIRE_MAIL);

        Boolean fresh = stringRedisTemplate.opsForValue()
                .setIfAbsent(UserRedisKeys.buildSmsThrottleKey(phone), "1", THROTTLE_TTL);
        BizAssert.isTrue(Boolean.TRUE.equals(fresh), UserErrorCode.USER_SMS_TOO_FREQUENT);

        BizAssert.notNull(appUserMapper.selectByPhone(phone), UserErrorCode.USER_NOT_FOUND);

        String code = sixDigitCode();
        stringRedisTemplate.opsForValue()
                .set(UserRedisKeys.buildSmsCodeKey(scene.name(), phone), code, CODE_TTL);
        // 演示期没有短信通道，验证码进日志；接短信商后把这里换成发送调用
        log.info("[user] 手机号 {} 的{}验证码：{}", phone, scene.getDesc(), code);

        VerifyCodeVO vo = new VerifyCodeVO();
        vo.setResendAfter((int) THROTTLE_TTL.toSeconds());
        vo.setChannel(1);
        if (echoEnabled) {
            vo.setDevCode(code);
        }
        return vo;
    }

    /**
     * 校验：先比对、后删除——反过来的话输错一次就把正确的码也删了，用户只能重新发。
     * 错 5 次作废，防止 6 位数字被穷举。
     */
    public void verifyCode(String phone, MailCodeSceneEnum scene, String code) {
        requirePhone(phone);
        String scope = "sms:" + scene.name() + ":" + phone;
        String key = UserRedisKeys.buildSmsCodeKey(scene.name(), phone);
        String cached = stringRedisTemplate.opsForValue().get(key);
        BizAssert.notNull(cached, UserErrorCode.USER_SMS_CODE_INVALID);

        boolean matched = cached.equals(code == null ? "" : code.trim());
        if (!matched && codeAttemptGuard.recordFail(scope)) {
            stringRedisTemplate.delete(key);
            log.warn("[user] 手机号 {} 的{}验证码错误次数达上限，已作废", phone, scene.getDesc());
        }
        BizAssert.isTrue(matched, UserErrorCode.USER_SMS_CODE_INVALID);

        stringRedisTemplate.delete(key);
        codeAttemptGuard.clear(scope);
    }

    /** 手机号格式：与前端 `^1[3-9]\\d{9}$` 同一口径 */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(phone.trim()).matches();
    }

    private static void requirePhone(String phone) {
        BizAssert.isTrue(isValidPhone(phone), UserErrorCode.USER_PHONE_INVALID);
    }

    private String sixDigitCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
