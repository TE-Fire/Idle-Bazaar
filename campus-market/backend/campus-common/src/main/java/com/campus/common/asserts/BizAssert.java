package com.campus.common.asserts;

import com.campus.common.exception.BizException;
import com.campus.common.exception.IErrorCode;

/**
 * 业务断言：把「条件不满足 → 抛业务异常」的 if 块压成一行。
 *
 * <p>不用 Hutool 的 `cn.hutool.core.lang.Assert`：它抛 IllegalArgumentException / StateException，
 * 会被兜底成 5000 系统错误，带不了业务错误码。
 *
 * <p>`isTrue(cond, ec)` 陈述的是**正常态**（条件为真才继续），与 `if (坏条件) throw` 互为反命题；
 * 这是 assert 家族（JUnit / Spring Assert）的既有语义，不是本项目自创。
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
