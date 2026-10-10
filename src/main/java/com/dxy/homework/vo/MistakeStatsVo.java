package com.dxy.homework.vo;

import java.util.List;

/** 错题统计。用于"我的"页与学情概览。 */
public record MistakeStatsVo(
        long total,
        long active,
        long mastered,
        long reviewDueToday,
        List<CountItem> bySubject,
        List<CountItem> byKnowledge
) {

    public record CountItem(String name, long count) {
    }
}
