package com.dxy.homework.storage;

import com.dxy.homework.common.BizException;
import com.dxy.homework.common.ErrorCode;

/**
 * 存储未配置时的占位实现：应用能正常启动（命令行批改/评测不依赖存储），
 * 但一旦真的用到文件读写就明确报错，而不是抛一个难以理解的空指针。
 */
public class UnavailableStorageClient implements StorageClient {

    @Override
    public void put(String key, byte[] content, String contentType) {
        throw fail();
    }

    @Override
    public String presignedUrl(String key) {
        throw fail();
    }

    @Override
    public byte[] read(String key) {
        throw fail();
    }

    @Override
    public void delete(String key) {
        // 没配置存储时删除本身就无意义，静默忽略
    }

    @Override
    public String name() {
        return "unavailable";
    }

    private BizException fail() {
        return new BizException(ErrorCode.STORAGE_ERROR,
                "文件服务未配置：请填写 COS_SECRET_ID / COS_SECRET_KEY / COS_BUCKET / COS_REGION");
    }
}
