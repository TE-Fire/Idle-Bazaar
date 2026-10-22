package com.campus.core.dto.req;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 上传票据签发入参。文件本体不在这里——票据只描述「将要传什么」，
 * 真实文件按票据的 mode 走 PROXY multipart 或 DIRECT PUT。
 */
@Data
public class TicketIssueReq {

    /** 原始文件名（取扩展名做白名单校验） */
    @NotBlank
    private String filename;

    /** 字节数；前端先拦一遍，服务端必须再查 */
    @NotNull
    @Min(1)
    private Long size;

    private String contentType;

    /**
     * 显式指定上传方式（proxy / direct）；不传按服务端 {@code campus.upload.mode} 配置。
     * 小程序端固定传 proxy——uni.uploadFile 只能发 multipart，发不了 PUT 直传。
     */
    private String mode;
}
