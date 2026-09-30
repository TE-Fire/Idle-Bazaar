package com.campus.core.redis;

/**
 * Redis key 的顶层定义：全项目 key 统一是 `cm:{模块}:{业务}:{标识}`。
 *
 * <p>前缀只在这里定义一次，将来换前缀或共用一套 Redis 时只改这一个常量。
 * 各模块自己提供 build 方法（见 {@link CaptchaRedisKeys}、{@link MailRedisKeys}）——
 * 拼装收进方法后，调用方写不出「漏冒号」「层级写反」「email 忘了转小写」这类
 * 不是编译错误、只能等线上才发现的错。
 *
 * <p>新增模块在本包加一个 `XxxRedisKeys`，不要往已有的类里塞（core 被管理端与用户端共用，
 * 一个类塞几十个 key 后两人改同一文件必冲突）。
 * 方法一律命名 `buildXxxKey(...)`：动词开头表明每次拼出新字符串，后缀 `Key` 表明不是业务方法。
 */
public final class RedisKeys {

    /** 项目前缀。用户端（小程序 / H5）接入后共用它，不另起前缀 */
    public static final String PREFIX = "cm:";

    private RedisKeys() {
    }
}
