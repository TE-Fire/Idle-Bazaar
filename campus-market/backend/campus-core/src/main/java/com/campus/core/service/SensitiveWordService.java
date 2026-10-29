package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BizException;
import com.campus.core.entity.SensitiveWord;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.SensitiveWordMapper;
import com.campus.core.redis.SensitiveWordRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 敏感词过滤（notes/22 §四 审核三态；词库 `sys_sensitive_word`）。
 *
 * <p>高危（level=1）命中直接抛 {@link UserErrorCode#SENSITIVE_WORD_HIT}，不入库；
 * 低危（level=2）替换成等长 `*` 后返回，调用方按返回值落库。
 *
 * <p>词库整表载入内存：过滤在发布/评论热路径上，逐次查库不划算。多实例各持一份副本，
 * 改词后靠 `cm:sensitive:ver` 版本戳失效——{@link #refresh()} 自增该键，各实例最多
 * {@link #VER_CHECK_MS} 毫秒后重载。Redis 不可用时退回本地 TTL 兜底，过滤本身不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    /** 本地缓存兜底有效期：只在 Redis 不可用时起作用，正常路径靠版本戳失效 */
    private static final long TTL_MS = 5 * 60 * 1000L;

    /** 版本戳的本地核对间隔：改词后最多这么久生效，又不会每个请求都打一次 Redis */
    private static final long VER_CHECK_MS = 3_000L;

    private final SensitiveWordMapper sensitiveWordMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private volatile Snapshot snapshot;

    /** 最近一次核对版本号的时刻；置 0 表示下一次过滤立即重新核对 */
    private volatile long lastCheckAt;

    /** 高危命中抛异常；低危替换成等长 `*`；返回清洗后的文本 */
    public String filter(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        Snapshot s = snapshot();
        for (String w : s.high()) {
            if (text.contains(w)) {
                throw new BizException(UserErrorCode.SENSITIVE_WORD_HIT);
            }
        }
        String out = text;
        for (String w : s.low()) {
            if (out.contains(w)) {
                out = out.replace(w, "*".repeat(w.length()));
            }
        }
        return out;
    }

    /**
     * 词库变更后调用：自增版本号，所有实例（含本实例）在下一次过滤时重载。
     *
     * <p>本实例把 {@code lastCheckAt} 清零，调用方改完词立刻验证能看到新词，不必等核对间隔。
     * 自增失败只 warn——多实例下其余实例仍会按 TTL 逐步过期，不能因为 Redis 抖动让改词操作报错。
     */
    public void refresh() {
        lastCheckAt = 0;
        try {
            stringRedisTemplate.opsForValue().increment(SensitiveWordRedisKeys.VERSION);
        } catch (RuntimeException e) {
            log.warn("敏感词版本号自增失败，改词需等本地缓存过期", e);
        }
    }

    private Snapshot snapshot() {
        long now = System.currentTimeMillis();
        Snapshot s = snapshot;
        if (s != null && now - lastCheckAt < VER_CHECK_MS) {
            return s;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            Snapshot cur = snapshot;
            if (cur != null && now - lastCheckAt < VER_CHECK_MS) {
                return cur;
            }
            String ver = version();
            lastCheckAt = now;
            // 首次加载 / TTL 兜底过期 / 版本号变了，三者任一即重载
            if (cur == null || now - cur.loadedAt() >= TTL_MS
                    || (ver != null && !ver.equals(cur.ver()))) {
                snapshot = load(ver);
            }
            return snapshot;
        }
    }

    /** 读版本号；Redis 不可用时返回 null，此时只能靠 TTL 过期，不阻断过滤 */
    private String version() {
        try {
            return stringRedisTemplate.opsForValue().get(SensitiveWordRedisKeys.VERSION);
        } catch (RuntimeException e) {
            log.warn("敏感词版本号读取失败，沿用手头缓存", e);
            return null;
        }
    }

    private Snapshot load(String ver) {
        List<SensitiveWord> rows = sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getStatus, 1));
        Set<String> high = new HashSet<>();
        List<String> low = new ArrayList<>();
        for (SensitiveWord w : rows) {
            if (w.getWord() == null || w.getWord().isBlank()) {
                continue;
            }
            if (Integer.valueOf(1).equals(w.getLevel())) {
                high.add(w.getWord());
            } else {
                low.add(w.getWord());
            }
        }
        return new Snapshot(high, low, System.currentTimeMillis(), ver);
    }

    private record Snapshot(Set<String> high, List<String> low, long loadedAt, String ver) {
    }
}
