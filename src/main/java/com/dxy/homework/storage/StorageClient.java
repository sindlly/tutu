package com.dxy.homework.storage;

/**
 * 对象存储抽象。
 *
 * <p>抽成接口只为一件事：COS 凭证还没配好时，也能用本地磁盘把整条业务链路跑通并验证。
 * 生产环境只会启用 COS 实现，且必须是私有桶。
 */
public interface StorageClient extends AutoCloseable {

    /** 上传对象。key 由调用方生成，实现不负责命名。 */
    void put(String key, byte[] content, String contentType);

    /**
     * 生成可读地址。私有桶下是有时效的预签名 URL，调用方不要缓存超过有效期。
     */
    String presignedUrl(String key);

    /**
     * 读取对象内容。批改链路需要把原始作业图取回来重新编码后送模型，
     * 送 base64 而不是让模型去下载我们的预签名 URL —— 避免把可访问地址交给第三方。
     */
    byte[] read(String key);

    void delete(String key);

    /** 实现名称，用于日志与健康检查。 */
    String name();

    /** 释放底层连接。无状态的实现可以不覆盖。 */
    @Override
    default void close() {
        // 默认无需释放
    }
}
