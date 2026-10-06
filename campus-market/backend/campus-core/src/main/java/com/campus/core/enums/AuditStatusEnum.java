package com.campus.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品审核单据状态（`item_audit.status`）。
 *
 * <p>1 / 2 与商品状态 `PENDING` / `ON_SALE` 同构，但两者**不是一回事**：
 * 商品状态描述商品此刻在不在架，审核单据状态描述这一轮审核的结果。
 * 一个商品可以有多条审核记录（重提会再进队一次），所以「商品在售」与「有一条已通过的审核单据」
 * 是可以同时成立的，不要用一个去推另一个。
 */
@Getter
@AllArgsConstructor
public enum AuditStatusEnum {

    PENDING(1, "待审核"),
    PASSED(2, "已通过"),
    REJECTED(3, "已驳回"),
    FORCED_OFFLINE(4, "已强制下架");

    private final Integer code;

    private final String desc;

    public static AuditStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (AuditStatusEnum s : values()) {
                if (s.code.equals(code)) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("未知的审核单据状态：" + code);
    }
}
