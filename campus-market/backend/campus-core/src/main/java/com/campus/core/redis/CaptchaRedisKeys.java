package com.campus.core.redis;

/** 图形验证码的 key，形态 `cm:captcha:{captchaKey}`，值是验证码文本，TTL 5 分钟 */
public final class CaptchaRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "captcha:";

    /** @param captchaKey 下发时给前端的一次性随机串，提交时原样带回 */
    public static String buildCodeKey(String captchaKey) {
        return MODULE + captchaKey;
    }

    private CaptchaRedisKeys() {
    }
}
