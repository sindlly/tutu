package com.dxy.homework.vo;

/** 首页每日美句。 */
public record DailyQuoteVo(
        /** 日期 yyyy-MM-dd */
        String date,
        /** 年级（生成时的依据） */
        Integer grade,
        /** 美句内容 */
        String text
) {
}
