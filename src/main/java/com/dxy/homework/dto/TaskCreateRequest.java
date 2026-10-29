package com.dxy.homework.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 添加每日任务。imageFileId 可选：任务详情图（内容在图上的任务）。 */
public record TaskCreateRequest(
        @NotNull(message = "孩子不能为空") Long studentId,
        @NotBlank(message = "任务名不能为空") @Size(max = 64, message = "任务名最长 64 字") String title,
        @NotNull(message = "积分不能为空") @Min(value = 1, message = "积分最少 1") @Max(value = 999, message = "积分最多 999") Integer points,
        Long imageFileId
) {
}
