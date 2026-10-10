package com.dxy.homework.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 修改错题状态。
 *
 * @param status 0在册 1已掌握 2已移除，为空表示不改
 */
public record MistakeUpdateRequest(
        @Min(value = 0, message = "状态取值 0-2") @Max(value = 2, message = "状态取值 0-2") Integer status
) {
}
