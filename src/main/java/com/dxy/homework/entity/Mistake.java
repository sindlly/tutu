package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 错题本条目。题干存快照，避免原题被改动后历史失真。 */
@Data
@TableName("t_mistake")
public class Mistake {

    public static final int STATUS_ACTIVE = 0;
    public static final int STATUS_MASTERED = 1;
    public static final int STATUS_REMOVED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long studentId;
    private Long questionId;
    private Long submitId;

    private String subject;
    private Integer grade;
    private Integer questionType;

    private String stemSnapshot;
    private String answerSnapshot;
    private String knowledgeKey;

    private Long cropFileId;
    private Long cardFileId;

    /** 0-5 掌握度 */
    private Integer masteryLevel;

    private Integer reviewCount;
    private Integer rightStreak;
    private LocalDateTime lastReviewAt;
    private LocalDateTime nextReviewAt;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
