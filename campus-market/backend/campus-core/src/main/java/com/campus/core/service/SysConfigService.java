package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.core.dto.req.SlaConfigUpdateReq;
import com.campus.core.dto.resp.SlaConfigVO;
import com.campus.core.entity.SysConfig;
import com.campus.core.mapper.SysConfigMapper;
import com.campus.core.redis.ConfigRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置的读写（`sys_config`，目前只有 SLA 六个阈值）。
 *
 * <p><b>只有六个键，别的什么都不收</b>。方法名就叫 {@code sla()} / {@code updateSla()}，
 * 不做通用的 {@code get(key)} / {@code put(key, value)}——一旦开了通用口子，
 * 这张表就会变成谁都能塞、没人敢删的杂物间。
 *
 * <p>读走 Redis Hash `cm:config:sla`（TTL 10 分钟），写提交后 `DEL`。
 * 缓存只为少查一次库，六个键的量不值得更复杂的结构。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    /** 六个键与默认值：库里缺行时按默认补，不让报表因为少一行数据就报错 */
    private static final Map<String, Integer> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put("sla.firstResponseHours", 2);
        DEFAULTS.put("sla.p95Hours", 6);
        DEFAULTS.put("sla.targetRate", 95);
        DEFAULTS.put("sla.backlogHours", 24);
        DEFAULTS.put("sla.avgHandleSeconds", 30);
        DEFAULTS.put("sla.appealHours", 48);
    }

    private static final Map<String, String> REMARKS = Map.of(
            "sla.firstResponseHours", "首审响应承诺小时数",
            "sla.p95Hours", "P95 首审时长上限小时数",
            "sla.targetRate", "SLA 达标率下限（百分数）",
            "sla.backlogHours", "积压红牌阈值小时数",
            "sla.avgHandleSeconds", "单条平均处置秒数上限",
            "sla.appealHours", "申诉复审承诺小时数");

    private static final Duration TTL = Duration.ofMinutes(10);

    private final SysConfigMapper sysConfigMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 读六个阈值：先缓存，缓存空则回库并回填 */
    public SlaConfigVO sla() {
        Map<String, Integer> values = cached();
        SlaConfigVO vo = new SlaConfigVO();
        vo.setFirstResponseHours(values.get("sla.firstResponseHours"));
        vo.setP95Hours(values.get("sla.p95Hours"));
        vo.setTargetRate(values.get("sla.targetRate"));
        vo.setBacklogHours(values.get("sla.backlogHours"));
        vo.setAvgHandleSeconds(values.get("sla.avgHandleSeconds"));
        vo.setAppealHours(values.get("sla.appealHours"));
        return vo;
    }

    /** 全量写六键；库里没有的行补插，已有的改值 */
    @Transactional
    public void updateSla(SlaConfigUpdateReq req) {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("sla.firstResponseHours", req.getFirstResponseHours());
        values.put("sla.p95Hours", req.getP95Hours());
        values.put("sla.targetRate", req.getTargetRate());
        values.put("sla.backlogHours", req.getBacklogHours());
        values.put("sla.avgHandleSeconds", req.getAvgHandleSeconds());
        values.put("sla.appealHours", req.getAppealHours());

        for (Map.Entry<String, Integer> e : values.entrySet()) {
            upsert(e.getKey(), String.valueOf(e.getValue()));
        }
        evict();
    }

    private void upsert(String key, String value) {
        SysConfig exist = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key)
                .eq(SysConfig::getDeleted, 0L)
                .last("LIMIT 1"));
        if (exist == null) {
            sysConfigMapper.insert(new SysConfig()
                    .setConfigKey(key)
                    .setConfigValue(value)
                    .setRemark(REMARKS.get(key)));
        } else {
            exist.setConfigValue(value);
            sysConfigMapper.updateById(exist);
        }
    }

    /** 缓存读：Redis 不可用时直接回库，配置读取不该因为没有缓存就失败 */
    private Map<String, Integer> cached() {
        try {
            Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(ConfigRedisKeys.SLA);
            if (raw != null && !raw.isEmpty()) {
                return toIntMap(raw, DEFAULTS);
            }
        } catch (RuntimeException e) {
            log.warn("[config] SLA 缓存读取失败，回库读", e);
        }
        Map<String, Integer> fromDb = fromDb();
        try {
            Map<String, String> cache = new HashMap<>(fromDb.size());
            fromDb.forEach((k, v) -> cache.put(k, String.valueOf(v)));
            stringRedisTemplate.opsForHash().putAll(ConfigRedisKeys.SLA, cache);
            stringRedisTemplate.expire(ConfigRedisKeys.SLA, TTL);
        } catch (RuntimeException e) {
            log.warn("[config] SLA 缓存回填失败，本次直读库", e);
        }
        return fromDb;
    }

    /** 库里有几行读几行，缺的用默认值补齐 */
    private Map<String, Integer> fromDb() {
        Map<String, Integer> map = new LinkedHashMap<>(DEFAULTS);
        List<SysConfig> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getDeleted, 0L)
                .likeRight(SysConfig::getConfigKey, "sla."));
        for (SysConfig row : rows) {
            if (row.getConfigKey() == null || row.getConfigValue() == null) {
                continue;
            }
            try {
                map.put(row.getConfigKey(), Integer.valueOf(row.getConfigValue().trim()));
            } catch (NumberFormatException e) {
                log.warn("[config] 配置值不是整数，按默认值处理 key={} value={}", row.getConfigKey(), row.getConfigValue());
            }
        }
        return map;
    }

    private static Map<String, Integer> toIntMap(Map<Object, Object> raw, Map<String, Integer> defaults) {
        Map<String, Integer> map = new LinkedHashMap<>(defaults);
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            try {
                map.put(String.valueOf(e.getKey()), Integer.valueOf(String.valueOf(e.getValue()).trim()));
            } catch (NumberFormatException ignore) {
                // 脏值忽略，保留默认
            }
        }
        return map;
    }

    /** 写后失效：失败只 warn，10 分钟 TTL 会自动纠正 */
    private void evict() {
        try {
            stringRedisTemplate.delete(ConfigRedisKeys.SLA);
        } catch (RuntimeException e) {
            log.warn("[config] SLA 缓存清理失败，等 TTL 过期", e);
        }
    }
}
