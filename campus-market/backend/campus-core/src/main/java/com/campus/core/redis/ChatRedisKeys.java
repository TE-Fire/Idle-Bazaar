package com.campus.core.redis;

/**
 * 询单留言相关的 key。
 */
public final class ChatRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "chat:";

    /** 实际形态 `cm:chat:unread:{userId}`。Hash：field=sessionId，value=该会话未读条数 */
    public static String buildUnreadKey(Long userId) {
        return MODULE + "unread:" + userId;
    }

    private ChatRedisKeys() {
    }
}
