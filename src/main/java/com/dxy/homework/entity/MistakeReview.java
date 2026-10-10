package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 错题复习打卡记录。 */
@Data
@TableName("t_mistake_review")
public class MistakeReview {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long mistakeId;
    private Long userId;

    /** 1答对 0答错 */
    private Integer result;

    private Integer costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
