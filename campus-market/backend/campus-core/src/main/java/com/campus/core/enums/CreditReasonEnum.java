package com.campus.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 信用分变动原因（`credit_log.reason_type`，口径见 db/schema.sql 该表注释）。
 *
 * <p>分值写死在本枚举，调用方只传原因：口径集中一处，改规则不用翻业务代码。
 * 上限 120 / 下限 0 由 {@code CreditService} 保证，与 `app_user.credit_score` 注释一致。
 */
@Getter
@AllArgsConstructor
public enum CreditReasonEnum {

    /** 完成交易 +1（上限 120，不参与本期社交链路） */
    TRADE_DONE(1, "完成交易"),
    /** 违约取消 -5 */
    BREACH_CANCEL(-5, "违约取消"),
    /** 举报成立 -10：商品举报与社交内容举报同一口径 */
    REPORTED(-10, "举报成立"),
    /** 举报误判被申诉撤销 +10：只走商品举报申诉通道，与 REPORTED 对冲 */
    REPORT_REVOKED(10, "举报撤销"),
    /** 平台强制下架 -15（当前不单独扣：举报成立内部已调强制下架，重复扣会双计） */
    FORCE_OFF(-15, "强制下架"),
    /** 封禁清零：delta 取负上限，下限 0 兜住 */
    BAN(-120, "封禁"),
    /**
     * 人工调整：delta 固定 0，实际幅度由调用方传入（±20）。
     * 它只是流水上的原因标记，不参与 {@code CreditService#change} 的算分。
     */
    ADMIN_ADJUST(0, "人工调整");

    private final int delta;

    private final String desc;
}
