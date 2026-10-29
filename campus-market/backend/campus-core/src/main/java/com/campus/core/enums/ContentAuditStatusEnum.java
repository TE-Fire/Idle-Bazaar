package com.campus.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容审核态（`user_moment` / `moment_comment` / `item_comment` 的 `audit_status`）。
 *
 * <p>与 {@link AuditStatusEnum} 是两套口径，数字重合但语义不同：那个是商品审核**单据**的状态
 * （2 = 已通过），这里是内容对外的可见性（2 = 举报下架）。取值不可互相借用。
 *
 * <p>三处内容表共用本枚举：发布即正常(0)；敏感词屏蔽(1) 目前没有自动写入口（低危词入库前已替换、
 * 高危词直接拒），留给人工复审；举报下架(2) 由管理端处置写入。
 */
@Getter
@AllArgsConstructor
public enum ContentAuditStatusEnum {

    NORMAL(0, "正常"),
    MASKED(1, "敏感词屏蔽"),
    OFFLINE(2, "举报下架");

    private final Integer code;

    private final String desc;
}
