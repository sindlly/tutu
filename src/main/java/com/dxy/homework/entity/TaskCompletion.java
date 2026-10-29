package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 任务打卡记录。每天每任务最多一条（uk_task_date 兜住并发）。 */
@Data
@TableName("t_task_completion")
public class TaskCompletion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private Long studentId;
    private LocalDate completeDate;
    private Integer pointsAwarded;

    /** 打卡凭证图片（t_file.id，可空）。打卡时已校验归属=打卡人。 */
    private Long imageFileId;

    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
