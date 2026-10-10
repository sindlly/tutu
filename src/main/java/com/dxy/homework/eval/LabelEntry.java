package com.dxy.homework.eval;

/**
 * 一道题的人工标注。
 *
 * @param stemKey       题干，已规范化，作为跨次运行的匹配键
 * @param rawStem       题干原文，仅用于报告展示
 * @param expectAnswer  学生真实笔迹答案；为 null 表示该题不参与识别层考核
 * @param expectCorrect 学生真实对错（true=做对）；为 null 表示该题不参与判分层考核
 * @param note          备注
 */
public record LabelEntry(
        String stemKey,
        String rawStem,
        String expectAnswer,
        Boolean expectCorrect,
        String note
) {
}
