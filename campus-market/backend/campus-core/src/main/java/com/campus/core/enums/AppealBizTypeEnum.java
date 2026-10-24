package com.campus.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 申诉对象类型（`appeal.biz_type`，与 `biz_id` 组成多态引用）。
 *
 * <p>三个值指向三张性质不同的表：1/2 是**待处置案卷**（平台处置的结果），
 * 3 是被引用的业务实体——订单自己不是案卷，纠纷只是它的一个状态，复用本表
 * 只为让它进同一条审核队列（notes/13 决策 2）。
 *
 * <p>裁决时分流：1/2 走 `revoke()` 恢复商品，3 走 `OrderService#settleDispute` 直接裁订单。
 */
@Getter
@AllArgsConstructor
public enum AppealBizTypeEnum {

    /** 商品审核处置：`biz_id` = `item_audit.id` */
    ITEM(1, "商品审核"),
    /** 举报处置：`biz_id` = `report.id` */
    REPORT(2, "举报"),
    /** 订单纠纷：`biz_id` = `trade_order.id` */
    ORDER(3, "订单纠纷");

    private final Integer code;

    private final String desc;

    public static AppealBizTypeEnum fromCode(Integer code) {
        if (code != null) {
            for (AppealBizTypeEnum t : values()) {
                if (t.code.equals(code)) {
                    return t;
                }
            }
        }
        throw new IllegalArgumentException("未知的申诉对象类型：" + code);
    }

    /** 入参校验用：不抛异常，查不到返回 null，由调用方按参数错误处理 */
    public static AppealBizTypeEnum fromCodeOrNull(Integer code) {
        if (code == null) {
            return null;
        }
        for (AppealBizTypeEnum t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
