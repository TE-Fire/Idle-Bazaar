package com.campus.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * 演示图片兜底端点。
 *
 * <p>种子数据的图片外链（cdn.campus.local）是不存在的域名，页面会全部裂图；
 * 把种子 URL 指到这里后，任何路径都回一张按路径哈希的确定性渐变 SVG——
 * 同一 URL 每次刷新同一张，截图可复现。仅演示环境使用，上传链路不受影响。
 */
@Tag(name = "学生端 · 演示图片")
@RestController
@RequestMapping("/api/file/demo")
public class DemoImageController {

    @Operation(summary = "演示图片", description = "按路径哈希生成确定性渐变 SVG，种子数据外链的兜底")
    @GetMapping("/**")
    public ResponseEntity<String> image(HttpServletRequest request) {
        String path = request.getRequestURI();
        int hue = (path.hashCode() & 0x7fffffff) % 360;
        int hue2 = (hue + 40) % 360;
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="800" height="600" viewBox="0 0 800 600">
                  <defs>
                    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0" stop-color="hsl(%d, 65%%, 72%%)"/>
                      <stop offset="1" stop-color="hsl(%d, 60%%, 58%%)"/>
                    </linearGradient>
                  </defs>
                  <rect width="800" height="600" fill="url(#g)"/>
                  <circle cx="640" cy="140" r="220" fill="hsl(%d, 60%%, 80%%)" opacity="0.45"/>
                  <circle cx="120" cy="500" r="180" fill="hsl(%d, 55%%, 45%%)" opacity="0.35"/>
                </svg>
                """.formatted(hue, hue2, hue2, hue);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS))
                .contentType(MediaType.valueOf("image/svg+xml"))
                .body(svg);
    }
}
