package com.campus.support.storage.upload;

/**
 * 上传策略：签发「怎么传」的票据。新增一种方式（如二期改 OSS 走 STS 临时凭证）
 * 只需加一个实现并在 {@link UploadStrategyRouter} 的 Map 里登记，Controller 与前端零改动。
 */
public interface UploadStrategy {

    /** 策略标识，同时是配置与请求里的 mode 值 */
    UploadMode mode();

    /**
     * 签发票据。不校验文件内容——大小与扩展名在 {@link UploadChecks} 统一做，
     * 两种策略与 multipart 端点共用同一套规则。
     */
    UploadTicket issue(UploadRequest req);
}
