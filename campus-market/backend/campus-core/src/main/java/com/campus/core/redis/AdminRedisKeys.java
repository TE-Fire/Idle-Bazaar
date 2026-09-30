package com.campus.core.redis;

/**
 * 管理员相关 key，形态 `cm:admin:lock:{username}`，值 `"1"`，TTL 10 分钟。
 *
 * <p>用 username 而不是 admin id：锁定判断要抢在查库之前完成才有意义，那时还拿不到 id；
 * DB 的 `admin_user.lock_until` 只是兜底。
 */
public final class AdminRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "admin:";

    /** 账号锁定标记：登录失败 5 次后写入，期间同账号登录直接拒绝 */
    public static String buildLockKey(String username) {
        return MODULE + "lock:" + username;
    }

    private AdminRedisKeys() {
    }
}
