package com.campus.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 举报对象类型（`report.target_type`，与 `target_id` 组成多态引用）。
 *
 * <p>商品举报最终落到**卖家**头上，但单据记的是商品 id，判定被举报方时要转一层
 * （`ReportService#isReportTarget`）。审核队列的举报任务只收商品举报：
 * 用户/动态/评论举报解析不出商品，进不了队列（notes/09），由社交举报通道单独处置。
 */
@Getter
@AllArgsConstructor
public enum ReportTargetTypeEnum {

    /** 商品：`target_id` = `item.id`；被举报方 = 商品卖家 */
    ITEM(1, "商品"),
    /** 用户：`target_id` = `app_user.id`；被举报方 = 用户本人 */
    USER(2, "用户"),
    /** 动态：`target_id` = `user_moment.id`；被举报方 = 动态作者（notes/22 批次 3） */
    MOMENT(3, "动态"),
    /** 商品评论：`target_id` = `item_comment.id`；被举报方 = 评论者 */
    ITEM_COMMENT(4, "商品评论"),
    /** 动态评论：`target_id` = `moment_comment.id`；被举报方 = 评论者 */
    MOMENT_COMMENT(5, "动态评论");

    private final Integer code;

    private final String desc;

    /** 社交内容（动态 / 两类评论）：不进商品审核队列，走社交举报通道 */
    public boolean isSocial() {
        return this == MOMENT || this == ITEM_COMMENT || this == MOMENT_COMMENT;
    }

    public static ReportTargetTypeEnum fromCode(Integer code) {
        if (code != null) {
            for (ReportTargetTypeEnum t : values()) {
                if (t.code.equals(code)) {
                    return t;
                }
            }
        }
        throw new IllegalArgumentException("未知的举报对象类型：" + code);
    }

    /** 入参校验用：不抛异常，查不到返回 null，由调用方按参数错误处理 */
    public static ReportTargetTypeEnum fromCodeOrNull(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReportTargetTypeEnum t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
