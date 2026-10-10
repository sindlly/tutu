package com.dxy.homework.model;

import java.util.List;

/**
 * 一次批改的完整产出，同时服务于控制台汇总与报告落盘。
 */
public record GradeOutcome(
        String file,
        String mode,
        String subject,
        Integer grade,
        boolean success,
        String error,
        int attempts,
        long latencyMs,
        Integer promptTokens,
        Integer completionTokens,
        Integer cacheHitTokens,
        Double costYuan,
        boolean peakPricing,
        boolean jsonParseOk,
        int questionCount,
        int wrongCount,
        int unknownCount,
        int needsCheckCount,
        Integer imageWidth,
        Integer imageHeight,
        long imageBytes,
        List<GradedQuestion> questions,
        String rawJson,
        String rawResponse
) {

    public static GradeOutcome failed(String file, String mode, String subject, Integer grade,
                                      String error, long latencyMs, PreparedImage image) {
        return new GradeOutcome(file, mode, subject, grade, false, error, 0, latencyMs,
                null, null, null, null, false, false, 0, 0, 0, 0,
                image == null ? null : image.width(),
                image == null ? null : image.height(),
                image == null ? 0L : image.originalBytes(),
                List.of(), null, null);
    }
}
