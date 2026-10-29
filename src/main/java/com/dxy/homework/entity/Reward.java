package com.dxy.homework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 奖励中心：家长预设的可兑换奖励（积分够就能换）。 */
@Data
@TableName("t_reward")
public class Reward {

    public static final int STATUS_ON = 1;
    public static final int STATUS_OFF = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;
    private Long userId;
    private String title;

    /** 预制卡通图标 key（前端 assets/rewards/{icon}.png），空则用默认礼物图。 */
    private String icon;

    private Integer cost;
    private Integer status;
    private Integer sortNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
