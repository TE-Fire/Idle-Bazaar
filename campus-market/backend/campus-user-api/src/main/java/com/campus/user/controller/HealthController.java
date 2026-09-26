package com.campus.user.controller;

import com.campus.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 学生端连通性自检。
 *
 * <p>顺带验证 Jackson 四条规则：id 是 Long 但输出为字符串、金额 2 位、
 * 时间 yyyy-MM-dd HH:mm:ss、null 字段不出现。
 */
@Tag(name = "学生端 · 自检")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "健康检查")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> data = new HashMap<>();
        // 19 位雪花 ID：如果前端把它定义成 number，这里就会看到尾数变成 0
        data.put("snowflakeId", 1964837263847563264L);
        data.put("price", new BigDecimal("18"));
        data.put("serverTime", LocalDateTime.now());
        data.put("module", "campus-user-api");
        return Result.ok(data);
    }
}
