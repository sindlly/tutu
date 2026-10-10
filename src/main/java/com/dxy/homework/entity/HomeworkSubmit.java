package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 一次批改任务。status 是异步流水线的状态机字段。 */
@Data
@TableName("t_homework_submit")
public class HomeworkSubmit {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_REVIEWING = 1;
    public static final int STATUS_GRADING = 2;
    public static final int STATUS_SUCCESS = 3;
    public static final int STATUS_FAILED = 4;
    public static final int STATUS_CANCELED = 5;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户端幂等号，唯一索引兜底防重复提交 */
    private String bizNo;

    private Long userId;
    private Long studentId;
    private String subject;
    private Integer grade;

    private Integer status;
    private Integer imageCount;

    private Integer totalCount;
    private Integer rightCount;
    private Integer wrongCount;
    private BigDecimal score;

    private Integer durationMs;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private BigDecimal cost;

    private String errorCode;
    private String errorMsg;
    private Integer retryCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;
}
