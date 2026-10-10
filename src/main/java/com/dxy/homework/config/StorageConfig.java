package com.dxy.homework.config;

import com.dxy.homework.storage.CosStorageClient;
import com.dxy.homework.storage.LocalStorageClient;
import com.dxy.homework.storage.MinioStorageClient;
import com.dxy.homework.storage.StorageClient;
import com.dxy.homework.storage.UnavailableStorageClient;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 {@code storage.provider} 选择实现。
 *
 * <p>显式指定了 provider 但没配置好凭证时，只记 ERROR 并降级为"不可用"，不让应用启动失败 ——
 * 这样命令行批改/评测入口在没有对象存储的机器上依然可用。真正用到文件时会给出明确报错。
 */
@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    @Bean(destroyMethod = "close")
    public StorageClient storageClient(StorageProperties props) {
        String provider = props.provider() == null ? "auto" : props.provider().trim().toLowerCase(Locale.ROOT);
        return switch (provider) {
            case "minio" -> configured(props.minio().configured(), "MinIO",
                    "请填写 MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY / MINIO_BUCKET",
                    () -> new MinioStorageClient(props.minio()));
            case "cos" -> configured(props.cos().configured(), "腾讯云 COS",
                    "请填写 COS_SECRET_ID / COS_SECRET_KEY / COS_BUCKET / COS_REGION",
                    () -> new CosStorageClient(props.cos()));
            case "local" -> {
                log.warn("已显式选择本地磁盘存储，仅限开发验证，生产请改用 minio 或 cos");
                yield new LocalStorageClient(props.localFallback());
            }
            case "auto" -> auto(props);
            default -> throw new IllegalStateException(
                    "未知的 storage.provider=" + provider + "，可选值：minio / cos / local / auto");
        };
    }

    private StorageClient auto(StorageProperties props) {
        if (props.minio().configured()) {
            return new MinioStorageClient(props.minio());
        }
        if (props.cos().configured()) {
            return new CosStorageClient(props.cos());
        }
        if (props.localFallback().enabled()) {
            return new LocalStorageClient(props.localFallback());
        }
        log.error("没有任何对象存储被配置，涉及文件读写的接口将不可用。"
                + "最小配置：STORAGE_PROVIDER=minio + MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET"
                + "（本地 MinIO 用 ./scripts/start-minio.sh 启动）");
        return new UnavailableStorageClient();
    }

    private StorageClient configured(boolean ok, String name, String hint,
                                     java.util.function.Supplier<StorageClient> factory) {
        if (ok) {
            return factory.get();
        }
        log.error("storage.provider 指定了 {}，但凭证未配置完整。{}", name, hint);
        return new UnavailableStorageClient();
    }
}
