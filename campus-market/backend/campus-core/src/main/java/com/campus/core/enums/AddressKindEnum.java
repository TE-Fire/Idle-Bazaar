package com.campus.core.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 地址类型（`user_address.kind`）。
 *
 * <p><b>收货(SHIP) 由买家维护</b>：送货上门订单下单时由买家从中选取；
 * <b>自提(PICKUP) 由卖家维护</b>：发布自提商品时从中选取一个作为提货点。
 * 两类地址同一张表，靠 `kind` 区分，互不可见。
 */
@Getter
@AllArgsConstructor
public enum AddressKindEnum {

    SHIP(1, "收货地址"),
    PICKUP(2, "自提地址");

    private final Integer code;

    private final String desc;

    @JsonValue
    public String value() {
        return name();
    }

    /** 校验入参 kind 合法：非法值统一抛参错，不让脏数据进库 */
    public static AddressKindEnum fromCode(Integer code) {
        for (AddressKindEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知的地址类型：" + code);
    }
}
