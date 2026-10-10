package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 批改出的单道题。 */
@Data
@TableName(value = "t_question", autoResultMap = true)
public class Question {

    public static final int TYPE_CHOICE = 1;
    public static final int TYPE_MULTI_CHOICE = 2;
    public static final int TYPE_JUDGE = 3;
    public static final int TYPE_FILL = 4;
    public static final int TYPE_CALC = 5;
    public static final int TYPE_SOLVE = 6;
    public static final int TYPE_ESSAY = 7;
    public static final int TYPE_OTHER = 8;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submitId;
    private Integer seq;

    /** 卷面题号，如 "口算15"。跨次运行不稳定，不能当业务主键 */
    private String questionNo;

    private Integer questionType;
    private String stem;
    private String studentAnswer;
    private String correctAnswer;

    /** 1对 0错 */
    private Integer isCorrect;

    private BigDecimal confidence;
    private String wrongReason;
    private String analysis;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> knowledgePoints;

    private Integer difficulty;
    private Integer pageSeq;

    /** 归一化 0-1 坐标，仅用于裁剪兜底，不保证精确 */
    private BigDecimal bboxX;
    private BigDecimal bboxY;
    private BigDecimal bboxW;
    private BigDecimal bboxH;

    /** 1AI 给出 2OCR 匹配 3用户手动 */
    private Integer bboxSource;

    private Long cropFileId;
    private Integer userCorrected;
    private Integer needsCheck;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
