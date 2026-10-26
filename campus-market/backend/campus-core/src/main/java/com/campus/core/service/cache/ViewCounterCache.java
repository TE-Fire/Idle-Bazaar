package com.campus.core.service.cache;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.core.entity.Item;
import com.campus.core.mapper.ItemMapper;
import com.campus.core.redis.ItemRedisKeys;
import com.campus.core.redis.LuaScripts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览量增量聚合：浏览只做 {@code HINCRBY}，定时批量写回 {@code item.view_count}。
 *
 * <p>商品详情是最高频的读接口，原来每看一次就 {@code UPDATE item SET view_count = view_count + 1}——
 * 同一商品被并发浏览时争同一行锁，而浏览量本身是弱一致统计，没有必要实时落库。
 *
 * <p><b>聚合点为什么放在 Redis 而不是进程内存</b>（BufferTrigger 那类组件）：多实例部署时
 * 进程内聚合会退化成「每实例各攒一份」，聚合度随机器数衰减，Redis 才是天然的全局限量点；
 * 且详情页要回显浏览量，{@code HINCRBY} 一条命令就返回累加后的增量，进程内聚合读不到
 * 尚未 flush 的那部分，数字会滞后一拍。
 *
 * <p>取走用 Lua 把 {@code HGETALL} 与 {@code DEL} 原子化：分两条命令的话，取到与删除之间
 * 新增的浏览会被一并删掉。落库失败的条目把增量 {@code HINCRBY} 回去，下一轮重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewCounterCache {

    /** 落库间隔：弱一致统计，5 分钟足够；太短则批量失去意义，太长则进程崩溃时丢得多 */
    private static final long FLUSH_INTERVAL_MS = 5 * 60 * 1000L;

    private static final RedisScript<List> FLUSH_SCRIPT = LuaScripts.load("lua/view_flush.lua", List.class);

    private final StringRedisTemplate stringRedisTemplate;

    private final ItemMapper itemMapper;

    /**
     * 浏览 +1，返回该商品在库值之上累计的增量（含尚未落库的部分），调用方用它拼展示值。
     * Redis 不可用时退回直接写库——宁可慢一点，也别让这次浏览凭空消失。
     */
    public long incr(Long itemId) {
        try {
            Long total = stringRedisTemplate.opsForHash()
                    .increment(ItemRedisKeys.VIEW, String.valueOf(itemId), 1);
            return total == null ? 1L : total;
        } catch (Exception e) {
            log.warn("[item] 浏览量聚合失败，退回直接写库：itemId={}", itemId, e);
            itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                    .eq(Item::getId, itemId)
                    .setSql("view_count = view_count + 1"));
            return 1L;
        }
    }

    /** 批量取增量（列表回显用）；Redis 不可用返回空 Map，调用方按库值展示 */
    public Map<Long, Long> deltas(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        List<Object> fields = itemIds.stream().map(id -> (Object) String.valueOf(id)).toList();
        List<Object> values;
        try {
            values = stringRedisTemplate.opsForHash().multiGet(ItemRedisKeys.VIEW, fields);
        } catch (Exception e) {
            log.warn("[item] 读浏览量增量失败，列表按库值展示", e);
            return Map.of();
        }
        Map<Long, Long> out = new HashMap<>(itemIds.size());
        if (values == null) {
            return out;
        }
        int i = 0;
        for (Long id : itemIds) {
            Object v = values.get(i++);
            if (v == null) {
                continue;
            }
            try {
                long n = Long.parseLong(String.valueOf(v));
                if (n > 0) {
                    out.put(id, n);
                }
            } catch (NumberFormatException e) {
                // 脏 field 只影响它自己，跳过即可
                log.warn("[item] 浏览量增量存在无法解析的值，已跳过：itemId={}", id);
            }
        }
        return out;
    }

    /** 定时把增量落库。原子取走，多实例并发时同一批增量只会被一个实例拿到 */
    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS)
    public void flush() {
        List<?> flat;
        try {
            flat = stringRedisTemplate.execute(FLUSH_SCRIPT, List.of(ItemRedisKeys.VIEW));
        } catch (Exception e) {
            log.warn("[item] 取浏览量增量失败，等待下一轮", e);
            return;
        }
        if (flat == null || flat.isEmpty()) {
            return;
        }
        int ok = 0;
        int failed = 0;
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            long itemId;
            long delta;
            try {
                itemId = Long.parseLong(String.valueOf(flat.get(i)));
                delta = Long.parseLong(String.valueOf(flat.get(i + 1)));
            } catch (NumberFormatException e) {
                log.warn("[item] 浏览量增量存在脏 field，已跳过：{}", flat.get(i));
                continue;
            }
            try {
                itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                        .eq(Item::getId, itemId)
                        .setSql("view_count = view_count + " + delta));
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("[item] 浏览量落库失败，增量退回下轮重试：itemId={} delta={}", itemId, delta, e);
                try {
                    stringRedisTemplate.opsForHash()
                            .increment(ItemRedisKeys.VIEW, String.valueOf(itemId), delta);
                } catch (Exception again) {
                    log.error("[item] 浏览量增量退回失败，这 {} 次浏览已丢：itemId={}", delta, itemId, again);
                }
            }
        }
        log.info("[item] 浏览量落库完成：成功 {} 条，失败 {} 条", ok, failed);
    }
}
