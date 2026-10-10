package com.dxy.homework.model;

import java.util.List;

/**
 * 单道题的批改结果。
 * <p>isCorrect 为 null 表示模型没有给出明确判定（例如 ocr 模式），不要当作"错题"统计。
 */
public record GradedQuestion(
        String no,
        Integer page,
        String type,
        String stem,
        String studentAnswer,
        String correctAnswer,
        Boolean isCorrect,
        Double confidence,
        String wrongReason,
        String analysis,
        List<String> knowledgePoints,
        Integer difficulty,
        Boolean needsHumanCheck,
        String remark,
        RegionHint regionHint
) {

    /** 题目在照片中的归一化位置，仅作裁剪兜底，不保证精确。 */
    public record RegionHint(Double x, Double y, Double w, Double h) {

        public boolean usable() {
            if (x == null || y == null || w == null || h == null) {
                return false;
            }
            boolean inRange = isUnit(x) && isUnit(y) && isUnit(w) && isUnit(h);
            return inRange && w > 0.01 && h > 0.01;
        }

        private static boolean isUnit(Double v) {
            return v != null && v >= 0 && v <= 1;
        }
    }
}
