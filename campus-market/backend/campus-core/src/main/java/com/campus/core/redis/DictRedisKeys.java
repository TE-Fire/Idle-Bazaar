package com.campus.core.redis;

/**
 * 字典缓存 key。整个模块只有一个：`cm:dict:all`。
 *
 * <p>为什么只缓存这一个：字典的读其实分两种，语义不同，**不能共用一个 key**——
 * <ul>
 *   <li>管理端 `/admin/dict/**`：要看到**停用项**、要「改完立刻看到新值」，而且只有超管在点，
 *       缓存没有收益，只会造成「改完列表没变」的假象 → **直查库**；</li>
 *   <li>学生端 `/api/dict/all`：只下发**启用项**，将来被发布页 / 筛选高频调用 → 走缓存。</li>
 * </ul>
 * 既然只有后者需要缓存，就不必给五张表各建一个 key。
 *
 * <p>TTL 60s 的定位是**兜底**而不是主手段：正常情况下写操作提交后会主动 del 失效，
 * TTL 只在「del 失败 / Redis 正好抖了一下」时兜住，最坏 60 秒自愈。
 */
public final class DictRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "dict:";

    /** 五张字典的启用项聚合，值是 `DictAllVO` 的 JSON */
    public static final String ALL = MODULE + "all";

    private DictRedisKeys() {
    }
}
