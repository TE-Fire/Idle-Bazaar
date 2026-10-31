package com.campus.core.service;

import com.campus.core.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 验证码错误次数计数。
 *
 * <p>6 位数字只有百万分之一，10 分钟有效期内不限制次数就等于可以离线穷举。
 * 同一个验证码错 5 次直接作废，逼用户重新发码（重发要过 60 秒节流）。
 *
 * <p>计数与验证码分开存：验证码本身是值、不能被计数覆盖，且 TTL 要各自可控。
 */
@Component
@RequiredArgsConstructor
public class CodeAttemptGuard {

    static final int MAX_FAIL = 5;

    private static final String PREFIX = RedisKeys.PREFIX + "user:code:fail:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 记一次失败。
     *
     * @param scope 渠道 + 场景 + 账号，例如 `mail:RESET:a@qq.com`
     * @return true 表示已达上限、验证码应作废
     */
    public boolean recordFail(String scope) {
        String key = PREFIX + scope;
        Long n = stringRedisTemplate.opsForValue().increment(key);
        if (n != null && n == 1L) {
            // 只有首次写入设过期：后续 INCR 会重置 TTL 的话，用户慢慢试也能把窗口拖长
            stringRedisTemplate.expire(key, Duration.ofMinutes(10));
        }
        if (n != null && n >= MAX_FAIL) {
            stringRedisTemplate.delete(key);
            return true;
        }
        return false;
    }

    /** 校验通过后清零，避免下次发码还背着上一次的失败次数 */
    public void clear(String scope) {
        stringRedisTemplate.delete(PREFIX + scope);
    }
}
