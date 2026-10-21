package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.core.dto.resp.SmsCodeVO;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.redis.UserRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 学生端短信验证码（登录用）。
 *
 * <p>演示期不接短信服务商：验证码照常生成、存 Redis、打日志，并随发码响应回显
 * （开关 `campus.user.sms.echo`）。上线前把发送那一处换成真实短信商即可，
 * 节流、TTL、一次性校验的口径都不动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSmsService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration THROTTLE_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom random = new SecureRandom();

    /** 回显开关：关闭时 devCode 为 null，验证码只进日志（生产形态） */
    @Value("${campus.user.sms.echo:true}")
    private boolean echoEnabled;

    /**
     * 发码：节流 → 生成 → 存 Redis → 发送。节流用 setIfAbsent 抢占，抢不到就拒，
     * 与邮箱验证码同一套口径；按手机号节流，60 秒内最多发一条。
     */
    public SmsCodeVO sendCode(String phone) {
        requirePhone(phone);
        Boolean fresh = stringRedisTemplate.opsForValue()
                .setIfAbsent(UserRedisKeys.buildSmsThrottleKey(phone), "1", THROTTLE_TTL);
        BizAssert.isTrue(Boolean.TRUE.equals(fresh), UserErrorCode.USER_SMS_TOO_FREQUENT);

        String code = sixDigitCode();
        stringRedisTemplate.opsForValue().set(UserRedisKeys.buildSmsCodeKey(phone), code, CODE_TTL);
        // 演示期没有短信通道，验证码进日志；接短信商后把这里换成发送调用
        log.info("[user] 手机号 {} 登录验证码：{}", phone, code);

        SmsCodeVO vo = new SmsCodeVO();
        vo.setResendAfter((int) THROTTLE_TTL.toSeconds());
        if (echoEnabled) {
            vo.setDevCode(code);
        }
        return vo;
    }

    /** 校验：先比对、后删除——反过来的话输错一次就把正确的码也删了，用户只能重新发 */
    public void verifyCode(String phone, String code) {
        requirePhone(phone);
        String key = UserRedisKeys.buildSmsCodeKey(phone);
        String cached = stringRedisTemplate.opsForValue().get(key);
        BizAssert.notNull(cached, UserErrorCode.USER_SMS_CODE_INVALID);
        BizAssert.isTrue(cached.equals(code == null ? "" : code.trim()),
                UserErrorCode.USER_SMS_CODE_INVALID);
        stringRedisTemplate.delete(key);
    }

    private static void requirePhone(String phone) {
        BizAssert.isTrue(phone != null && phone.matches("^1[3-9]\\d{9}$"),
                UserErrorCode.USER_PHONE_INVALID);
    }

    private String sixDigitCode() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
