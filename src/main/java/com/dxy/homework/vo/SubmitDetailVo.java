package com.dxy.homework.vo;

import java.util.List;

/**
 * 任务详情。
 *
 * @param images 原图的可读地址（按页序），带时效；结果页用它做定位叠加
 */
public record SubmitDetailVo(
        SubmitVo submit,
        List<String> images,
        List<QuestionVo> questions
) {
}
