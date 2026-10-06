package com.campus.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 申诉工单状态（`appeal.status`）。
 *
 * <p>2 / 3 都是终态，不可再申诉：UPHELD(2) 维持原判、REVOKED(3) 撤销原判并恢复商品。
 * 「维持」不意味着商品不动——它只是保持原处置结果；「撤销」才要按原处置反向恢复商品状态。
 */
@Getter
@AllArgsConstructor
public enum AppealStatusEnum {

    /** 待复审；进队条件 */
    WAITING(1, "待复审"),
    UPHELD(2, "维持"),
    REVOKED(3, "撤销");

    private final Integer code;

    private final String desc;

    public static AppealStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (AppealStatusEnum s : values()) {
                if (s.code.equals(code)) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("未知的申诉状态：" + code);
    }
}
