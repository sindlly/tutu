package com.dxy.homework.vo;

import java.util.List;

/**
 * 某孩子今天的任务面板。
 *
 * @param date    日期 yyyy-MM-dd，前端展示「今天」用
 * @param tasks   启用中的任务，含今日打卡状态
 * @param summary 积分与完成度概览
 */
public record TaskTodayVo(
        String date,
        List<Item> tasks,
        Summary summary
) {

    public record Item(
            Long id,
            String title,
            Integer points,
            boolean completed,
            String completedAt,
            /** 任务详情图 URL（内容在图上的任务；无图为 null，服务端 presign 短时效） */
            String imageUrl
    ) {
    }

    public record Summary(
            int balance,
            int todayEarned,
            int todayCompleted,
            int todayTotal
    ) {
    }
}
