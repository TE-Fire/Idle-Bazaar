package com.campus.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 订单取消方（`trade_order.cancel_by`）。
 *
 * <p>记录取消方而不是只记录结果，是为了**区分是否构成违约**：
 * 待面交之后单方取消属违约（`business/01` F-W4 判据），会影响信用分判定；
 * 而付款前取消是正常交易中止，双方都不承担后果。没有这个字段就无法回溯当时的情形。
 */
@Getter
@AllArgsConstructor
public enum CancelTypeEnum {

    BUYER(1, "买家取消"),
    SELLER(2, "卖家取消"),
    /** 30 分钟未付款，由超时扫描或延迟消息触发 */
    SYSTEM_TIMEOUT(3, "系统超时");
    private final Integer code;

    private final String desc;

    public static CancelTypeEnum fromCode(Integer code) {
        if (code != null) {
            for (CancelTypeEnum t : values()) {
                if (t.code.equals(code)) {
                    return t;
                }
            }
        }
        return null;
    }
}
