package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** AI 调用日志。request_id 用于与账单对账。 */
@Data
@TableName("t_ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submitId;
    private String scene;
    private String model;
    private String requestId;
    private Integer httpStatus;
    private Integer latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer cacheHitTokens;
    private BigDecimal cost;
    private Integer retryNo;
    private String errorCode;
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
