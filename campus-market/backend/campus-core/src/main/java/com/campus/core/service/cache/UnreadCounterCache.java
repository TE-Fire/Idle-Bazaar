package com.campus.core.service.cache;

import com.campus.core.redis.ChatRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 会话未读计数缓存（{@code cm:chat:unread:{userId}}），供 {@code ChatService} 的列表、发送、已读三条路径共用。
 *
 * <p>改造前每次会话列表都要把该用户的未读消息行全扫一遍再 Java 计数（`idx_receiver` 兜着索引，
 * 但扫描量随未读消息条数增长）。这里存 Hash 是因为业务语义就是「会话 → 条数」的映射：
 * 读一次 {@code HGETALL}、收消息一条 {@code HINCRBY}、进会话一条 {@code HDEL}。
 *
 * <p><b>DB 是真相源，缓存整份可重建</b>。Hash 用哨兵 field {@code _init} 标记已初始化，
 * 缺哨兵即视为未初始化、从库重算覆盖——否则「Hash 不存在」与「全部会话都 0 未读」在 Redis 里
 * 都是空集合，无法区分。TTL 设 1 小时且写路径不续期：既是兜底自愈（写失败或重建期
 * 并发覆盖造成的偏差最迟一小时后自动纠正），也避免活跃用户永不重建导致偏差永久留存。
 * 计数器不做 TTL 会省掉一次重建，但偏差就没有任何自愈路径，不划算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnreadCounterCache {

    private static final Duration TTL = Duration.ofHours(1);

    /** 已初始化标记 field；sessionId 是雪花 ID，不会与之冲突 */
    private static final String INIT_FIELD = "_init";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 取指定会话的未读数。命中缓存直接过滤返回；未命中回源 {@code dbLoader} 并重建整份。
     * Redis 不可用时同样回源，只跳过重建——缓存挂了不该让会话列表 500。
     */
    public Map<Long, Integer> unreadOf(Long userId, Collection<Long> sessionIds,
                                       Supplier<Map<Long, Integer>> dbLoader) {
        if (sessionIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> cached = readCache(userId);
        if (cached != null) {
            return filter(cached, sessionIds);
        }
        Map<Long, Integer> fresh = dbLoader.get();
        rebuild(userId, fresh);
        return filter(fresh, sessionIds);
    }

    /** 收到新消息：该会话未读 +1。挂在事务提交后执行，回滚不该留下脏计数 */
    public void incrAfterCommit(Long userId, Long sessionId) {
        afterCommit(() -> {
            try {
                stringRedisTemplate.opsForHash()
                        .increment(ChatRedisKeys.buildUnreadKey(userId), String.valueOf(sessionId), 1);
            } catch (Exception e) {
                log.warn("[chat] 累加未读缓存失败，删除 key 待下次重建：user={} session={}", userId, sessionId, e);
                evict(userId);
            }
        });
    }

    /** 会话已读：该会话计数清零。同样等提交后再改缓存，否则回滚会让未读凭空消失 */
    public void clearAfterCommit(Long userId, Long sessionId) {
        afterCommit(() -> {
            try {
                stringRedisTemplate.opsForHash()
                        .delete(ChatRedisKeys.buildUnreadKey(userId), String.valueOf(sessionId));
            } catch (Exception e) {
                log.warn("[chat] 清除未读缓存失败，删除 key 待下次重建：user={} session={}", userId, sessionId, e);
                evict(userId);
            }
        });
    }

    /** 读缓存；未初始化、读失败都返回 `null`，由调用方回源 */
    private Map<Long, Integer> readCache(Long userId) {
        Map<Object, Object> entries;
        try {
            entries = stringRedisTemplate.opsForHash().entries(ChatRedisKeys.buildUnreadKey(userId));
        } catch (Exception e) {
            log.warn("[chat] 读未读缓存失败，降级直查库", e);
            return null;
        }
        if (entries == null || !entries.containsKey(INIT_FIELD)) {
            return null;
        }
        Map<Long, Integer> out = new HashMap<>(entries.size());
        entries.forEach((field, value) -> {
            if (INIT_FIELD.equals(String.valueOf(field))) {
                return;
            }
            try {
                out.put(Long.valueOf(String.valueOf(field)), Integer.valueOf(String.valueOf(value)));
            } catch (NumberFormatException e) {
                // 脏 field（旧结构残留或手改过 key）：跳过该行，不让整份缓存变 miss
                log.warn("[chat] 未读缓存存在无法解析的 field={}，已跳过", field);
            }
        });
        return out;
    }

    /**
     * 整份重建：先删再写，清掉已不存在会话的残留 field。
     *
     * <p>重建窗口内若有并发 {@code HINCRBY}，增量会被这次覆盖盖掉——但 DB 里的未读行是
     * 提交后才累加的，窗口后紧接着的列表请求若仍落在这个快照上，最多少显示一条，
     * 一小时后 TTL 到期重建即纠正。
     */
    private void rebuild(Long userId, Map<Long, Integer> counts) {
        Map<String, String> flat = new HashMap<>(counts.size() + 1);
        counts.forEach((sessionId, n) -> {
            if (n != null && n > 0) {
                flat.put(String.valueOf(sessionId), String.valueOf(n));
            }
        });
        flat.put(INIT_FIELD, "1");
        try {
            stringRedisTemplate.delete(ChatRedisKeys.buildUnreadKey(userId));
            stringRedisTemplate.opsForHash().putAll(ChatRedisKeys.buildUnreadKey(userId), flat);
            stringRedisTemplate.expire(ChatRedisKeys.buildUnreadKey(userId), TTL);
        } catch (Exception e) {
            log.warn("[chat] 重建未读缓存失败，本次直查结果仍然可用", e);
        }
    }

    /** 只保留调用方关心的会话：Hash 里可能残留已删会话的 field */
    private Map<Long, Integer> filter(Map<Long, Integer> raw, Collection<Long> sessionIds) {
        Map<Long, Integer> out = new HashMap<>(sessionIds.size());
        for (Long id : sessionIds) {
            Integer n = raw.get(id);
            if (n != null && n > 0) {
                out.put(id, n);
            }
        }
        return out;
    }

    private void evict(Long userId) {
        try {
            stringRedisTemplate.delete(ChatRedisKeys.buildUnreadKey(userId));
        } catch (Exception e) {
            log.warn("[chat] 删除未读缓存失败，等待 TTL 或重启自愈：user={}", userId, e);
        }
    }

    private void afterCommit(Runnable work) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
            return;
        }
        work.run();
    }
}
