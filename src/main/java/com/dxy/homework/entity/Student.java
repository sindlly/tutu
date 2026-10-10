package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 学生（一个家长可管理多个孩子）。默认不采集真实姓名，只存称呼。 */
@Data
@TableName("t_student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属家长，所有查询都必须带上这个条件做越权防护 */
    private Long userId;

    private String name;

    /** 1-12 年级 */
    private Integer grade;

    private String school;
    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
