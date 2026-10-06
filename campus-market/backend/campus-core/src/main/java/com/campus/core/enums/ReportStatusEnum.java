package com.campus.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 举报工单状态（`report.status`）。
 *
 * <p>`PROCESSING`(2) 存在的唯一意义是「已被某人接手」，单人审核场景用不上，
 * 所以本期没有独立的受理接口（notes/09 问题 4）。它留在状态机里只是为了让
 * 演示数据能体现出这条流程，以及将来扩展多人协作审核时不用改表。
 */
@Getter
@AllArgsConstructor
public enum ReportStatusEnum {

    /** 待受理 */
    WAITING(1, "待受理"),
    PROCESSING(2, "处理中"),
    /** 终态；结论看 `result` */
    HANDLED(3, "已处理");

    private final Integer code;

    private final String desc;

    public static ReportStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (ReportStatusEnum s : values()) {
                if (s.code.equals(code)) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("未知的举报状态：" + code);
    }
}
