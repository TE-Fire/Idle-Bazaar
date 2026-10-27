package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.resp.FollowListVO;
import com.campus.core.dto.resp.FollowUserVO;
import com.campus.core.dto.resp.UserPubVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.UserFollow;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.UserFollowMapper;
import com.campus.core.redis.FollowRedisKeys;
import com.campus.core.redis.LuaScripts;
import com.campus.core.service.cache.UserPubCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;

/**
 * 关注关系：关注 / 取关 / 是否关注 / 关注与粉丝列表（notes/22 S1·S2，方案见 notes/23）。
 *
 * <p><b>Redis 是热状态与写缓冲，MySQL 是真相源。</b> 关注/取关先以 Lua 原子改两个 ZSET
 * 并写入 pending，接口立即返回；定时任务每 5 秒批量刷库。读优先走 ZSET，未加载时回源库。
 *
 * <p><b>幂等</b>：Lua 里先 {@code ZSCORE} 判存在，重复关注/取关直接返回 0，不进 pending；
 * DB 侧再由 {@code uk(follower_id, followee_id, deleted)} 兜底。pending 以关系对为 field，
 * 连点只保留最后一条。
 *
 * <p><b>冷启动</b>：两个集合各有 `loaded:*` 标记，首次访问按库重建（先 DEL 再 ZADD），
 * 之后才信任 ZSET。重建用 JVM 分段锁串行化——本项目单 jar 部署，多实例时需换 Redis 锁。
 *
 * <p><b>兜底</b>：Redis 不可用时关注/取关直写库、列表直查库，宁可慢也不丢操作；
 * 故障期间直写的增量靠下一次冷启动/重建收敛。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    /** 弱一致关系，5 秒刷一次：足够短，进程崩溃也只丢这一小段 */
    private static final long FLUSH_INTERVAL_MS = 5_000L;

    private static final int PAGE_MAX = 50;

    private static final RedisScript<Long> FOLLOW_SCRIPT = LuaScripts.load("lua/follow.lua", Long.class);

    private static final RedisScript<Long> UNFOLLOW_SCRIPT = LuaScripts.load("lua/unfollow.lua", Long.class);

    private static final RedisScript<List> FLUSH_SCRIPT = LuaScripts.load("lua/follow_flush.lua", List.class);

    private final StringRedisTemplate stringRedisTemplate;

    private final UserFollowMapper userFollowMapper;

    private final AppUserMapper appUserMapper;

    private final UserPubCache userPubCache;

    /** 重建串行化：按 userId 取模分 64 段，避免为每个用户常驻一把锁 */
    private final Object[] loadLocks = initLocks();

    /** 关注。已是关注中则幂等成功；目标不存在 / 关注自己 → 1000 */
    public void follow(long followerId, long followeeId) {
        BizAssert.isTrue(followerId != followeeId, CommonErrorCode.PARAM_ERROR, "不能关注自己");
        requireUser(followeeId);
        try {
            ensureLoaded(followerId, false);
            ensureLoaded(followeeId, true);
            stringRedisTemplate.execute(FOLLOW_SCRIPT,
                    List.of(FollowRedisKeys.following(followerId),
                            FollowRedisKeys.followers(followeeId),
                            FollowRedisKeys.PENDING),
                    String.valueOf(followerId), String.valueOf(followeeId),
                    String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("[follow] Redis 关注失败，退回直写库：{} → {}", followerId, followeeId, e);
            applyFollow(followerId, followeeId);
            syncZsetAfterDirect(followerId, followeeId, true);
        }
    }

    /** 取关。本就没关注则幂等成功 */
    public void unfollow(long followerId, long followeeId) {
        if (followerId == followeeId) {
            return;
        }
        try {
            ensureLoaded(followerId, false);
            ensureLoaded(followeeId, true);
            stringRedisTemplate.execute(UNFOLLOW_SCRIPT,
                    List.of(FollowRedisKeys.following(followerId),
                            FollowRedisKeys.followers(followeeId),
                            FollowRedisKeys.PENDING),
                    String.valueOf(followerId), String.valueOf(followeeId),
                    String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("[follow] Redis 取关失败，退回直写库：{} → {}", followerId, followeeId, e);
            applyUnfollow(followerId, followeeId, System.currentTimeMillis());
            syncZsetAfterDirect(followerId, followeeId, false);
        }
    }

    /** 当前查看者是否关注了目标用户（按钮态） */
    public boolean isFollowing(long viewerId, long followeeId) {
        if (viewerId == followeeId) {
            return false;
        }
        String marker = FollowRedisKeys.loadedFollowing(viewerId);
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(marker))) {
                Double score = stringRedisTemplate.opsForZSet()
                        .score(FollowRedisKeys.following(viewerId), String.valueOf(followeeId));
                return score != null;
            }
        } catch (Exception e) {
            log.warn("[follow] 读关注态失败，退回查库：{} → {}", viewerId, followeeId, e);
        }
        // 集合未加载或 Redis 不可用：只查这一条关系，不为一次判断拉整个集合
        Long n = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, viewerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, 0L));
        return n != null && n > 0;
    }

    /**
     * 关注 / 粉丝列表。`targetUserId` 为空看自己，传他人即看其公开主页（入口均在登录态内）。
     *
     * @param type following=我关注的人，fans=我的粉丝
     */
    public FollowListVO list(long viewerId, Long targetUserId, String type, int pageNum, int pageSize) {
        boolean fans = parseFans(type);
        long target = targetUserId == null ? viewerId : targetUserId;
        if (targetUserId != null) {
            requireUser(target);
        }
        int page = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), PAGE_MAX);
        try {
            return listFromRedis(viewerId, target, fans, page, size);
        } catch (Exception e) {
            log.warn("[follow] 读关注列表失败，退回查库：target={} type={}", target, type, e);
            return listFromDb(viewerId, target, fans, page, size);
        }
    }

    /** 定时把 pending 批量落库。原子取走，多实例并发时同一批只会被一个实例拿到 */
    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS)
    public void flush() {
        List<?> flat;
        try {
            flat = stringRedisTemplate.execute(FLUSH_SCRIPT, List.of(FollowRedisKeys.PENDING));
        } catch (Exception e) {
            log.warn("[follow] 取关注写缓冲失败，等待下一轮", e);
            return;
        }
        if (flat == null || flat.isEmpty()) {
            return;
        }
        int ok = 0;
        int failed = 0;
        for (int i = 0; i + 1 < flat.size(); i += 2) {
            String field = String.valueOf(flat.get(i));
            String value = String.valueOf(flat.get(i + 1));
            int sep = field.indexOf(':');
            if (sep <= 0) {
                log.warn("[follow] 写缓冲存在脏 field，已跳过：{}", field);
                continue;
            }
            long followerId;
            long followeeId;
            long ts;
            try {
                followerId = Long.parseLong(field.substring(0, sep));
                followeeId = Long.parseLong(field.substring(sep + 1));
                ts = parseTs(value);
            } catch (NumberFormatException e) {
                log.warn("[follow] 写缓冲存在无法解析的条目，已跳过：{}={}", field, value);
                continue;
            }
            if (!value.startsWith("follow") && !value.startsWith("unfollow")) {
                log.warn("[follow] 写缓冲存在未知操作，已跳过：{}={}", field, value);
                continue;
            }
            try {
                if (value.startsWith("unfollow")) {
                    applyUnfollow(followerId, followeeId, ts);
                } else {
                    applyFollow(followerId, followeeId);
                }
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("[follow] 关注关系落库失败，留待下轮重试：{}={}", field, value, e);
                try {
                    // putIfAbsent 而不是 put：期间若有更新的操作，别用旧值把它盖回去
                    stringRedisTemplate.opsForHash().putIfAbsent(FollowRedisKeys.PENDING, field, value);
                } catch (Exception again) {
                    log.error("[follow] 写缓冲回填失败，本次变更未落库：{}={}", field, value, again);
                }
            }
        }
        log.info("[follow] 关注关系落库完成：成功 {} 条，失败 {} 条", ok, failed);
    }

    /* ── Redis 读路径 ─────────────────────────────────────── */

    private FollowListVO listFromRedis(long viewerId, long target, boolean fans, int page, int size) {
        ensureLoaded(target, fans);
        ensureLoaded(target, !fans);
        if (viewerId != target) {
            ensureLoaded(viewerId, false);
        }

        String key = fans ? FollowRedisKeys.followers(target) : FollowRedisKeys.following(target);
        long total = zcard(key);

        FollowListVO vo = new FollowListVO();
        vo.setTotal(total);
        vo.setPageNum(page);
        vo.setPageSize(size);
        vo.setFollowingCount(zcard(FollowRedisKeys.following(target)));
        vo.setFansCount(zcard(FollowRedisKeys.followers(target)));

        long start = (long) (page - 1) * size;
        if (start >= total) {
            vo.setList(List.of());
            return vo;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, start + size - 1);
        if (tuples == null || tuples.isEmpty()) {
            vo.setList(List.of());
            return vo;
        }

        List<Long> ids = new ArrayList<>(tuples.size());
        Map<Long, LocalDateTime> times = new HashMap<>();
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            if (t.getValue() == null) {
                continue;
            }
            long id;
            try {
                id = Long.parseLong(t.getValue());
            } catch (NumberFormatException e) {
                continue;
            }
            ids.add(id);
            times.put(id, fromScore(t.getScore()));
        }
        vo.setList(buildVOs(ids, times, id -> viewerFollows(viewerId, id)));
        return vo;
    }

    private void ensureLoaded(long userId, boolean fans) {
        String marker = fans ? FollowRedisKeys.loadedFollowers(userId) : FollowRedisKeys.loadedFollowing(userId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(marker))) {
            return;
        }
        synchronized (loadLocks[(int) (userId & 63)]) {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(marker))) {
                return;
            }
            String key = fans ? FollowRedisKeys.followers(userId) : FollowRedisKeys.following(userId);
            // 重建前先清空：避免上次半途失败留下的残留成员混进来
            stringRedisTemplate.delete(key);
            LambdaQueryWrapper<UserFollow> qw = new LambdaQueryWrapper<UserFollow>()
                    .eq(UserFollow::getDeleted, 0L);
            if (fans) {
                qw.eq(UserFollow::getFolloweeId, userId);
            } else {
                qw.eq(UserFollow::getFollowerId, userId);
            }
            List<UserFollow> rows = userFollowMapper.selectList(qw);
            for (UserFollow r : rows) {
                long other = fans ? r.getFollowerId() : r.getFolloweeId();
                stringRedisTemplate.opsForZSet().add(key, String.valueOf(other), toScore(r.getCreateTime()));
            }
            stringRedisTemplate.opsForValue().set(marker, "1");
        }
    }

    private boolean viewerFollows(long viewerId, long targetId) {
        if (viewerId == targetId) {
            return false;
        }
        try {
            Double score = stringRedisTemplate.opsForZSet()
                    .score(FollowRedisKeys.following(viewerId), String.valueOf(targetId));
            return score != null;
        } catch (Exception e) {
            return false;
        }
    }

    private long zcard(String key) {
        Long n = stringRedisTemplate.opsForZSet().zCard(key);
        return n == null ? 0 : n;
    }

    /* ── 库读路径（Redis 不可用 / 冷启动未加载时的保底） ───────── */

    private FollowListVO listFromDb(long viewerId, long target, boolean fans, int page, int size) {
        LambdaQueryWrapper<UserFollow> qw = new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getDeleted, 0L)
                .orderByDesc(UserFollow::getCreateTime)
                .orderByDesc(UserFollow::getId);
        if (fans) {
            qw.eq(UserFollow::getFolloweeId, target);
        } else {
            qw.eq(UserFollow::getFollowerId, target);
        }
        Page<UserFollow> p = userFollowMapper.selectPage(new Page<>(page, size), qw);

        List<Long> ids = new ArrayList<>(p.getRecords().size());
        Map<Long, LocalDateTime> times = new HashMap<>();
        for (UserFollow r : p.getRecords()) {
            long id = fans ? r.getFollowerId() : r.getFolloweeId();
            ids.add(id);
            times.put(id, r.getCreateTime());
        }
        Set<Long> viewerFollowing = loadViewerFollowing(viewerId, ids);

        FollowListVO vo = new FollowListVO();
        vo.setList(buildVOs(ids, times, viewerFollowing::contains));
        vo.setTotal(p.getTotal());
        vo.setPageNum(page);
        vo.setPageSize(size);
        vo.setFollowingCount(countActive(false, target));
        vo.setFansCount(countActive(true, target));
        return vo;
    }

    private List<FollowUserVO> buildVOs(List<Long> ids, Map<Long, LocalDateTime> times, LongPredicate following) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, UserPubVO> pubs = userPubCache.getOrLoad(ids);
        List<FollowUserVO> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            FollowUserVO vo = new FollowUserVO();
            vo.setUserId(id);
            UserPubVO p = pubs.get(id);
            if (p != null) {
                vo.setNickname(p.getNickname());
                vo.setAvatarUrl(p.getAvatarUrl());
                vo.setCreditScore(p.getCreditScore());
                vo.setAuthStatus(p.getAuthStatus());
            }
            vo.setFollowing(following.test(id));
            vo.setFollowTime(times.get(id));
            out.add(vo);
        }
        return out;
    }

    private Set<Long> loadViewerFollowing(long viewerId, List<Long> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<UserFollow> rows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, viewerId)
                .eq(UserFollow::getDeleted, 0L)
                .in(UserFollow::getFolloweeId, ids));
        Set<Long> set = new HashSet<>(rows.size());
        for (UserFollow r : rows) {
            set.add(r.getFolloweeId());
        }
        return set;
    }

    private long countActive(boolean fans, long userId) {
        LambdaQueryWrapper<UserFollow> qw = new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getDeleted, 0L);
        if (fans) {
            qw.eq(UserFollow::getFolloweeId, userId);
        } else {
            qw.eq(UserFollow::getFollowerId, userId);
        }
        Long n = userFollowMapper.selectCount(qw);
        return n == null ? 0 : n;
    }

    /* ── 落库与直写 ─────────────────────────────────────── */

    /** 关注落库：活跃行已存在直接返回；否则复活墓碑，再不行才插入。撞唯一键都视为成功 */
    private void applyFollow(long followerId, long followeeId) {
        Long active = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFolloweeId, followeeId)
                .eq(UserFollow::getDeleted, 0L));
        if (active != null && active > 0) {
            return;
        }
        try {
            if (userFollowMapper.revive(followerId, followeeId) > 0) {
                return;
            }
        } catch (DuplicateKeyException e) {
            // 脏数据：墓碑与活跃行并存时复活会撞唯一键，活跃行已存在，视为成功
            log.debug("[follow] 复活墓碑撞唯一键，视为已关注：{} → {}", followerId, followeeId);
            return;
        }
        try {
            userFollowMapper.insert(new UserFollow()
                    .setFollowerId(followerId)
                    .setFolloweeId(followeeId));
        } catch (DuplicateKeyException e) {
            log.debug("[follow] 并发关注撞唯一键，视为成功：{} → {}", followerId, followeeId);
        }
    }

    private void applyUnfollow(long followerId, long followeeId, long ts) {
        userFollowMapper.softDelete(followerId, followeeId, ts);
    }

    /** 直写库后尽力同步 ZSET；Redis 仍不可用就放弃，等下一次重建收敛 */
    private void syncZsetAfterDirect(long followerId, long followeeId, boolean follow) {
        try {
            if (follow) {
                double score = System.currentTimeMillis();
                stringRedisTemplate.opsForZSet()
                        .add(FollowRedisKeys.following(followerId), String.valueOf(followeeId), score);
                stringRedisTemplate.opsForZSet()
                        .add(FollowRedisKeys.followers(followeeId), String.valueOf(followerId), score);
            } else {
                stringRedisTemplate.opsForZSet()
                        .remove(FollowRedisKeys.following(followerId), String.valueOf(followeeId));
                stringRedisTemplate.opsForZSet()
                        .remove(FollowRedisKeys.followers(followeeId), String.valueOf(followerId));
            }
        } catch (Exception e) {
            log.warn("[follow] 直写库后同步关注集合失败，等待重建收敛：{} → {}", followerId, followeeId);
        }
    }

    private void requireUser(long userId) {
        Long n = appUserMapper.selectCount(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getId, userId)
                .eq(AppUser::getDeleted, 0L));
        BizAssert.isTrue(n != null && n > 0, CommonErrorCode.PARAM_ERROR, "用户不存在");
    }

    /* ── 工具 ──────────────────────────────────────────── */

    private static boolean parseFans(String type) {
        BizAssert.isTrue("following".equals(type) || "fans".equals(type),
                CommonErrorCode.PARAM_ERROR, "type 只能是 following 或 fans");
        return "fans".equals(type);
    }

    private static long parseTs(String value) {
        int bar = value.indexOf('|');
        if (bar < 0 || bar == value.length() - 1) {
            return System.currentTimeMillis();
        }
        return Long.parseLong(value.substring(bar + 1));
    }

    private static double toScore(LocalDateTime time) {
        if (time == null) {
            return 0d;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime fromScore(Double score) {
        if (score == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(score.longValue()), ZoneId.systemDefault());
    }

    private static Object[] initLocks() {
        Object[] arr = new Object[64];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = new Object();
        }
        return arr;
    }
}
