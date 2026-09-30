package com.campus.core.redis;

/**
 * 图形验证码的 key（business/03 F-A1 登录 / F-A0 注册 / F-A10 找回密码，三处共用）。
 *
 * <p>实际形态：`cm:captcha:{captchaKey}`，值是验证码文本，TTL 5 分钟。
 */
public final class CaptchaRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "captcha:";

    /**
     * 验证码文本。
     *
     * @param captchaKey 下发时返回给前端的一次性随机串，提交时原样带回
     */
    public static String code(String captchaKey) {
        return MODULE + captchaKey;
    }

    private CaptchaRedisKeys() {
    }
}
