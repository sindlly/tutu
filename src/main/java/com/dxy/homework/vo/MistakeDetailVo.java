package com.dxy.homework.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 错题详情。
 *
 * @param cropImageUrl 原图裁剪地址，可能为空（坐标不可用或裁剪失败）。
 *                     卡片是主要展示形式，这张图是"当时我到底怎么写的"的补充证据
 * @param reviews      复习记录，按时间倒序
 */
public record MistakeDetailVo(
        MistakeVo mistake,
        String correctAnswer,
        String wrongReason,
        String analysis,
        List<String> knowledgePoints,
        String cropImageUrl,
        Long questionId,
        List<ReviewLog> reviews
) {

    public record ReviewLog(Long id, boolean correct, LocalDateTime reviewedAt) {
    }
}
