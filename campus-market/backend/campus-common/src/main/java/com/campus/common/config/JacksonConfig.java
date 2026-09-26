package com.campus.common.config;

import com.campus.common.jackson.BigDecimal2Serializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Jackson 四条硬规则（tech/01 5.3）：
 * 1. Long 全部转 String —— 雪花 ID 超过 JS 安全整数，不转会让 ID 尾数变成 00
 * 2. BigDecimal 统一 2 位
 * 3. 时间统一 yyyy-MM-dd HH:mm:ss + Asia/Shanghai
 * 4. null 不输出
 */
@Configuration
public class JacksonConfig {

    private static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
        return builder -> builder
                // 规则 1：Long / long 一律字符串
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                // 规则 2：金额
                .serializerByType(BigDecimal.class, new BigDecimal2Serializer())
                // 规则 3：时间
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(formatter))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(formatter))
                .timeZone(TimeZone.getTimeZone(ZoneId.of("Asia/Shanghai")))
                .simpleDateFormat(DATETIME_PATTERN)
                // 规则 4：null 不输出
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                // 前端多传字段不炸，便于前后端并行开发
                .featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
