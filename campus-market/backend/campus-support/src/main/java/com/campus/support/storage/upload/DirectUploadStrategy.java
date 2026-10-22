package com.campus.support.storage.upload;

import com.campus.support.storage.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 前端直传策略：票据是 MinIO PUT 签名地址，文件不进应用，省带宽。
 *
 * <p>依赖 StorageService Bean，故只在 MinIO 启用时装配（demo 关存储时该方式不可用，
 * 由 {@link UploadStrategyRouter} 给出明确错误，而不是空指针）。
 */
@Component
@ConditionalOnBean(StorageService.class)
public class DirectUploadStrategy implements UploadStrategy {

    /** 签名有效期 10 分钟：够前端选完图再传，又不至于签一把长期有效的写入口 */
    private static final int EXPIRY_SECONDS = 600;

    private final StorageService storage;

    private final String bucket;

    public DirectUploadStrategy(StorageService storage,
                                @org.springframework.beans.factory.annotation.Value("${minio.bucket:item-image}") String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    @Override
    public UploadMode mode() {
        return UploadMode.DIRECT;
    }

    @Override
    public UploadTicket issue(UploadRequest req) {
        UploadChecks.requireSizeOk(req.getSize());
        String objectKey = UploadChecks.buildObjectKey(req.getUserId(), req.getOriginalFilename());

        UploadTicket ticket = new UploadTicket();
        ticket.setMode(mode());
        ticket.setObjectKey(objectKey);
        ticket.setUploadUrl(storage.presignPutUrl(bucket, objectKey, EXPIRY_SECONDS));
        ticket.setHttpMethod("PUT");
        ticket.setFormFields(Map.of());
        ticket.setHeaders(Map.of());
        ticket.setAccessUrl(storage.publicUrl(bucket, objectKey));
        ticket.setExpiresIn(EXPIRY_SECONDS);
        return ticket;
    }
}
