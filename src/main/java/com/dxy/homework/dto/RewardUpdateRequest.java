package com.dxy.homework.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 更新奖励。字段可空，空值不覆盖。status：1 上架 2 下架。 */
public record RewardUpdateRequest(
        @Size(max = 20, message = "奖励名最长 20 字") String title,
        @Min(value = 1, message = "积分最少 1") @Max(value = 9999, message = "积分最多 9999") Integer cost,
        Integer status,
        String icon
) {
}
