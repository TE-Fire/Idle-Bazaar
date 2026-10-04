package com.campus.core.redis;

/**
 * 字典缓存 key。整个模块只有一个：`cm:dict:all`。
 *
 * <p>字典的读分两种，返回内容不同，不能共用一个 key：
 * 管理端 `/admin/dict/**` 要看到停用项、改完要立刻看到新值，直查库；
 * 学生端 `/api/dict/all` 只下发启用项，走缓存。既然只有后者需要缓存，就不必给五张表各建一个 key。
 *
 * <p>TTL 60s 不是主要的失效手段，写操作提交后会主动 del。
 * 它只在 del 失败或 Redis 短暂不可用时，限制脏数据的存活时间。
 */
public final class DictRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "dict:";

    /** 五张字典的启用项聚合，值是 `DictAllVO` 的 JSON */
    public static final String ALL = MODULE + "all";

    private DictRedisKeys() {
    }
}
