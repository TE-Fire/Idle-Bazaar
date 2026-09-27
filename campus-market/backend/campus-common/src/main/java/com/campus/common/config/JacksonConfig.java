package com.campus.common.config;

import com.campus.common.constant.TimePattern;
import com.campus.common.jackson.BigDecimal2Serializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Jackson 四条硬规则（tech/01 5.3）：
 * 1. Long 全部转 String —— 雪花 ID 超过 JS 安全整数，不转会让 ID 尾数变成 00
 * 2. BigDecimal 统一 2 位
 * 3. 时间统一 yyyy-MM-dd HH:mm:ss + Asia/Shanghai（格式常量见 {@link TimePattern}）
 * 4. null 不输出
 *
 * <p>这里走 Spring Boot 的定制器扩展点，而不是自己 new ObjectMapper：
 * 后者会丢掉 Boot 自动注册的所有模块（含 JavaTimeModule），见 README「已知坑」。
 * 单个字段要用别的格式时，在字段上加 {@code @JsonFormat(pattern = TimePattern.DATE)}，
 * 注解优先级高于这里的全局注册。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                // 规则 1：Long / long 一律字符串
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                // 规则 2：金额
                .serializerByType(BigDecimal.class, new BigDecimal2Serializer())
                // 规则 3：时间。LocalDateTime 显式钉格式，否则 Boot 默认输出 ISO（带 T）
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(TimePattern.DATETIME_FORMATTER))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(TimePattern.DATETIME_FORMATTER))
                // LocalTime 默认输出 18:22:49.123（带纳秒），截断为 HH:mm:ss 更省字节
                .serializerByType(LocalTime.class, new LocalTimeSerializer(TimePattern.TIME_FORMATTER))
                .timeZone(TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai")))
                .simpleDateFormat(TimePattern.DATETIME)
                // 规则 4：null 不输出
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                // 前端多传字段不炸，便于前后端并行开发
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
