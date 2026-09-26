package com.campus.common.asserts;

import com.campus.common.exception.BizException;
import com.campus.common.exception.IErrorCode;

/**
 * 业务断言：把「条件不满足 → 抛业务异常」的 if 块压成一行，消灭多分支样板代码。
 *
 * <p>对比：
 * <pre>
 *   // 以前
 *   if (order == null) { throw BizException.of(OrderErrorCode.ORDER_NOT_FOUND); }
 *   if (!order.isOwner(uid)) { throw BizException.of(OrderErrorCode.ORDER_NOT_OWNER); }
 *
 *   // 现在
 *   BizAssert.notNull(order, OrderErrorCode.ORDER_NOT_FOUND);
 *   BizAssert.isTrue(order.isOwner(uid), OrderErrorCode.ORDER_NOT_OWNER);
 * </pre>
 *
 * <p>为什么不用 Hutool 的 {@code cn.hutool.core.lang.Assert}：它抛的是
 * IllegalArgumentException / StateException，会被兜底成 5000 系统错误，
 * 无法携带业务错误码；自写 50 行即可与 IErrorCode 契约打通，比改造依赖更省。
 */
public final class BizAssert {

    private BizAssert() {
    }

    /** 条件为 false 时抛业务异常 */
    public static void isTrue(boolean cond, IErrorCode ec) {
        if (!cond) {
            throw new BizException(ec);
        }
    }

    /** 条件为 false 时抛业务异常，并补充细节文案（保留枚举错误码） */
    public static void isTrue(boolean cond, IErrorCode ec, String detail) {
        if (!cond) {
            throw new BizException(ec, detail);
        }
    }

    /** 对象为 null 时抛业务异常；否则原样返回（可链式继续用） */
    public static <T> T notNull(T obj, IErrorCode ec) {
        if (obj == null) {
            throw new BizException(ec);
        }
        return obj;
    }

    /** 对象为 null 时抛业务异常并补充细节；否则原样返回 */
    public static <T> T notNull(T obj, IErrorCode ec, String detail) {
        if (obj == null) {
            throw new BizException(ec, detail);
        }
        return obj;
    }

    /** 字符串为 null / 空白时抛业务异常；否则原样返回 */
    public static String notBlank(String s, IErrorCode ec) {
        if (s == null || s.isBlank()) {
            throw new BizException(ec);
        }
        return s;
    }

    /** 字符串为 null / 空白时抛业务异常并补充细节；否则原样返回 */
    public static String notBlank(String s, IErrorCode ec, String detail) {
        if (s == null || s.isBlank()) {
            throw new BizException(ec, detail);
        }
        return s;
    }
}
