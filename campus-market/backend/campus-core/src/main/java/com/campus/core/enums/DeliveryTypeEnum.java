package com.campus.core.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 收货方式（`item.delivery_type` / `trade_order.delivery_type`）。
 *
 * <p><b>三种方式互斥</b>：面交走面交点（{@code meet_point_id}）、自提由卖家设提货地址
 * （{@code user_address.kind=PICKUP}）、送货上门由买家下单时选收货地址（{@code user_address.kind=SHIP}）。
 * 订单在创建时把商品的 {@code delivery_type} 抄进订单，之后不再随商品改动。
 *
 * <p><b>对外输出枚举名</b>：与 {@link OrderStatusEnum} 同口径，前端不维护 1–3 的映射。
 */
@Getter
@AllArgsConstructor
public enum DeliveryTypeEnum {

    MEET(1, "面交"),
    PICKUP(2, "自提"),
    DELIVERY(3, "送货上门");

    private final Integer code;

    private final String desc;

    @JsonValue
    public String value() {
        return name();
    }

    public static DeliveryTypeEnum fromCode(Integer code) {
        if (code != null) {
            for (DeliveryTypeEnum e : values()) {
                if (e.code.equals(code)) {
                    return e;
                }
            }
        }
        // 历史数据 / 缺省按面交兜底，避免 toVO 序列化空指针
        return MEET;
    }
}
