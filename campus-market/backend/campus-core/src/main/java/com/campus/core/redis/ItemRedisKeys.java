package com.campus.core.redis;

/**
 * 商品模块缓存 key。
 *
 * <p>目前只有一个：分类成交均价。它是全量 GROUP BY、不随分页条件变化、结果只有几十行，
 * 却每个列表请求都要跑一次——典型的「算一次存起来」场景。
 *
 * <p>注意缓存的是 `List` 而不是 `Map`：JSON 的对象 key 只能是字符串，
 * 存 `Map&lt;Long, ?&gt;` 取回来会变成 key 为 `String` 的 Map，
 * 调用方拿 `Long` 去 `get` 会静默取不到（返回 null 而不是抛错）。详见 notes/10。
 */
public final class ItemRedisKeys {

    private static final String MODULE = RedisKeys.PREFIX + "item:";

    /** 各分类已成交均价，值是 `List<ItemMapper.CategoryAvgRow>` 的 JSON */
    public static final String CATEGORY_AVG = MODULE + "catAvg";

    /** 商品占位状态的 key 前缀，值是 ON_SALE / LOCKED / SOLD 之一（见 SPECIAL 常量） */
    private static final String STATE_PREFIX = MODULE + "state:";

    /**
     * 占位持有者 Hash：`field` 是上面的 state key，`value` 是抢到锁的订单号。
     * 用独立 Hash 而不是把 holder 拼进 value，是为了让回滚时的校验只读一个 field。
     */
    public static final String HOLDER = MODULE + "holder";

    /** 前置拦截的健康值：只有「在售」才允许被抢占（`tech/01` §9.1 的 Lua 脚本） */
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
