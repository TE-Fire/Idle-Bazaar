package com.campus.support.storage;

import java.io.InputStream;

/**
 * 对象存储抽象（tech/01 4.4）：一期 MinIO，二期换 OSS 只改实现类与配置。
 *
 * <p>业务代码只依赖这个接口，不出现任何 MinIO 的 API。
 * 桶为公开读策略（建桶时设置），读 URL 直拼不过期；写走两种通道：
 * {@link #upload}（文件流经服务端）与 {@link #presignPutUrl}（前端直传）。
 */
public interface StorageService {

    /**
     * 上传（服务端转发通道：文件流经应用再入桶）
     *
     * @param bucket  桶：item-image / verify-file / avatar
     * @param object  对象名（含扩展名，由调用方保证唯一）
     * @param input   文件流
     * @param size    字节大小，用于校验上限
     * @return 公开读 URL（直拼，不过期）
     */
    String upload(String bucket, String object, InputStream input, long size);

    /** PUT 签名 URL（前端直传通道用），有效期秒；前端 PUT 时不需要再带认证 */
    String presignPutUrl(String bucket, String object, int expirySeconds);

    /** 公开读 URL 直拼：桶已设公开读策略，与签名无关故不过期 */
    String publicUrl(String bucket, String object);

    /** 按公开读 URL 删对象（解析不出 bucket/object 时静默跳过，删孤儿对象不因解析失败中断） */
    void deleteByUrl(String url);

    void delete(String bucket, String object);
}
