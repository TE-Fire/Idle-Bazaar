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

    private ItemRedisKeys() {
    }
}
