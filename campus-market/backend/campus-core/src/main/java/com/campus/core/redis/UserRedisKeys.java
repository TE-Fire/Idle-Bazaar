package com.campus.core.redis;

/**
 * 学生端短信验证码的 key。两个 key 一对：code 存 6 位验证码（10 分钟），
 * throttle 挡 60 秒重发——分开存是因为 TTL 与语义不同，节流过期时验证码还得有效。
 */
public final class UserRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "user:";

    /** 实际形态 `cm:user:sms:code:{phone}` */
    public static String buildSmsCodeKey(String phone) {
        return MODULE + "sms:code:" + phone;
    }

    /** 实际形态 `cm:user:sms:throttle:{phone}`。setIfAbsent 抢占，抢到才允许发 */
    public static String buildSmsThrottleKey(String phone) {
        return MODULE + "sms:throttle:" + phone;
    }

    private UserRedisKeys() {
    }
}
