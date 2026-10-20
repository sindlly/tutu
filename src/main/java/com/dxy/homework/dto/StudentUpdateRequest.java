package com.dxy.homework.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 更新孩子档案。字段可空，空值不覆盖。 */
public record StudentUpdateRequest(
        @Size(max = 12, message = "称呼最长 12 个字") String name,
        @Min(value = 1, message = "年级范围 1-12") @Max(value = 12, message = "年级范围 1-12") Integer grade,
        @Size(max = 64, message = "学校名最长 64 字") String school
) {
}
