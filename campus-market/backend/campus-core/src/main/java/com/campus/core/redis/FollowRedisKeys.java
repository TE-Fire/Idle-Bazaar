package com.campus.core.redis;

/**
 * 关注模块缓存 key（方案见 notes/23）。
 *
 * <p>两个 ZSET 互为镜像：`following:{A}` 的 member 是 A 关注的人，`followers:{B}` 的 member
 * 是关注 B 的人；score 统一是关系建立时间（毫秒），用于倒序分页。
 *
 * <p>`loaded:*` 是「该集合已从库加载过」的标记。没有它就无法区分「冷启动、集合为空」
 * 与「真的没有关注」——空 ZSET 两种情况长得一样，会导致每次都白查库或读不到历史关系。
 * 标记不设 TTL：过期后重建会把尚未刷库的增量冲掉，宁可常驻、由显式重建收敛。
 */
public final class FollowRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "follow:";

    /** 我关注的人，ZSET：member=followeeId，score=关注时间（毫秒） */
    public static String following(Long userId) {
        return MODULE + "following:" + userId;
    }

    /** 我的粉丝，ZSET：member=followerId，score=关注时间（毫秒） */
    public static String followers(Long userId) {
        return MODULE + "followers:" + userId;
    }

    /** 关注集合已加载标记 */
    public static String loadedFollowing(Long userId) {
        return MODULE + "loaded:following:" + userId;
    }

    /** 粉丝集合已加载标记 */
    public static String loadedFollowers(Long userId) {
        return MODULE + "loaded:fans:" + userId;
    }

    /**
     * 关注关系写缓冲，Hash：field=`{followerId}:{followeeId}`，value=`follow|毫秒` 或 `unfollow|毫秒`。
     *
     * <p>field 以关系对为键，连续操作 HSET 覆盖（last-write-wins），刷库前自动合并，
     * 连点「关注→取关→关注」最终只落最后一条。
     */
    public static final String PENDING = MODULE + "pending";

    private FollowRedisKeys() {
    }
}
