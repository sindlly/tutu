package com.dxy.homework.common;

/**
 * 统一响应体。后续正式接口沿用这个结构。
 */
public record R<T>(int code, String msg, T data) {

    public static <T> R<T> ok(T data) {
        return new R<>(0, "ok", data);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }
}
