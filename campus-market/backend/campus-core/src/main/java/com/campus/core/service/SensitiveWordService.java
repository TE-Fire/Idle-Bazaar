package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.exception.BizException;
import com.campus.core.entity.SensitiveWord;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
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
 * <p>词库整表载入内存并缓存 5 分钟：过滤在发布/评论热路径上，逐次查库不划算；
 * 管理端改词后最多 5 分钟生效。Redis 发布订阅热更新属批次 3，此处先不上。
 */
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private static final long TTL_MS = 5 * 60 * 1000L;

    private final SensitiveWordMapper sensitiveWordMapper;

    private volatile Snapshot snapshot;

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

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s != null && System.currentTimeMillis() - s.loadedAt() < TTL_MS) {
            return s;
        }
        synchronized (this) {
            if (snapshot != null && System.currentTimeMillis() - snapshot.loadedAt() < TTL_MS) {
                return snapshot;
            }
            snapshot = load();
            return snapshot;
        }
    }

    private Snapshot load() {
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
        return new Snapshot(high, low, System.currentTimeMillis());
    }

    private record Snapshot(Set<String> high, List<String> low, long loadedAt) {
    }
}
