package com.dxy.homework.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 复习打卡。
 *
 * @param correct 这次复习是否做对
 */
public record MistakeReviewRequest(
        @NotNull(message = "不能为空") Boolean correct
) {
}
