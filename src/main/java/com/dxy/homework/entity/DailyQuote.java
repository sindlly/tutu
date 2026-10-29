package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 每日美句：每天每个年级一句（DeepSeek 生成，符合孩子年龄）。 */
@Data
@TableName("t_daily_quote")
public class DailyQuote {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate quoteDate;
    private Integer grade;
    private String text;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
