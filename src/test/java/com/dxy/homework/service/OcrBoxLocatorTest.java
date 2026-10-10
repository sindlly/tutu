package com.dxy.homework.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dxy.homework.config.OcrProperties;
import com.dxy.homework.model.GradedQuestion;
import com.dxy.homework.model.OcrLine;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 定位算法的验证。
 *
 * <p>为什么要单独写测试：腾讯云 OCR 要花真金白银、且依赖子账号权限，不能每次调算法都打一次接口。
 * 这里用构造出来的行坐标把算法钉死 —— 尤其是"宁可不给框，也不给错框"这条底线。
 */
class OcrBoxLocatorTest {

    private static final double MIN_MATCH = 0.34;

    private final OcrBoxLocator locator = new OcrBoxLocator(new OcrProperties(
            true, true, "", "", "ap-guangzhou", "ocr.tencentcloudapi.com",
            "GeneralAccurateOCR", "2018-11-19", true,
            Duration.ofSeconds(10), Duration.ofSeconds(30), MIN_MATCH, 8));

    /** 一页口算：一行一题，行高 0.03，行距 0.01。 */
    private List<OcrLine> page() {
        return List.of(
                line("口算", 0.02, 0.03),
                line("12+7=19", 0.06, 0.03),
                line("25-8=18", 0.10, 0.03),
                line("9×6=54", 0.14, 0.03),
                line("30+15=45", 0.18, 0.03));
    }

    @Test
    void 按题干匹配到对应行并只框住那一题() {
        List<GradedQuestion> questions = List.of(
                question("1", "12+7=", "19"),
                question("2", "25-8=", "18"),
                question("3", "9×6=", "54"));

        Map<Integer, OcrBoxLocator.Box> boxes = locator.locate(page(), questions);

        assertEquals(3, boxes.size());
        OcrBoxLocator.Box second = boxes.get(2);
        assertNotNull(second);
        assertEquals(0.10, round(second.y()), "锚点应落在第 2 题所在行");
        assertEquals(0.03, round(second.h()), "只有一行，不该把下一题框进来");
    }

    @Test
    void 题干跨两行时用下一题锚点当边界() {
        List<OcrLine> lines = List.of(
                line("12+7=19", 0.05, 0.03),
                line("小明有25颗糖，吃了8颗，还剩几颗？", 0.10, 0.03),
                line("答：18颗", 0.14, 0.03),
                line("9×6=54", 0.20, 0.03));

        List<GradedQuestion> questions = List.of(
                question("1", "12+7=", "19"),
                question("2", "小明有25颗糖，吃了8颗，还剩几颗？", "18"),
                question("3", "9×6=", "54"));

        Map<Integer, OcrBoxLocator.Box> boxes = locator.locate(lines, questions);

        OcrBoxLocator.Box second = boxes.get(2);
        assertNotNull(second);
        assertEquals(0.10, round(second.y()), "从第 2 题的题干行开始");
        assertEquals(0.07, round(second.h()), "应包含作答行，但不越过第 3 题");
    }

    @Test
    void 匹配不上时宁可不给框() {
        List<GradedQuestion> questions = List.of(
                question("1", "完全不存在的题目内容XYZ", "999"));

        Map<Integer, OcrBoxLocator.Box> boxes = locator.locate(page(), questions);

        assertTrue(boxes.isEmpty(), "匹配分低于阈值时必须放弃，不能随便框一块");
    }

    @Test
    void 锚点必须单调_顺序错乱时不给框() {
        // 后一道题的题干出现在前面：说明匹配串了，直接放弃而不是硬框一处
        List<GradedQuestion> questions = List.of(
                question("1", "30+15=", "45"),
                question("2", "12+7=", "19"));

        Map<Integer, OcrBoxLocator.Box> boxes = locator.locate(page(), questions);

        assertNotNull(boxes.get(1));
        assertNull(boxes.get(2), "第 2 题的锚点落在第 1 题之前，应被单调约束拦下");
    }

    private static GradedQuestion question(String no, String stem, String answer) {
        return new GradedQuestion(no, 1, "calc", stem, answer, null, null, null,
                null, null, null, null, null, null, null);
    }

    private static OcrLine line(String text, double y, double h) {
        return new OcrLine(text, 0.05, y, 0.90, h, null, 1);
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
