package com.campus.core.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品状态（`item.status`）。对外输出枚举名而非库值，与前端 AdminItem.status 逐字对齐。
 *
 * <p>OFFLINE(4) 卖家自下架可重上，FORCED_OFFLINE(7) 平台处置终态只能走申诉恢复——
 * 合成一个状态会丢掉「谁下的架」。无「已预定」：拍下只生成待付款订单（business/03 §4.1）。
 */
@Getter
@AllArgsConstructor
public enum ItemStatusEnum {

    DRAFT(1, "草稿"),
    PENDING(2, "待审核"),
    ON_SALE(3, "在售"),
    /** 卖家自己下架，可重新上架 */
    OFFLINE(4, "已下架"),
    SOLD(5, "已售出"),
    REJECTED(6, "审核未过"),
    /** 平台处置，终态，恢复需走申诉 */
    FORCED_OFFLINE(7, "已强制下架");

    private final Integer code;

    private final String desc;

    /** 对外输出的形态：枚举名本身 */
    @JsonValue
    public String value() {
        return name();
    }

    public static ItemStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (ItemStatusEnum s : values()) {
                if (s.code.equals(code)) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("未知的商品状态：" + code);
    }

    /**
     * 平台可强制下架的状态：只有「待审核」与「在售」。
     * 其余（草稿、已下架、已售出、审核未过、已强制下架）下架没有意义——
     * 它们本来就不在架，或已经是处置结果。
     */
    public boolean canForceOffline() {
        return this == PENDING || this == ON_SALE;
    }

    /**
     * 平台可删除的状态：草稿与三个非在售终态。
     *
     * <p>不含 `SOLD`：已售出商品被 `trade_order` 引用，删了订单页查不到商品快照
     * （business/03 F-A6 判据：纠纷介入看的是商品快照而非当前商品）。
     * 不含 `PENDING` / `ON_SALE`：先下架再删，避免误删正在交易的货。
     */
    public boolean canDelete() {
        return this == DRAFT || this == OFFLINE || this == REJECTED || this == FORCED_OFFLINE;
    }
}
