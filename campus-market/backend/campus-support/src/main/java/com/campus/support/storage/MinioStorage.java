package com.campus.support.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 实现（S3 兼容）。
 *
 * <p>只在 minio.enabled=true 时装配，骨架阶段默认关闭，避免没起容器就启动失败。
 * 桶建时即设公开读策略：读 URL 直拼、不过期；{@link #deleteByUrl} 靠 URL 反解定位对象。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
public class MinioStorage implements StorageService {

    /** 公开读策略模板（%s = 桶名）：只放行 GetObject，写与列桶都不公开 */
    private static final String PUBLIC_READ_POLICY = """
            {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
            "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""";

    private final MinioClient client;

    private final String endpoint;

    public MinioStorage(@Value("${minio.endpoint}") String endpoint,
                        @Value("${minio.access-key}") String accessKey,
                        @Value("${minio.secret-key}") String secretKey) {
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        // 去尾斜杠：publicUrl 直拼不能出现双斜杠
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    @Override
    public String upload(String bucket, String object, InputStream input, long size) {
        try {
            ensureBucket(bucket);
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(object)
                    .stream(input, size, -1)
                    .build());
            return publicUrl(bucket, object);
        } catch (Exception e) {
            log.error("[storage] upload failed bucket={} object={}", bucket, object, e);
            throw new IllegalStateException("文件上传失败", e);
        }
    }

    @Override
    public String presignPutUrl(String bucket, String object, int expirySeconds) {
        try {
            ensureBucket(bucket);
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(object)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("[storage] presign PUT failed bucket={} object={}", bucket, object, e);
            throw new IllegalStateException("生成上传链接失败", e);
        }
    }

    @Override
    public String publicUrl(String bucket, String object) {
        return endpoint + "/" + bucket + "/" + object;
    }

    @Override
    public void deleteByUrl(String url) {
        String[] parts = splitBucketObject(url);
        if (parts == null) {
            log.warn("[storage] deleteByUrl 解析不出 bucket/object，跳过：{}", url);
            return;
        }
        delete(parts[0], parts[1]);
    }

    @Override
    public void delete(String bucket, String object) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(object)
                    .build());
        } catch (Exception e) {
            log.warn("[storage] delete failed bucket={} object={}", bucket, object, e);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        if (client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            return;
        }
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        // 建桶后立即公开读：不设的话 publicUrl 直拼返回 403
        client.setBucketPolicy(SetBucketPolicyArgs.builder()
                .bucket(bucket)
                .config(PUBLIC_READ_POLICY.formatted(bucket))
                .build());
    }

    /** URL 形如 {endpoint}/{bucket}/{object...}；object 允许带多级路径，故只切前两段 */
    private String[] splitBucketObject(String url) {
        if (url == null) {
            return null;
        }
        String path = url.startsWith(endpoint) ? url.substring(endpoint.length()) : url;
        int start = path.startsWith("/") ? 1 : 0;
        int slash = path.indexOf('/', start);
        if (slash < 0 || slash == path.length() - 1) {
            return null;
        }
        return new String[]{path.substring(start, slash), path.substring(slash + 1)};
    }
}
