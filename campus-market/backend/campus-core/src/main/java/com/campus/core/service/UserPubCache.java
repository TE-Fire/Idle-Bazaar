package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.core.dto.resp.UserPubVO;
import com.campus.core.entity.AppUser;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.redis.UserPubRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 学生公开资料缓存（{@link UserPubVO}），读穿供 {@link ItemService#fillSellers} 回填卖家信息。
 *
 * <p>缓存公开子集而非整个 AppUser：实体含手机号等敏感字段。批量读是因为列表一页要回填
 * 多个卖家，且同一卖家会跨页重复出现。
 *
 * <p>TTL 10 分钟是当前唯一失效手段——昵称 / 信用分 / 认证态本期没有写入口；
 * 将来用户管理、认证审核、信用分任一落地时，写路径必须调 {@link #evict(Long)}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPubCache {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final AppUserMapper appUserMapper;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 按用户 id 批量取公开资料：先读缓存，未命中的回库并回填；Redis 不可用时整体降级直查库。
     *
     * <p>已软删的用户查不到，不进结果也不写缓存——调用方按「查不到就不回填」处理，
     * 与改造前行为一致。
     */
    public Map<Long, UserPubVO> getOrLoad(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>(userIds);
        Map<Long, UserPubVO> result = readCache(ids);
        List<Long> missing = ids.stream().filter(id -> !result.containsKey(id)).toList();
        if (missing.isEmpty()) {
            return result;
        }
        List<AppUser> rows = appUserMapper.selectList(new LambdaQueryWrapper<AppUser>()
                .in(AppUser::getId, missing)
                .eq(AppUser::getDeleted, 0));
        for (AppUser u : rows) {
            UserPubVO vo = UserPubVO.from(u);
            result.put(u.getId(), vo);
            writeCache(vo);
        }
        return result;
    }

    /** 批量读缓存；读失败或类型不符都当作未命中，由调用方回库——缓存不可用不该让页面 500 */
    private Map<Long, UserPubVO> readCache(LinkedHashSet<Long> ids) {
        List<String> keys = ids.stream().map(UserPubRedisKeys::buildKey).toList();
        List<Object> cached;
        try {
            cached = redisTemplate.opsForValue().multiGet(keys);
        } catch (Exception e) {
            log.warn("[user] 读公开资料缓存失败，降级直查库", e);
            return new HashMap<>();
        }
        Map<Long, UserPubVO> result = new HashMap<>();
        if (cached == null) {
            return result;
        }
        // multiGet 按 key 顺序返回（含 null），按下标回填 id
        int i = 0;
        for (Long id : ids) {
            Object v = cached.get(i++);
            if (v instanceof UserPubVO vo) {
                result.put(id, vo);
            }
        }
        return result;
    }

    /** 写缓存失败不影响本次返回：回库的结果已经拿到了 */
    private void writeCache(UserPubVO vo) {
        try {
            redisTemplate.opsForValue().set(UserPubRedisKeys.buildKey(vo.getUserId()), vo, TTL);
        } catch (Exception e) {
            log.warn("[user] 写公开资料缓存失败，本次回填结果仍然可用", e);
        }
    }

    /** 公开资料变更后失效；失败只意味着多滞后一个 TTL，不抛 */
    public void evict(Long userId) {
        try {
            redisTemplate.delete(UserPubRedisKeys.buildKey(userId));
        } catch (Exception e) {
            log.warn("[user] 失效公开资料缓存失败，等待 TTL 自愈", e);
        }
    }
}
