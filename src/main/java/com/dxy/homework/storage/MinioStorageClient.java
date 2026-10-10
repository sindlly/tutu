package com.dxy.homework.storage;

import com.dxy.homework.common.BizException;
import com.dxy.homework.common.ErrorCode;
import com.dxy.homework.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MinIO 实现（S3 兼容），用于本地开发验证。
 *
 * <p>桶自动创建且默认为私有；读取一律走预签名 URL。接口行为与 COS 实现保持一致，
 * 后续切换生产只改 {@code storage.provider}，业务代码不用动。
 */
public class MinioStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(MinioStorageClient.class);

    private final MinioClient client;
    private final String bucket;
    private final boolean autoCreateBucket;
    private final int presignSeconds;

    /** 桶只需确认一次；用 volatile + 双重检查，避免每次上传都发一次 bucketExists */
    private volatile boolean bucketReady;

    public MinioStorageClient(StorageProperties.Minio props) {
        this.bucket = props.bucket();
        this.autoCreateBucket = props.autoCreateBucket();
        this.presignSeconds = (int) props.presignExpire().toSeconds();
        this.client = MinioClient.builder()
                .endpoint(props.endpoint())
                .credentials(props.accessKey(), props.secretKey())
                .build();
        log.info("MinIO 存储已启用：endpoint={} bucket={} 预签名有效期={}min",
                props.endpoint(), bucket, props.presignExpire().toMinutes());
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        ensureBucket();
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    // 第三参数传 -1 表示长度已知，避免 SDK 为了探测长度而缓冲整个流
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception ex) {
            log.error("MinIO 上传失败 key={}", key, ex);
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public String presignedUrl(String key) {
        // 签名本身是本地运算，但 MinIO 服务端会校验桶是否存在，所以这一步也要先确保桶
        ensureBucket();
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .expiry(presignSeconds)
                    .build());
        } catch (Exception ex) {
            log.error("MinIO 生成预签名地址失败 key={}", key, ex);
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public byte[] read(String key) {
        ensureBucket();
        try (GetObjectResponse response = client.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build())) {
            return response.readAllBytes();
        } catch (Exception ex) {
            log.error("MinIO 读取失败 key={}", key, ex);
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception ex) {
            // 删除失败不影响主流程，靠定期清理任务兜底
            log.warn("MinIO 删除失败 key={}：{}", key, ex.getMessage());
        }
    }

    @Override
    public String name() {
        return "minio:" + bucket;
    }

    /** 惰性检查桶：构造时不连服务，MinIO 没起来也不影响应用启动。 */
    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    if (!autoCreateBucket) {
                        throw new IllegalStateException(
                                "MinIO 桶不存在且未开启自动创建：" + bucket);
                    }
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("已自动创建 MinIO 桶 {}", bucket);
                }
                bucketReady = true;
            } catch (Exception ex) {
                log.error("MinIO 桶检查失败 bucket={}：{}。请确认 MinIO 已启动"
                        + "（./scripts/start-minio.sh）", bucket, ex.getMessage());
                throw new BizException(ErrorCode.STORAGE_ERROR);
            }
        }
    }
}
