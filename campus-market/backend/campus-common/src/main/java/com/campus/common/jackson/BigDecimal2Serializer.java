package com.campus.common.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BigDecimal 统一输出 2 位小数（tech/01 5.3 规则 2）。
 * 金额一律 decimal(10,2)，前端拿到的是 "18.00" 这类定长字符串，不需要自己再格式化。
 */
public class BigDecimal2Serializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(value.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }
}
