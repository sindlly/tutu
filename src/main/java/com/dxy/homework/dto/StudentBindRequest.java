package com.dxy.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 凭孩子编号绑定。 */
public record StudentBindRequest(
        @NotBlank(message = "孩子编号不能为空") @Size(max = 6, message = "孩子编号是 6 位") String code
) {
}
