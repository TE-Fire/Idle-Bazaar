package com.campus.core.redis;

/**
 * 通知去重 key，形态 `cm:notice:{event}`，值为固定 "1"，只靠 TTL 过期。
 *
 * <p>去重而非节流：同一事件（同一人赞同一条动态、同一人关注同一人）只推一次通知，
 * 避免取关再关注、取消赞再赞把信箱刷成流水账。
 */
public final class NoticeRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "notice:";

    public static String eventKey(String event) {
        return MODULE + event;
    }

    private NoticeRedisKeys() {
    }
}
