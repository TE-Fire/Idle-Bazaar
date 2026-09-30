package com.campus.core.redis;

/**
 * 管理员相关 key。
 *
 * <p>实际形态：`cm:admin:lock:{username}`，值为 `"1"`，TTL 10 分钟。
 *
 * <p>key 里用 **username** 而不是 admin id：锁定判断要抢在查库之前完成才有意义——
 * 那时还用不上 id，而 DB 的 `admin_user.lock_until` 只作为兜底。
 */
public final class AdminRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "admin:";

    /**
     * 账号锁定标记。登录失败 5 次后写入，期间同账号登录直接拒绝。
     *
     * @param username 登录名；与登录入参保持一致（未查库时只能用它）
     */
    public static String buildLockKey(String username) {
        return MODULE + "lock:" + username;
    }

    private AdminRedisKeys() {
    }
}
