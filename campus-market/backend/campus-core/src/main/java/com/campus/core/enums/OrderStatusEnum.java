package com.campus.core.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单状态（`trade_order.status`，十一态，状态机见 notes/13 第二节）。
 *
 * <p><b>对外输出枚举名而不是数字</b>（同 {@link ItemStatusEnum} 的口径）：
 * 前端不必再维护一份 1–11 的映射表，后端改状态语义时前端编译期就会报错。
 *
 * <p><b>付款之后的分化由收货方式决定</b>（{@code DeliveryTypeEnum}）：面交走
 * 待面交→(核销)→已验货；自提走 待自提→(买家确认)；送货上门走 待发货→(卖家发货)→已发货→(买家确认)。
 * 三个新态（待发货/已发货/待自提）与既有态共享同一套终态与冻结语义。
 *
 * <p><b>没有「已预定」</b>：商品侧同样没有，两者是同一个决策的两处落地——
 * 拍下不锁货，付款成功才由 {@code OrderService} 转 `SOLD`。
 *
 * <p><b>注意没有「纠纷已裁决」这个状态码</b>：库里八个取值中没有它，终态一律落到
 * `COMPLETED` / `CANCELED` / `REFUNDED` 之一。裁决的结果由管理端决定后映射到已有状态——
 * 买家赢→已退款，卖家赢→已完成，双方均无据→已取消。这是有意的设计，
 * 单独留一个「已裁决」状态会让订单既不完成也不退款，反而说不清钱货去向。
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    WAIT_PAY(1, "待付款"),
    WAIT_MEET(2, "待面交"),
    /** 面交现场扫码核销完成，等待买家认可货品；不是终态 */
    VERIFIED(3, "已验货"),
    COMPLETED(4, "已完成"),
    CANCELED(5, "已取消"),
    REFUNDING(6, "退款中"),
    REFUNDED(7, "已退款"),
    /** 冻结态，双方不可操作，等待管理端裁决 */
    DISPUTING(8, "纠纷中"),

    /** 送货上门：付款成功后待卖家发货 */
    WAIT_SHIP(9, "待发货"),

    /** 送货上门：卖家已发货，待买家确认收货 */
    SHIPPED(10, "已发货"),

    /** 自提：付款成功后待买家到提货地址自取 */
    WAIT_PICKUP(11, "待自提");

    private final Integer code;

    private final String desc;

    /** 对外输出的形态：枚举名本身 */
    @JsonValue
    public String value() {
        return name();
    }

    public static OrderStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (OrderStatusEnum s : values()) {
                if (s.code.equals(code)) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("未知的订单状态：" + code);
    }

    /** 终态：不可再流转。注：本平台未设「纠纷已裁决」状态，见类注释 */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELED || this == REFUNDED;
    }

    /** 买家可在履约动作发生前直接取消的阶段（面交前 / 发货前 / 自提前） */
    public boolean canBuyerCancel() {
        return this == WAIT_PAY || this == WAIT_MEET || this == WAIT_SHIP || this == WAIT_PICKUP;
    }

    /** 冻结：纠纷中订单双方均不可操作 */
    public boolean isFrozen() {
        return this == DISPUTING;
    }
}
