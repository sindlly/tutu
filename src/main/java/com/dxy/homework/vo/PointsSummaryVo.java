package com.dxy.homework.vo;

/** 孩子的积分概览。 */
public record PointsSummaryVo(
        int balance,
        int todayEarned,
        int totalEarned,
        int totalSpent
) {
}
