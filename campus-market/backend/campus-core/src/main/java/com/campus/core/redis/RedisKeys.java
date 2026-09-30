package com.campus.core.redis;

/**
 * Redis key 的顶层定义：**全项目 key 统一是 `cm:{模块}:{业务}:{标识}` 这个形状**。
 *
 * <p>项目前缀只在这里定义一次。将来多环境共用一套 Redis（或要换成 `campus:`）
 * 时只改这一个常量，不用翻业务代码——这是把 key 收敛到常量类的首要理由。
 *
 * <p>各模块自己提供 build 方法，见同包 {@link CaptchaRedisKeys}、{@link MailRedisKeys}：
 * key 拼装收进方法之后，调用方写不出「漏冒号」「层级顺序写反」「email 忘了转小写」
 * 这类错——它们不是编译错误，等到线上才发现。
 *
 * <p>**新增模块时**：在本包加一个 `XxxRedisKeys`，不要往已有的类里塞。
 * core 被管理端与用户端共用，一个类塞几十个 key 之后，两个人改同一个文件必冲突。
 *
 * <p>**命名规范**：拼 key 的方法一律是 `buildXxxKey(...)`——动词开头表明它每次都会
 * 拼出一个新字符串（而不是读某个已有字段），后缀 `Key` 让调用处一眼看出这不是业务方法。
 */
public final class RedisKeys {

    /** 项目前缀。用户端（小程序 / H5）接入后共用它，不另起前缀 */
    public static final String PREFIX = "cm:";

    private RedisKeys() {
    }
}
