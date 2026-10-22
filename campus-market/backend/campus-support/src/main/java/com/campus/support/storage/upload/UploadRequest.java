package com.campus.support.storage.upload;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 上传请求：由 Controller 从登录态与请求体组装，策略不碰会话。
 *
 * @param userId          当前登录用户，用于 objectKey 前缀（item/{userId}/...）与越权校验
 * @param originalFilename 原始文件名，取扩展名（白名单校验在 {@link UploadChecks}）
 * @param size            字节数
 * @param contentType     MIME 类型，DIRECT 预留给签名约束，PROXY 忽略
 */
@Data
@AllArgsConstructor
public class UploadRequest {

    private final Long userId;

    private final String originalFilename;

    private final long size;

    private final String contentType;
}
