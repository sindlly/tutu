package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 每日任务定义。家长动态添加，挂孩子名下（数据主权与错题一致）。 */
@Data
@TableName("t_task")
public class Task {

    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_ARCHIVED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;
    private Long userId;
    private String title;
    private Integer points;

    /** 任务详情图（t_file.id，可空）：内容在图上的任务（作业页照片、手写任务卡）。 */
    private Long imageFileId;

    private Integer sortNo;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
