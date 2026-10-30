package com.campus.core.redis;

/**
 * 系统配置缓存 key：`cm:config:sla`（Hash，字段即六个 `sla.*` 键）。
 *
 * <p>结构按「怎么读」选：SLA 卡与报表一次要全部六个阈值，Hash 一次 `entries()` 就够；
 * 若拆成六个 String key，读一次要六次往返。TTL 10 分钟只是兜底，写操作提交后直接 `DEL`。
 */
public final class ConfigRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "config:";

    /** SLA 六个阈值（Hash） */
    public static final String SLA = MODULE + "sla";

    private ConfigRedisKeys() {
    }
}
