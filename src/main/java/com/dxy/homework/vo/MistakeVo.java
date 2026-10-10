package com.dxy.homework.vo;

import com.dxy.homework.entity.Mistake;
import java.time.LocalDateTime;

/**
 * 错题列表项。
 *
 * @param cardImageUrl 错题卡片地址（私有读、带时效）。列表页以卡片图为主视觉
 */
public record MistakeVo(
        Long id,
        String subject,
        Integer questionType,
        String stem,
        String studentAnswer,
        String knowledgeKey,
        Integer masteryLevel,
        Integer reviewCount,
        LocalDateTime nextReviewAt,
        Integer status,
        Long submitId,
        String cardImageUrl
) {

    public static MistakeVo from(Mistake mistake, String cardImageUrl) {
        return new MistakeVo(
                mistake.getId(),
                mistake.getSubject(),
                mistake.getQuestionType(),
                mistake.getStemSnapshot(),
                mistake.getAnswerSnapshot(),
                mistake.getKnowledgeKey(),
                mistake.getMasteryLevel(),
                mistake.getReviewCount(),
                mistake.getNextReviewAt(),
                mistake.getStatus(),
                mistake.getSubmitId(),
                cardImageUrl);
    }
}
