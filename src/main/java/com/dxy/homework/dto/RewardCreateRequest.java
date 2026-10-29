package com.dxy.homework.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 添加可兑换奖励。icon 可选：预制卡通图标 key。 */
public record RewardCreateRequest(
        @NotNull(message = "孩子不能为空") Long studentId,
        @NotBlank(message = "奖励名不能为空") @Size(max = 20, message = "奖励名最长 20 字") String title,
        @NotNull(message = "所需积分不能为空") @Min(value = 1, message = "积分最少 1") @Max(value = 9999, message = "积分最多 9999") Integer cost,
        String icon
) {
}
