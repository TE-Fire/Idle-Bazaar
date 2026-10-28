package com.campus.core.service;

import com.campus.core.entity.CreditLog;
import com.campus.core.enums.CreditReasonEnum;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.CreditLogMapper;
import com.campus.core.service.cache.UserPubCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 信用分变动（`credit_log` 流水 + `app_user.credit_score`）。
 *
 * <p>口径：初始 100、上限 120、下限 0，分值与原因绑定在 {@link CreditReasonEnum}。
 * 读分用行锁（`SELECT ... FOR UPDATE`）而不是先读后写：管理端裁决会有多人同时操作同一作者，
 * 不加锁时两次并发的 before/after 会算到同一个基准上，流水与实际分对不上。
 *
 * <p>调用方都是「处置已经有结果」的场景，所以用户不存在或已到边界一律静默跳过——
 * 扣分失败不该把已经生效的下架回滚掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 120;

    private final AppUserMapper appUserMapper;
    private final CreditLogMapper creditLogMapper;
    private final UserPubCache userPubCache;

    /** 按原因变动一次并记流水；到边界（已 0 / 已 120）不写流水 */
    @Transactional(rollbackFor = Exception.class)
    public void change(long userId, CreditReasonEnum reason, Long bizId, String remark) {
        Integer before = appUserMapper.selectCreditForUpdate(userId);
        if (before == null) {
            log.warn("[credit] 用户不存在，跳过变动 userId={} reason={}", userId, reason);
            return;
        }
        int after = Math.max(MIN_SCORE, Math.min(MAX_SCORE, before + reason.getDelta()));
        if (after == before) {
            return;
        }

        appUserMapper.updateCredit(userId, after);

        CreditLog row = new CreditLog();
        row.setUserId(userId);
        row.setChangeValue(after - before);
        row.setBeforeScore(before);
        row.setAfterScore(after);
        row.setReasonType(reason.name());
        row.setBizId(bizId);
        row.setRemark(remark);
        creditLogMapper.insert(row);

        // 信用分是公开资料的一部分（商品卡 / 个人主页展示），写后失效
        userPubCache.evict(userId);
    }
}
