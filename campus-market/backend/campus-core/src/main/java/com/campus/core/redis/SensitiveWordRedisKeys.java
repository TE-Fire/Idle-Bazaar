package com.campus.core.redis;

/**
 * 敏感词库版本戳，`cm:sensitive:ver`。
 *
 * <p>词库整表载入内存做过滤，每个实例各持一份副本；改词后靠这个计数器让所有实例失效重载，
 * 而不是等本地缓存自然过期（本地 TTL 只是 Redis 不可用时的兜底）。
 *
 * <p>值本身没有含义，只要单调变化即可。词库被改（增删词、调级别、启停）后
 * `INCR` 一次，各实例下一次过滤就会读到新版本号并重载。
 */
public final class SensitiveWordRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "sensitive:";

    public static final String VERSION = MODULE + "ver";

    private SensitiveWordRedisKeys() {
    }
}
