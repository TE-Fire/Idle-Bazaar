package com.campus.core.redis;

/**
 * 邮箱验证码的 key（business/03 F-A0 注册 / F-A10 找回密码）。
 *
 * <p>两个 key 是一对：`code` 存 6 位验证码（10 分钟），`throttle` 挡 60 秒内重复发信。
 * 分开存而不是塞一个 hash，是因为两者的 TTL 与语义都不同——节流过期验证码还得有效。
 */
public final class MailRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "mail:";

    /**
     * 6 位邮箱验证码，TTL 10 分钟。实际形态：`cm:mail:code:{email}`。
     *
     * @param email 已规范化的邮箱（小写去空白），非规范化会写出两份 key
     */
    public static String buildCodeKey(String email) {
        return MODULE + "code:" + email;
    }

    /**
     * 发送节流，TTL 60 秒。实际形态：`cm:mail:throttle:{email}`。
     *
     * <p>用 `setIfAbsent` 抢占，抢到才允许发，所以同一邮箱 60 秒内只会真发一封。
     */
    public static String buildThrottleKey(String email) {
        return MODULE + "throttle:" + email;
    }

    private MailRedisKeys() {
    }
}
