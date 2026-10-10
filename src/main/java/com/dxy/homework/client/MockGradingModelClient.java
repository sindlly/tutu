package com.dxy.homework.client;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 无密钥时的占位实现，用于验证「图片预处理 → 提示词 → JSON 解析 → 报告落盘」整条链路是否通畅。
 * <p>返回内容是固定样例，与真实模型无关，<b>不能用于评估准确率</b>。
 */
@Component
@Primary
@ConditionalOnProperty(name = "demo.mock", havingValue = "true")
public class MockGradingModelClient implements GradingModelClient {

    private static final Logger log = LoggerFactory.getLogger(MockGradingModelClient.class);

    private static final String SAMPLE_JSON = """
            {
              "subject": "math",
              "page_count": 1,
              "questions": [
                {
                  "no": "1",
                  "page": 1,
                  "type": "calc",
                  "stem": "计算：48 ÷ 6 =",
                  "student_answer": "6",
                  "correct_answer": "8",
                  "is_correct": false,
                  "confidence": 0.96,
                  "wrong_reason": "把 48÷6 算成了 6，商与除数混淆",
                  "analysis": "48÷6=8。验算：6×8=48，所以商是 8。",
                  "knowledge_points": ["表内除法", "除法验算"],
                  "difficulty": 1,
                  "needs_human_check": false,
                  "region_hint": {"x": 0.12, "y": 0.18, "w": 0.76, "h": 0.09}
                },
                {
                  "no": "2",
                  "page": 1,
                  "type": "fill",
                  "stem": "3 个十和 5 个一组成的数是（  ）。",
                  "student_answer": "35",
                  "correct_answer": "35",
                  "is_correct": true,
                  "confidence": 0.99,
                  "wrong_reason": "",
                  "analysis": "3 个十是 30，5 个一是 5，30+5=35。",
                  "knowledge_points": ["100 以内数的组成"],
                  "difficulty": 1,
                  "needs_human_check": false,
                  "region_hint": {"x": 0.10, "y": 0.34, "w": 0.80, "h": 0.08}
                },
                {
                  "no": "3",
                  "page": 1,
                  "type": "solve",
                  "stem": "小明有 24 元，买了 3 本同样的笔记本，每本 7 元，还剩多少元？",
                  "student_answer": "24-7=17（元）",
                  "correct_answer": "3 元",
                  "is_correct": false,
                  "confidence": 0.72,
                  "wrong_reason": "思路对、列式错：只减了一本的价钱，没有先算 3 本的总价",
                  "analysis": "先求 3 本总价 7×3=21 元，再求剩余 24-21=3 元。",
                  "knowledge_points": ["两步计算应用题"],
                  "difficulty": 3,
                  "needs_human_check": true,
                  "region_hint": {"x": 0.08, "y": 0.52, "w": 0.84, "h": 0.20}
                }
              ],
              "unreadable_parts": ["第 4 题被橡皮擦涂改，答案无法确认"]
            }
            """;

    @Override
    public ChatCallResult chatWithImages(String systemPrompt, String userText, List<String> imageDataUrls) {
        log.warn("当前为 mock 模式，返回固定样例数据，请勿用于评估准确率");
        return new ChatCallResult(SAMPLE_JSON, "", "mock-request-id", "mock-model",
                2048, 512, 1500, 12L, 1, SAMPLE_JSON);
    }
}
