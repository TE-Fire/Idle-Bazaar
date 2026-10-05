package com.campus.core.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商品状态（`item.status`，状态机见 business/03 §4.1）。
 *
 * <p><b>枚举名与前端 `AdminItem.status` 逐字对齐</b>（`packages/shared/src/admin.ts`）：
 * 对外输出枚举名（`DRAFT` / `ON_SALE` …）而不是库里的数字，
 * 前端不必再维护一份 1–7 的映射表，后端改状态语义时前端编译期就会报错。
 * 这条符合 tech/01 §5.3「枚举对外输出 code 而非 name」——此处的"code"就是枚举名本身。
 *
 * <p>`OFFLINE`(4) 与 `FORCED_OFFLINE`(7) 必须分开：前者是卖家自己下的、可重新上架；
 * 后者是平台处置，属终态，要恢复只能走申诉。合成一个状态会把「谁下的架」这个信息丢掉。
 *
 * <p><b>无「已预定」</b>（business/03 §4.1）：拍下只生成待付款订单，付款成功才转 `SOLD`。
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
