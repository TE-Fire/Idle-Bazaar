package com.campus.core.redis;

import com.campus.core.enums.MailCodeScene;

import java.util.Locale;

/**
 * 邮箱验证码的 key（business/03 F-A0 注册 / F-A10 找回密码 / F-A1 邮箱验证码登录）。
 *
 * <p>两个 key 是一对：`code` 存 6 位验证码（10 分钟），`throttle` 挡 60 秒内重复发信。
 * 分开存而不是塞一个 hash，是因为两者的 TTL 与语义都不同——节流过期验证码还得有效。
 */
public final class MailRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "mail:";

    /**
     * 6 位邮箱验证码，TTL 10 分钟。实际形态：`cm:mail:code:{scene}:{email}`。
     *
     * <p>**必须带场景**：三场景共用一个 key 会有两个后果——
     * ① 后发的码把先发的冲掉（找回密码拿到码后切去登录页点一下，找回流程就断了）；
     * ② 注册场景的码能拿去重置密码 / 登录，等于把一道校验让了出去。
     *
     * @param scene 业务场景，参与 key 拼装
     * @param email 已规范化的邮箱（小写去空白），非规范化会写出两份 key
     */
    public static String buildCodeKey(MailCodeScene scene, String email) {
        return MODULE + "code:" + scene.name().toLowerCase(Locale.ROOT) + ":" + email;
    }

    /**
     * 发送节流，TTL 60 秒。实际形态：`cm:mail:throttle:{email}`。
     *
     * <p>用 `setIfAbsent` 抢占，抢到才允许发，所以同一邮箱 60 秒内只会真发一封。
     *
     * <p>**刻意不带场景**：发信是有投诉成本的资源，按邮箱而不是按场景节流，
     * 一个邮箱 60 秒内最多收一封（三个场景互相挤占，代价只是提示 4010）。
     */
    public static String buildThrottleKey(String email) {
        return MODULE + "throttle:" + email;
    }

    private MailRedisKeys() {
    }
}
