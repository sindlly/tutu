package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 批改任务与原始图片的关联，seq 为页序。 */
@Data
@TableName("t_submit_image")
public class SubmitImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long submitId;

    /** 页序，从 1 开始 */
    private Integer seq;

    private Long fileId;
}
