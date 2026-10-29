package com.campus.core.redis;

/**
 * 动态列表缓存 key（方案见 notes/18 问题 4）。
 *
 * <p>列表 key 里带版本戳 `feed:ver`：内容下架时只 INCR 一次，所有列表 key 同时逻辑失效，
 * 不必 SCAN 枚举 —— 旧 key 交给 TTL 自然回收。
 */
public final class MomentRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "moment:";

    /** 列表正确性版本戳，String 类型（只能走 StringRedisTemplate，见 MomentCache 类注释） */
    public static final String FEED_VER = MODULE + "feed:ver";

    /** 广场流某一页，全站用户共享同一份 */
    public static String squarePage(long ver, int pageNum, int pageSize) {
        return MODULE + "feed:square:v" + ver + ":p" + pageNum + ":s" + pageSize;
    }

    /** 某作者主页某一页，按作者共享 */
    public static String userPage(long ver, long userId, int pageNum, int pageSize) {
        return MODULE + "feed:user:v" + ver + ":" + userId + ":p" + pageNum + ":s" + pageSize;
    }

    /** 重建互斥锁：抢到的实例回源，抢不到的短暂重读 */
    public static String rebuildLock(String key) {
        return key + ":lock";
    }

    private MomentRedisKeys() {
    }
}
