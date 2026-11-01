package com.campus.core.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 议价单状态（`item_offer.status`，状态机见 notes/28）。
 *
 * <p><b>只有「已同意」能下单</b>，且带 10 分钟付款截止；超时由定时任务转「已失效」。
 * 这一点是议价与订单的分水岭：议价单本身不锁货，商品对被同意的多个买家同时有效，
 * 谁先付款谁成交——裁决交给 {@code OrderService#pay} 的条件更新，不在这里做。
 *
 * <p><b>「已拒绝」与「已失效」对外文案不同但行为一致</b>：前者是卖家明确否决，
 * 后者是没人处理或同意的价格放过了时限。拆成两个值是为了让买家知道该不该再谈一次，
 * 而不是笼统地显示「失败」。
 *
 * <p>「已下单」是终态，与订单一一对应。买家之后取消订单不会把议价打回可用状态——
 * 那会让一个过期的价格重新生效；要再买就重新发起议价。
 */
@Getter
@AllArgsConstructor
public enum OfferStatusEnum {

    PENDING(1, "待答复"),
    AGREED(2, "已同意"),
    REJECTED(3, "已拒绝"),
    EXPIRED(4, "已失效"),
    ORDERED(5, "已下单");

    private final Integer code;

    private final String desc;

    /** 对外输出的形态：枚举名本身（同 {@link ItemStatusEnum} 的口径） */
    @JsonValue
    public String value() {
        return name();
    }

    public static OfferStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (OfferStatusEnum s : values()) {
                if (s.code.equals(code)) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("未知的议价状态：" + code);
    }

    /** 进行中：占着「同买家同商品仅一条」的名额，也是卖家待办列表的取数范围 */
    public boolean isAlive() {
        return this == PENDING || this == AGREED;
    }
}
