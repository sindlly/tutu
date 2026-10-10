package com.dxy.homework.client;

/**
 * DeepSeek 调用失败。
 * <p>httpStatus 为 -1 表示未拿到响应（网络层异常）。
 * 上层可据此判断是否要返还用户额度：只有"确认没有产生计费"的场景才应返还。
 */
public class DeepSeekException extends RuntimeException {

    private final int httpStatus;

    public DeepSeekException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
