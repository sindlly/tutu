package com.dxy.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 「每日一话」生成请求。requirements 为用户输入的主题/风格/长度等自由要求。 */
public record EssayGenerateRequest(
        @NotNull(message = "孩子不能为空") Long studentId,
        @NotBlank(message = "先说说想要什么样的文章") @Size(max = 200, message = "要求最多 200 字") String requirements
) {
}
