package com.campus.common.constant;

import java.time.format.DateTimeFormatter;

/**
 * 全局时间格式常量（对应 tech/01 5.3 规则 3）。
 *
 * <p>两类成员用途不同，不要混用：
 * <ul>
 *   <li><b>String 常量</b>：给注解用——`@JsonFormat(pattern = TimePattern.DATE)`
 *       注解的属性值只能是编译期字符串，放不进 DateTimeFormatter</li>
 *   <li><b>DateTimeFormatter 常量</b>：给代码用。它是不可变对象且线程安全，
 *       做成静态常量复用，省去每次 `ofPattern` 重新解析 pattern 的开销</li>
 * </ul>
 *
 * <p>注意：这里的 pattern 都不含时区，只能用于 LocalDateTime / LocalDate / LocalTime。
 * Instant 与 ZonedDateTime 用它们会抛 `UnsupportedTemporalTypeException`。
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
