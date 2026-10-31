package com.campus.core.redis;

/**
 * 学生端验证码与密码登录的 key。
 *
 * <p>短信与邮箱各自一对 code / throttle：TTL 与语义不同，节流过期时验证码还得有效。
 * 邮箱验证码的 code key 带场景（注册 / 找回 / 登录），跨场景能用等于让掉一道校验。
 */
public final class UserRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "user:";

    /** 实际形态 `cm:user:sms:code:{scene}:{phone}`。短信码同样按场景隔离，登录码不能拿去改密码 */
    public static String buildSmsCodeKey(String scene, String phone) {
        return MODULE + "sms:code:" + scene + ":" + phone;
    }

    /** 实际形态 `cm:user:sms:throttle:{phone}`。setIfAbsent 抢占，抢到才允许发 */
    public static String buildSmsThrottleKey(String phone) {
        return MODULE + "sms:throttle:" + phone;
    }

    /** 实际形态 `cm:user:mail:code:{scene}:{email}`。scene 用枚举名而非 code，便于日志辨认 */
    public static String buildMailCodeKey(String scene, String email) {
        return MODULE + "mail:code:" + scene + ":" + email;
    }

    /** 实际形态 `cm:user:mail:throttle:{email}`。按邮箱节流，不按场景 */
    public static String buildMailThrottleKey(String email) {
        return MODULE + "mail:throttle:" + email;
    }

    /**
     * 实际形态 `cm:user:pwd:fail:{userId}`。密码登录失败计数，10 分钟窗口。
     * 按 userId 而非账号：同一账号换大小写 / 换渠道都落在同一个计数器上。
     */
    public static String buildPwdFailKey(long userId) {
        return MODULE + "pwd:fail:" + userId;
    }

    private UserRedisKeys() {
    }
}
