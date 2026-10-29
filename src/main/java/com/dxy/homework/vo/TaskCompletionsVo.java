package com.dxy.homework.vo;

import java.time.LocalDate;
import java.util.List;

/**
 * 任务打卡历史（按天/周/月查询）。
 *
 * @param range   day / week / month
 * @param start   查询区间起（含），yyyy-MM-dd
 * @param end     查询区间止（含），yyyy-MM-dd
 * @param items   打卡记录，新在前
 * @param summary 区间完成数与积分合计
 */
public record TaskCompletionsVo(
        String range,
        LocalDate start,
        LocalDate end,
        List<Item> items,
        Summary summary
) {

    /**
     * @param id             打卡记录 id
     * @param taskId         任务 id（可能已归档，标题仍可显示）
     * @param taskTitle      任务名快照
     * @param pointsAwarded  入账积分快照
     * @param completeDate   完成日期 yyyy-MM-dd
     * @param completedAt    完成时间 HH:mm
     * @param imageUrl       任务详情图 URL（无图为 null；服务端 presign，短时效）
     */
    public record Item(
            Long id,
            Long taskId,
            String taskTitle,
            Integer pointsAwarded,
            String completeDate,
            String completedAt,
            String imageUrl
    ) {
    }

    public record Summary(
            int count,
            int earned
    ) {
    }
}
