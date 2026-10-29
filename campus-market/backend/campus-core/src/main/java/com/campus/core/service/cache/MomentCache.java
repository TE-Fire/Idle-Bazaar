package com.campus.core.service.cache;

import com.campus.core.redis.MomentRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 动态列表缓存：读穿 + 互斥重建 + 版本戳失效（notes/18 问题 4）。
 *
 * <p>只缓存「不随 viewer 变化」的视图。`liked` 在缓存里恒为 false，命中后由调用方按
 * 当前用户补一次；作者本人的视角（能看到被屏蔽内容）不进缓存 —— 这类差异一旦烤进
 * 共享缓存，要么泄漏内容，要么把第一个访问者的状态发给所有人。
 *
 * <p>TTL 一律「保底 + 随机偏移」，见 {@link #ttl}：同批写入的 key 不在同一刻过期。
 *
 * <p>版本戳走 {@link StringRedisTemplate} 而不是 {@link RedisTemplate}：后者的 value
 * 序列化器带 {@code @class}，`increment` 会把计数器写成 JSON、之后再自增直接报错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MomentCache {

    /** 版本戳读不到时返回：调用方据此绕过缓存直查库 */
    public static final long NO_VERSION = -1L;

    /** 重建锁 3 秒：只覆盖一次回源，宁可提前释放多回源一次，也不让请求排在死锁上 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    /** 没抢到锁的重读策略：3 次 × 30ms，最长等 90ms，超过就自己查库 */
    private static final int WAIT_ROUNDS = 3;
    private static final long WAIT_MILLIS = 30L;

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 保底时间 + [0, jitter] 随机偏移。
     *
     * <p>jitter 取 base 的 50%–100%：太小打不散同批写入的 key，太大会让实际寿命翻倍，
     * 内容下架后残留时间不可控。
     */
    public static Duration ttl(long baseSeconds, long jitterSeconds) {
        return Duration.ofSeconds(baseSeconds + ThreadLocalRandom.current().nextLong(jitterSeconds + 1));
    }

    /** 当前列表版本；Redis 异常返回 {@link #NO_VERSION}，本次读写都绕开缓存 */
    public long feedVersion() {
        try {
            String v = stringRedisTemplate.opsForValue().get(MomentRedisKeys.FEED_VER);
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("[moment] 读列表版本戳失败，本次跳过缓存", e);
            return NO_VERSION;
        }
    }

    /** 内容下架后调用：所有列表 key 逻辑失效，旧 key 靠 TTL 回收 */
    public void invalidateFeeds() {
        try {
            stringRedisTemplate.opsForValue().increment(MomentRedisKeys.FEED_VER);
        } catch (Exception e) {
            log.warn("[moment] 列表版本戳自增失败，下架内容可能残留至 TTL 过期", e);
        }
    }

    /**
     * 读穿：命中直接返回；未命中时只放一个实例回源，其余短暂重读，仍为空则自己查库。
     *
     * <p>不排队等锁：让少量请求穿透，好过让全部请求卡在等锁上。
     * Redis 整体不可用时（拿不到锁）直接回源，不在缓存上耗时间。
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        Boolean locked = tryLock(key);
        if (locked == null) {
            return loader.get();
        }
        if (locked) {
            try {
                T loaded = loader.get();
                if (loaded != null) {
                    put(key, loaded, ttl);
                }
                return loaded;
            } finally {
                unlock(key);
            }
        }
        for (int i = 0; i < WAIT_ROUNDS; i++) {
            sleepQuietly();
            T retry = get(key, type);
            if (retry != null) {
                return retry;
            }
        }
        return loader.get();
    }

    /** 读失败或类型不符都当未命中 —— 缓存不可用不该让列表页 500 */
    private <T> T get(String key, Class<T> type) {
        try {
            Object v = redisTemplate.opsForValue().get(key);
            return type.isInstance(v) ? type.cast(v) : null;
        } catch (Exception e) {
            log.warn("[moment] 读列表缓存失败，降级直查库", e);
            return null;
        }
    }

    private void put(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            log.warn("[moment] 写列表缓存失败，本次结果仍然可用", e);
        }
    }

    /** true 抢到 / false 被别人占着 / null Redis 不可用（三态，null 时调用方直接回源） */
    private Boolean tryLock(String key) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(MomentRedisKeys.rebuildLock(key), "1", LOCK_TTL));
        } catch (Exception e) {
            log.warn("[moment] 重建锁不可用，直查库", e);
            return null;
        }
    }

    private void unlock(String key) {
        try {
            stringRedisTemplate.delete(MomentRedisKeys.rebuildLock(key));
        } catch (Exception e) {
            log.warn("[moment] 释放重建锁失败，锁将在 {} 秒后自行过期", LOCK_TTL.toSeconds());
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
