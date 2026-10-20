package com.dxy.homework.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 对象存储配置。
 *
 * <p>三种实现共用同一套 {@code StorageClient} 接口，切换只改 provider，业务代码无感知：
 * <ul>
 *   <li>{@code minio} —— 本地 S3 兼容存储，开发验证用</li>
 *   <li>{@code cos} —— 腾讯云 COS 私有桶，生产用</li>
 *   <li>{@code local} —— 本地磁盘，最省事的兜底</li>
 *   <li>{@code auto} —— 按 minio → cos → local 顺序取第一个配置好的</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(
        @DefaultValue("auto") String provider,
        @DefaultValue Minio minio,
        @DefaultValue Cos cos,
        @DefaultValue LocalFallback localFallback
) {

    public record Minio(
            @DefaultValue("") String endpoint,
            /** 预签名 URL 用的对外地址。Host 参与签名，容器部署时内外地址不同，必须分开 */
            @DefaultValue("") String presignEndpoint,
            @DefaultValue("") String accessKey,
            @DefaultValue("") String secretKey,
            @DefaultValue("homework-grader") String bucket,
            /** 预签名读的有效期，越短越安全 */
            @DefaultValue("30m") Duration presignExpire,
            /** 桶不存在时自动创建，省去手工建桶 */
            @DefaultValue("true") boolean autoCreateBucket
    ) {

        public boolean configured() {
            return notBlank(endpoint) && notBlank(accessKey) && notBlank(secretKey) && notBlank(bucket);
        }
    }

    public record Cos(
            @DefaultValue("") String secretId,
            @DefaultValue("") String secretKey,
            /** 桶名。可以只填 name，由 appId 拼成 name-appid；也可以直接填完整桶名 */
            @DefaultValue("") String bucket,
            @DefaultValue("ap-guangzhou") String region,
            @DefaultValue("") String appId,
            @DefaultValue("30m") Duration presignExpire,
            @DefaultValue("10s") Duration connectTimeout,
            @DefaultValue("30s") Duration socketTimeout
    ) {

        public String resolvedBucket() {
            if (!notBlank(bucket)) {
                return "";
            }
            // 完整桶名形如 name-1250000000，已含后缀就不再拼接
            if (bucket.contains("-")) {
                return bucket;
            }
            return notBlank(appId) ? bucket + "-" + appId : bucket;
        }

        public boolean configured() {
            return notBlank(secretId) && notBlank(secretKey) && !resolvedBucket().isEmpty();
        }
    }

    /**
     * 本地磁盘实现。用途是让整条业务链路在没有对象存储时也能跑通并验证。
     * 生产必须保持 enabled=false。
     */
    public record LocalFallback(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("./data/files") String dir,
            /** 开发模式下直接回源用的地址前缀 */
            @DefaultValue("") String publicBaseUrl
    ) {
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
