package com.campus.core.redis;

import com.campus.core.enums.MailCodeSceneEnum;

import java.util.Locale;

/**
 * 邮箱验证码的 key。两个 key 是一对：`code` 存 6 位验证码（10 分钟），`throttle` 挡 60 秒重复发信——
 * 分开存是因为 TTL 与语义都不同，节流过期时验证码还得有效。
 */
public final class MailRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "mail:";

    /**
     * 实际形态 `cm:mail:code:{scene}:{email}`。**必须带场景**：共用一个 key 会导致
     * ① 后发的码冲掉先发的（找回拿到码后切去登录页点一下，找回流程就断了）；
     * ② 注册的码能拿去重置密码 / 登录。
     *
     * @param email 已规范化的邮箱（小写去空白），非规范化会写出两份 key
     */
    public static String buildCodeKey(MailCodeSceneEnum scene, String email) {
        return MODULE + "code:" + scene.name().toLowerCase(Locale.ROOT) + ":" + email;
    }

    /**
     * 实际形态 `cm:mail:throttle:{email}`。用 `setIfAbsent` 抢占，抢到才允许发。
     * **刻意不带场景**：发信有投诉成本，按邮箱节流，一个邮箱 60 秒内最多收一封。
     */
    public static String buildThrottleKey(String email) {
        return MODULE + "throttle:" + email;
    }

    private MailRedisKeys() {
    }
}
