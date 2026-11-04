package com.campus.core.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

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

    private static final Logger log = LoggerFactory.getLogger(ItemRedisKeys.class);

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

    /**
     * 商品变为在售时在 Redis 落占位标记（ON_SALE）。使并发首单也能经 occupy.lua 的
     * WON→LOCKED 前置拦截，而不必全靠 DB 的 CAS 兜底（后者对并发输家提示更糟、且多打一次 DB）。
     * 带 TTL：过期后 key 消失，occupy 降级为 MISS 放行、DB 仍保证唯一赢家，不影响正确性。
     * Redis 不可用时静默失败——占位标记只是前置优化，缺失只让首单走 DB 兜底。
     */
    public static void markOnSale(StringRedisTemplate rt, Long itemId) {
        try {
            rt.opsForValue().set(buildStateKey(itemId), STATE_ON_SALE, Duration.ofSeconds(STATE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("[item] 上架占位标记写入失败，首单将走 DB 兜底，itemId={}", itemId, e);
        }
    }

    /**
     * 读时回填：详情读到在售且 Redis 无占位时才补写 ON_SALE（SET NX）。
     *
     * <p>不覆盖已有值——已有的 LOCKED / SOLD 比「读到在售」更权威，覆盖会破坏正在进行的占位。
     * 只补缺失，用来把快速通道的有效期从「发布后 60s」延长到「每次被打开详情后的 60s」。
     * 失败仅降级为 DB 兜底，故用 debug，避免读路径刷日志。
     */
    public static void armOnSaleIfAbsent(StringRedisTemplate rt, Long itemId) {
        try {
            rt.opsForValue().setIfAbsent(buildStateKey(itemId), STATE_ON_SALE, Duration.ofSeconds(STATE_TTL_SECONDS));
        } catch (Exception e) {
            log.debug("[item] 读时回填占位失败（可忽略），itemId={}", itemId, e);
        }
    }

    private ItemRedisKeys() {
    }
}
