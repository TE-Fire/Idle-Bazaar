package com.campus.core.redis;

/**
 * 学生公开资料缓存 key，形态 `cm:user:pub:{userId}`，值是
 * {@link com.campus.core.dto.resp.UserPubVO} 的 JSON。
 *
 * <p>独立于 {@link UserRedisKeys}（验证码 / 节流）：语义与失效手段都不同，
 * 按本包约定一个关注点一个 key 类。
 */
public final class UserPubRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "user:pub:";

    public static String buildKey(Long userId) {
        return MODULE + userId;
    }

    private UserPubRedisKeys() {
    }
}
