package com.dxy.homework.common;

import java.util.List;

/**
 * 统一分页返回。page 从 1 开始。
 */
public record PageResult<T>(List<T> records, long total, long page, long size) {

    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        return new PageResult<>(records, total, page, size);
    }
}
