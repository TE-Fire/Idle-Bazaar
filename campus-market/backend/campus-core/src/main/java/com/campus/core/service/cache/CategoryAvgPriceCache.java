package com.campus.core.service.cache;

import com.campus.core.mapper.ItemMapper;
import com.campus.core.redis.ItemRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 各分类已成交均价的共享缓存（{@link ItemMapper#avgPriceByCategory()}）。
 *
 * <p>三个调用方共用：管理端商品列表均价展示（{@code ItemAdminService}）、学生端发布
 * 进队的 price_abnormal 快照（{@code ItemService}）、审核队列回填（{@code ReviewAdminService}）。
 * 该查询是全量 GROUP BY、成本随订单历史增长，必须走缓存；读写异常降级直查库，
 * Redis 抖动不该让页面 500。
 *
 * <p><b>存 Hash 而不是 JSON 字符串</b>：业务语义本身就是「分类 → 均价」的映射，
 * Hash 的 field/value 是它的原生表达。JSON 存 {@code List<Row>} 要把每行包成对象、
 * 每行各带一遍类型信息，读时整包反序列化；Hash 用 {@code HGETALL} 一次取全，
 * 值都是纯字符串，没有中间对象。分类只有十几行、整表替换（从不局部改），
 * 所以写入走「删除 + 重建 + 设 TTL」三步——5 分钟才一次，不在热路径上。
 *
 * <p>空结果用哨兵 field {@code _empty} 表示：Hash 不存在与「算过但没有任何成交」在
 * Redis 里都是空集合，不区分的话没成交时会每次请求都白查一次库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryAvgPriceCache {

    private static final Duration TTL = Duration.ofMinutes(5);

    /** 已计算且无任何成交数据的标记 field；分类 id 从 1 开始，不会与之冲突 */
    private static final String EMPTY_FIELD = "_empty";

    private final ItemMapper itemMapper;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 各分类已成交均价，category_id → avg_price。没成交的分类不出现在结果里，
     * 调用方按「查不到就是没有均价可比」处理。
     */
    public Map<Long, BigDecimal> categoryAvgPrices() {
        Map<Long, BigDecimal> cached = readCache();
        if (cached != null) {
            return cached;
        }
        List<ItemMapper.CategoryAvgRow> rows = itemMapper.avgPriceByCategory();
        Map<Long, BigDecimal> prices = rows.stream()
                .collect(Collectors.toMap(ItemMapper.CategoryAvgRow::getCategoryId,
                        ItemMapper.CategoryAvgRow::getAvgPrice, (a, b) -> a));
        writeCache(prices);
        return prices;
    }

    /** 读缓存；未缓存、读失败都返回 `null` 由调用方查库。空结果由哨兵 field 表达 */
    private Map<Long, BigDecimal> readCache() {
        Map<Object, Object> entries;
        try {
            entries = stringRedisTemplate.opsForHash().entries(ItemRedisKeys.CATEGORY_AVG);
        } catch (Exception e) {
            log.warn("[item] 读均价缓存失败，降级直查库", e);
            return null;
        }
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        if (entries.containsKey(EMPTY_FIELD)) {
            return Map.of();
        }
        Map<Long, BigDecimal> out = new HashMap<>(entries.size());
        entries.forEach((field, value) -> {
            try {
                out.put(Long.valueOf(String.valueOf(field)), new BigDecimal(String.valueOf(value)));
            } catch (NumberFormatException e) {
                // 脏 field（旧结构残留或手改过 key）：跳过该行，不让整张表变 miss
                log.warn("[item] 均价缓存存在无法解析的 field={}，已跳过", field);
            }
        });
        return out;
    }

    /** 整表重建：先删再写，避免不再有成交的分类留下旧均价；写失败不影响本次查库结果 */
    private void writeCache(Map<Long, BigDecimal> prices) {
        Map<String, String> flat = new HashMap<>(prices.size() + 1);
        if (prices.isEmpty()) {
            flat.put(EMPTY_FIELD, "1");
        } else {
            prices.forEach((id, avg) -> flat.put(String.valueOf(id), avg.toPlainString()));
        }
        try {
            stringRedisTemplate.delete(ItemRedisKeys.CATEGORY_AVG);
            stringRedisTemplate.opsForHash().putAll(ItemRedisKeys.CATEGORY_AVG, flat);
            stringRedisTemplate.expire(ItemRedisKeys.CATEGORY_AVG, TTL);
        } catch (Exception e) {
            log.warn("[item] 写均价缓存失败，本次直查结果仍然可用", e);
        }
    }

    /** 成交事实变化（订单完成）后失效；失败只意味着多滞后一个 TTL，不抛 */
    public void evict() {
        try {
            stringRedisTemplate.delete(ItemRedisKeys.CATEGORY_AVG);
        } catch (Exception e) {
            log.warn("[item] 失效均价缓存失败，等待 TTL 自愈", e);
        }
    }
}
