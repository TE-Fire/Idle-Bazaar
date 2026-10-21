package com.campus.core.service;

import com.campus.core.mapper.ItemMapper;
import com.campus.core.redis.ItemRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 各分类已成交均价的共享缓存（{@link ItemMapper#avgPriceByCategory()}）。
 *
 * <p>三个调用方共用：管理端商品列表均价展示（{@link ItemAdminService}）、学生端发布
 * 进队的 price_abnormal 快照（{@link ItemService}）、审核队列回填（{@link ReviewAdminService}）。
 * 该查询是全量 GROUP BY、成本随订单历史增长，必须走缓存；读写异常降级直查库，
 * Redis 抖动不该让页面 500。
 *
 * <p>缓存 List 不存 Map：JSON 对象 key 会变字符串，Long 取 key 静默返回 null。
 * TTL 5 分钟：均价是慢变量，进队判「3 倍」不受滞后影响；订单完成时 DEL 是后续项。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryAvgPriceCache {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final ItemMapper itemMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 各分类已成交均价，category_id → avg_price。没成交的分类不出现在结果里，
     * 调用方按「查不到就是没有均价可比」处理。
     */
    public Map<Long, BigDecimal> categoryAvgPrices() {
        List<ItemMapper.CategoryAvgRow> rows = readCache();
        if (rows == null) {
            rows = itemMapper.avgPriceByCategory();
            writeCache(rows);
        }
        return rows.stream().collect(Collectors.toMap(ItemMapper.CategoryAvgRow::getCategoryId,
                ItemMapper.CategoryAvgRow::getAvgPrice, (a, b) -> a));
    }

    /** 读缓存；未命中、类型不符或读失败都返回 `null`，调用方统一走查库 */
    private List<ItemMapper.CategoryAvgRow> readCache() {
        Object cached;
        try {
            cached = redisTemplate.opsForValue().get(ItemRedisKeys.CATEGORY_AVG);
        } catch (Exception e) {
            log.warn("[item] 读均价缓存失败，降级直查库", e);
            return null;
        }
        if (!(cached instanceof List<?> list)) {
            return null;
        }
        // 空列表是合法结果（没有任何已成交订单），要当成命中，否则每次都白查一次库
        if (list.isEmpty()) {
            return List.of();
        }
        if (!(list.get(0) instanceof ItemMapper.CategoryAvgRow)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<ItemMapper.CategoryAvgRow> rows = (List<ItemMapper.CategoryAvgRow>) list;
        return rows;
    }

    /** 写缓存失败不影响本次返回：查库的结果已经拿到了 */
    private void writeCache(List<ItemMapper.CategoryAvgRow> rows) {
        try {
            redisTemplate.opsForValue().set(ItemRedisKeys.CATEGORY_AVG, rows, TTL);
        } catch (Exception e) {
            log.warn("[item] 写均价缓存失败，本次直查结果仍然可用", e);
        }
    }
}
