package com.dxy.homework.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 更新任务。字段可空，空值不覆盖（imageFileId 传值即更新任务详情图）。 */
public record TaskUpdateRequest(
        @Size(max = 64, message = "任务名最长 64 字") String title,
        @Min(value = 1, message = "积分最少 1") @Max(value = 999, message = "积分最多 999") Integer points,
        /** 1启用 2归档。空则不改 */
        Integer status,
        /** 任务详情图。空则不改 */
        Long imageFileId
) {
}
