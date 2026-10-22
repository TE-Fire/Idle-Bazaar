package com.campus.user.controller;

import com.campus.common.exception.BizException;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.TicketIssueReq;
import com.campus.core.dto.resp.FileUploadVO;
import com.campus.support.storage.StorageService;
import com.campus.support.storage.upload.UploadChecks;
import com.campus.support.storage.upload.UploadMode;
import com.campus.support.storage.upload.UploadRequest;
import com.campus.support.storage.upload.UploadStrategyRouter;
import com.campus.support.storage.upload.UploadTicket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 学生端图片上传（business/01 W-B4 发布与编辑的前置；tech/01 4.4 策略模式）。
 *
 * <p>两步走：① {@code /upload-ticket} 由策略签发票据（PROXY 指路服务端端点 /
 * DIRECT 给 MinIO PUT 签名）；② PROXY 模式再把文件 multipart 传到 {@code /upload}。
 * 前端提交商品时传票据里的 accessUrl，`item_image.url` 存的永远是公开读直拼地址。
 *
 * <p>上传要登录：对象名带用户前缀（item/{userId}/...），越权写他人 key 会被拒。
 */
@Tag(name = "学生端 · 文件上传")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final UploadStrategyRouter router;

    /** MinIO 关闭时无 Bean（demo profile），PROXY 票据仍可签，真正上传才报错 */
    private final ObjectProvider<StorageService> storageProvider;

    @Value("${minio.bucket:item-image}")
    private String bucket;

    @Operation(summary = "签发上传票据", description = "按服务端配置或显式 mode 选策略；返回 uploadUrl/accessUrl，前端按 mode 分流上传")
    @PostMapping("/upload-ticket")
    public Result<UploadTicket> ticket(@Valid @RequestBody TicketIssueReq req) {
        Long userId = UserContext.getUserId();
        UploadMode mode = req.getMode() == null || req.getMode().isBlank()
                ? null
                : UploadMode.fromConfig(req.getMode());
        UploadTicket ticket = router.issue(
                new UploadRequest(userId, req.getFilename(), req.getSize(), req.getContentType()), mode);
        return Result.ok(ticket);
    }

    @Operation(summary = "服务端转发上传（PROXY 模式第二步）", description = "multipart：file + objectKey（票据的 formFields）；objectKey 前缀必须是 item/{当前用户}/")
    @PostMapping("/upload")
    public Result<FileUploadVO> upload(@RequestParam("file") MultipartFile file,
                                       @RequestParam String objectKey) {
        Long userId = UserContext.getUserId();
        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new BizException(CommonErrorCode.SYSTEM_ERROR, "对象存储未启用，请联系管理员");
        }
        // 票据可被伪造，服务端按当前用户与白名单再查一遍
        UploadChecks.requireOwnObjectKey(userId, objectKey);
        UploadChecks.requireImageExt(file.getOriginalFilename());
        UploadChecks.requireSizeOk(file.getSize());

        String url;
        try {
            url = storage.upload(bucket, objectKey, file.getInputStream(), file.getSize());
        } catch (java.io.IOException e) {
            throw new BizException(CommonErrorCode.SYSTEM_ERROR, "文件读取失败");
        }
        return Result.ok(new FileUploadVO(objectKey, url));
    }
}
