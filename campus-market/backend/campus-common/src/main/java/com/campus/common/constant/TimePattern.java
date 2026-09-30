package com.campus.common.constant;

import java.time.format.DateTimeFormatter;

/**
 * 全局时间格式常量（tech/01 5.3 规则 3）。两类成员不要混用：
 * String 常量给注解用（`@JsonFormat(pattern = TimePattern.DATE)` 的属性值只能是编译期字符串）；
 * DateTimeFormatter 常量给代码用（线程安全，复用省去每次解析 pattern 的开销）。
 *
 * <p>pattern 都不含时区，只能用于 LocalDateTime / LocalDate / LocalTime；
 * Instant 与 ZonedDateTime 用它抛 `UnsupportedTemporalTypeException`。
 */
public final class TimePattern {

    /** 日期 2026-09-27 */
    public static final String DATE = "yyyy-MM-dd";

    /** 时间 18:22:49 */
    public static final String TIME = "HH:mm:ss";

    /** 日期时间 2026-09-27 18:22:49，全局序列化默认格式 */
    public static final String DATETIME = "yyyy-MM-dd HH:mm:ss";

    /** 紧凑格式 20260927182249，用于文件名、导出批次号等拼接场景 */
    public static final String COMPACT = "yyyyMMddHHmmss";

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE);
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME);
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern(DATETIME);
    public static final DateTimeFormatter COMPACT_FORMATTER = DateTimeFormatter.ofPattern(COMPACT);

    private TimePattern() {
    }
}
