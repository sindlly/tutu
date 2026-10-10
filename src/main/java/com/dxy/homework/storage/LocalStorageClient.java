package com.dxy.homework.storage;

import com.dxy.homework.common.BizException;
import com.dxy.homework.common.ErrorCode;
import com.dxy.homework.config.StorageProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地磁盘实现，**仅用于本地开发验证**。
 * <p>存在的理由：COS 凭证没配好之前，也要能验证「上传 → 批改 → 错题本」整条链路。
 */
public class LocalStorageClient implements StorageClient {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageClient.class);

    private final Path root;
    private final String publicBaseUrl;

    public LocalStorageClient(StorageProperties.LocalFallback props) {
        this.root = Paths.get(props.dir()).toAbsolutePath().normalize();
        this.publicBaseUrl = props.publicBaseUrl();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建本地文件目录：" + root, ex);
        }
        log.warn("当前使用本地磁盘存储（仅限开发）：{}。生产请配置 COS 凭证。", root);
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            Path target = resolveSafely(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            log.error("本地写入失败 key={}", key, ex);
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public String presignedUrl(String key) {
        // 本地实现没有签名概念，直接回源到读接口
        return publicBaseUrl + "?key=" + key;
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolveSafely(key));
        } catch (IOException ex) {
            log.error("本地读取失败 key={}", key, ex);
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolveSafely(key));
        } catch (IOException ex) {
            log.warn("本地删除失败 key={}：{}", key, ex.getMessage());
        }
    }

    @Override
    public String name() {
        return "local:" + root;
    }

    /** 防目录穿越：key 必须落在 root 之内。 */
    private Path resolveSafely(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw BizException.badRequest("非法的文件路径");
        }
        return target;
    }
}
