package com.campus.core.redis;

/**
 * 商品模块缓存 key。
 *
 * <p>注意均价缓存存的是 `Hash` 而不是 JSON 字符串：语义是「分类 → 均价」的映射，
 * field 直接就是分类 id（字符串形态），省掉行对象包装与整包反序列化。
 * 早先存 `List&lt;Row&gt;` 是因为 JSON 的对象 key 只能为字符串，
 * 存 `Map&lt;Long, ?&gt;` 取回来 key 变成 `String`、拿 `Long` 去 `get` 会静默返回 null
 * （见 notes/10）；Hash 由 field 承担 Long→String 的转换，没有这个问题。
 */
public final class ItemRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "item:";

    /** 各分类已成交均价，Hash：field = categoryId，value = 均价（十进制字符串） */
    public static final String CATEGORY_AVG = MODULE + "catAvg";

    /**
     * 浏览量增量聚合，Hash：field = itemId，value = 尚未落库的浏览次数。
     * 定时任务用 {@code lua/view_flush.lua} 原子取走后批量写回 {@code item.view_count}。
     */
    public static final String VIEW = MODULE + "view";

    /** 商品占位状态的 key 前缀，值是 ON_SALE / LOCKED / SOLD 之一（见 SPECIAL 常量） */
    private static final String STATE_PREFIX = MODULE + "state:";

    /**
     * 占位持有者 Hash：`field` 是上面的 state key，`value` 是抢到锁的订单号。
     * 用独立 Hash 而不是把 holder 拼进 value，是为了让回滚时的校验只读一个 field。
     */
    public static final String HOLDER = MODULE + "holder";

    /** 前置拦截的健康值：只有「在售」才允许被抢占（`lua/occupy.lua`，口径见 tech/01 §9.1） */
    public static final String STATE_ON_SALE = "ON_SALE";

    public static final String STATE_LOCKED = "LOCKED";

    public static final String STATE_SOLD = "SOLD";

    /** 占位标记的 TTL，60 秒。它只覆盖「抢到标记 → DB 提交」这段窗口，不是订单超时时长 */
    public static final long STATE_TTL_SECONDS = 60L;

    public static String buildStateKey(Long itemId) {
        return STATE_PREFIX + itemId;
    }

    private ItemRedisKeys() {
    }
}
