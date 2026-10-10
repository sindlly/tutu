package com.dxy.homework.vo;

import com.dxy.homework.entity.Question;
import java.util.List;

/**
 * 单道题的批改结果。
 *
 * @param needsCheck 是否建议用户核对。置信度低或模型自报不确定时为 true，
 *                   UI 上必须区别展示，不能直接当成"孩子做错了"
 * @param bbox       题目在原图中的归一化位置，可能为空（模型未给出）
 * @param cropImageUrl 错题裁剪图地址，可能为空（尚未生成）
 */
public record QuestionVo(
        Long id,
        Integer seq,
        String questionNo,
        Integer questionType,
        String stem,
        String studentAnswer,
        String correctAnswer,
        boolean correct,
        Double confidence,
        String wrongReason,
        String analysis,
        List<String> knowledgePoints,
        Integer difficulty,
        boolean needsCheck,
        Bbox bbox,
        String cropImageUrl
) {

    /** 归一化 0-1 坐标，前端按原图尺寸换算成像素。 */
    public record Bbox(Double x, Double y, Double w, Double h) {
    }

    public static QuestionVo from(Question q, String cropImageUrl) {
        Bbox bbox = null;
        if (q.getBboxX() != null && q.getBboxY() != null && q.getBboxW() != null && q.getBboxH() != null) {
            bbox = new Bbox(q.getBboxX().doubleValue(), q.getBboxY().doubleValue(),
                    q.getBboxW().doubleValue(), q.getBboxH().doubleValue());
        }
        return new QuestionVo(
                q.getId(),
                q.getSeq(),
                q.getQuestionNo(),
                q.getQuestionType(),
                q.getStem(),
                q.getStudentAnswer(),
                q.getCorrectAnswer(),
                Integer.valueOf(1).equals(q.getIsCorrect()),
                q.getConfidence() == null ? null : q.getConfidence().doubleValue(),
                q.getWrongReason(),
                q.getAnalysis(),
                q.getKnowledgePoints() == null ? List.of() : q.getKnowledgePoints(),
                q.getDifficulty(),
                Integer.valueOf(1).equals(q.getNeedsCheck()),
                bbox,
                cropImageUrl);
    }
}
