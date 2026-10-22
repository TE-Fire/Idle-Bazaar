package com.campus.support.storage.upload;

/**
 * 图片上传的两种方式（tech/01 4.4 的策略模式落地）：
 * <ul>
 *   <li>PROXY：文件流经服务端（multipart → 应用 → 桶），小程序端唯一可用通道</li>
 *   <li>DIRECT：后端只发 PUT 签名 URL，前端直传桶，不占应用带宽</li>
 * </ul>
 * 默认方式由 {@code campus.upload.mode} 配置，请求可显式覆盖（仅用于降级）。
 */
public enum UploadMode {

    PROXY,
    DIRECT;

    /** 配置值解析（proxy / direct，大小写不敏感）；非法值按 PROXY 兜底并告警由调用方决定 */
    public static UploadMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return PROXY;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return PROXY;
        }
    }
}
