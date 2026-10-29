package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 积分流水。余额 = SUM(delta)。
 *
 * <p>不做冗余「余额」字段：流水是唯一事实来源，任何对账都从它求和，
 * 冗余字段迟早会在并发下出现两个「余额」打架。
 */
@Data
@TableName("t_points_log")
public class PointsLog {

    public static final int SOURCE_TASK = 1;
    public static final int SOURCE_MANUAL = 2;
    public static final int SOURCE_REWARD = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;
    private Integer delta;
    private Integer source;
    private Long refId;
    private String reason;
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
