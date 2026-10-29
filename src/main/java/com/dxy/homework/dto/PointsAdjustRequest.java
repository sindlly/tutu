package com.dxy.homework.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 家长手动调整积分。正数=奖励，负数=兑换消耗。
 * 消耗（负数）必须写清楚兑换了什么，流水里才有意义。
 */
public record PointsAdjustRequest(
        @NotNull(message = "孩子不能为空") Long studentId,
        @NotNull(message = "变动值不能为空") @Min(value = -9999, message = "单次最多扣 9999") @Max(value = 9999, message = "单次最多加 9999") Integer delta,
        @Size(max = 128, message = "备注最长 128 字") String reason
) {
}
