package com.campus.support.storage.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 服务端转发策略：票据指向自己的 multipart 端点，文件由应用收流传桶。
 *
 * <p>不依赖 StorageService Bean（传文件是 Controller 的事，策略只指路）——
 * 因此 MinIO 关闭时该策略仍可装配，调 multipart 端点才报「对象存储未启用」。
 * 小程序端唯一可用通道：uni.uploadFile 只能发 multipart，发不了 PUT 直传。
 */
@Component
public class ProxyUploadStrategy implements UploadStrategy {

    /** 与 {@code FileUploadController} 的 multipart 端点对应 */
    private static final String UPLOAD_ENDPOINT = "/api/file/upload";

    private final String endpoint;

    private final String bucket;

    public ProxyUploadStrategy(@Value("${minio.endpoint}") String endpoint,
                               @Value("${minio.bucket:item-image}") String bucket) {
        // 去尾斜杠：直拼不能出现双斜杠
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.bucket = bucket;
    }

    @Override
    public UploadMode mode() {
        return UploadMode.PROXY;
    }

    @Override
    public UploadTicket issue(UploadRequest req) {
        UploadChecks.requireSizeOk(req.getSize());
        String objectKey = UploadChecks.buildObjectKey(req.getUserId(), req.getOriginalFilename());

        UploadTicket ticket = new UploadTicket();
        ticket.setMode(mode());
        ticket.setObjectKey(objectKey);
        ticket.setUploadUrl(UPLOAD_ENDPOINT);
        ticket.setHttpMethod("POST");
        // 表单里除文件外还要带 objectKey：服务端按它落桶，不让客户端决定 key
        ticket.setFormFields(Map.of("objectKey", objectKey));
        ticket.setHeaders(Map.of());
        // accessUrl 只与 endpoint/桶/key 有关，与传法无关：票据签发时即可给出
        ticket.setAccessUrl(endpoint + "/" + bucket + "/" + objectKey);
        ticket.setExpiresIn(0);
        return ticket;
    }
}
