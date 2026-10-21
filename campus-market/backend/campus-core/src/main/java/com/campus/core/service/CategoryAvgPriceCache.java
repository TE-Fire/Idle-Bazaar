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
 * <p>三个调用方共用一份缓存：
 * <ol>
 *   <li>管理端商品列表 / 详情的「分类均价」展示（{@link ItemAdminService}）；</li>
 *   <li>学生端发布进队时的 `price_abnormal` 快照（{@link ItemService#isPriceAbnormal}，
 *       `notes/13` O1）；</li>
 *   <li>审核队列回填（{@link ReviewAdminService}）。</li>
 * </ol>
 *
 * <p><b>为什么这条查询必须缓存</b>：全量 GROUP BY、不随分页与筛选变化，而结果只有
 * 几十行、几乎不变。实测 5 万条已完成订单单次要 380–1000ms，是相关查询里最贵的
 * 一条，加索引也救不了——瓶颈在按 `item_id` 回表取 `category_id`，加候选索引反而
 * 会让优化器换错 JOIN 驱动方向。
 *
 * <p><b>缓存 `List` 而不是 `Map`</b>：JSON 的对象 key 只能是字符串，存
 * `Map&lt;Long, ?&gt;` 取回来 key 会变成 `String`，调用方拿 `Long` 去 `get` 不会
 * 抛错、只会静默返回 `null`（表现为均价全都不显示）。缓存 List 没有这个问题。
 *
 * <p><b>读写异常都降级直查库</b>（与 {@link DictService#all()} 同一口径）：均价只是
 * 展示与提示性质的数字，不该因为它让整页 500。反序列化失败（改过类结构、旧格式对不上）
 * 也走降级。
 *
 * <p>TTL 取 5 分钟：均价是整段历史的分组平均，单笔订单对它的影响可以忽略。三个用途里
 * 唯一涉及写判断的是进队时的 `price_abnormal` 快照（标价超出均价 3 倍才记 1）——均价
 * 晚几分钟刷新不改变判定结果。订单模块已落地，完成订单（status → 4）时主动 DEL
 * `cm:item:catAvg` 是明确的后续项，当前靠 TTL 兜底。
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
