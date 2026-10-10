package com.dxy.homework.service;

import com.dxy.homework.config.OcrProperties;
import com.dxy.homework.model.GradedQuestion;
import com.dxy.homework.model.OcrLine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 用 OCR 的行级坐标 + 题干文本匹配，算出每道题在原图中的区域。
 *
 * <p>这是"OCR 选框"的核心。思路很朴素：腾讯云会给**每一行文字**返回一个矩形，
 * 而模型已经给出了每道题的题干文本。拿题干去和这些行做文本匹配，找到最像的那一行当锚点，
 * 再往下扩到下一题的锚点之前，就得到了这道题的完整区域。
 *
 * <p>三条刻意的保守设计：
 * <ol>
 *   <li>匹配分低于阈值就<b>不给框</b>。宁可没有裁剪图，也不要裁出一道别的题 ——
 *       展示一张错误区域的截图比不展示更糟。</li>
 *   <li>锚点强制单调。题目是按卷面顺序返回的，后一道题的锚点不可能在前一道之前，
 *       违反就说明匹配串了，直接放弃。</li>
 *   <li>下一题锚点存在时，用它当右边界；不存在才退化成行距启发式，并限制最大行数。</li>
 * </ol>
 */
@Component
public class OcrBoxLocator {

    /** 归一化坐标下的最小边长，太小说明框不可信 */
    private static final double MIN_SIDE = 0.015;
    /** 行距超过"上一行平均行高 × 该倍数"就认为换行分段了 */
    private static final double GAP_FACTOR = 1.6;

    private final OcrProperties properties;

    public OcrBoxLocator(OcrProperties properties) {
        this.properties = properties;
    }

    /** 归一化区域，左上角原点。 */
    public record Box(double x, double y, double w, double h) {
    }

    /**
     * @param lines     全部页的 OCR 行，按页序、页内从上到下排列
     * @param questions 模型返回的题目，顺序即卷面顺序
     * @return key 是题目在 {@code questions} 中的序号（从 1 开始，与落库 seq 对齐）
     */
    public Map<Integer, Box> locate(List<OcrLine> lines, List<GradedQuestion> questions) {
        Map<Integer, Box> boxes = new HashMap<>();
        if (lines == null || lines.isEmpty() || questions == null || questions.isEmpty()) {
            return boxes;
        }

        int[] anchors = new int[questions.size()];
        for (int i = 0; i < anchors.length; i++) {
            anchors[i] = -1;
        }

        int searchFrom = 0;
        for (int i = 0; i < questions.size(); i++) {
            int best = -1;
            double bestScore = 0;
            for (int j = searchFrom; j < lines.size(); j++) {
                double score = score(questions.get(i), lines.get(j));
                if (score > bestScore) {
                    bestScore = score;
                    best = j;
                }
            }
            if (best < 0 || bestScore < properties.minMatchScore()) {
                continue;
            }
            anchors[i] = best;
            searchFrom = best + 1;
        }

        for (int i = 0; i < anchors.length; i++) {
            if (anchors[i] < 0) {
                continue;
            }
            int end = expand(lines, anchors, i);
            Box box = union(lines, anchors[i], end);
            if (box != null) {
                boxes.put(i + 1, box);
            }
        }
        return boxes;
    }

    /** 从锚点行向下扩，返回应包含的最后一行的下标。 */
    private int expand(List<OcrLine> lines, int[] anchors, int index) {
        int start = anchors[index];

        int nextAnchor = -1;
        for (int k = index + 1; k < anchors.length; k++) {
            if (anchors[k] >= 0) {
                nextAnchor = anchors[k];
                break;
            }
        }
        // 下一题的锚点是硬边界；没有就只能靠行距猜，并限制最多扩几行
        int limit = nextAnchor > 0 ? nextAnchor - 1 : lines.size() - 1;
        int maxEnd = Math.min(limit, start + Math.max(0, properties.maxLinesPerQuestion()) - 1);

        int end = start;
        double lineHeight = lines.get(start).h();
        for (int j = start + 1; j <= maxEnd; j++) {
            OcrLine prev = lines.get(j - 1);
            OcrLine cur = lines.get(j);
            if (cur.pageSeq() != prev.pageSeq()) {
                break;
            }
            double gap = cur.y() - prev.bottom();
            if (gap > lineHeight * GAP_FACTOR) {
                break;
            }
            end = j;
            lineHeight = (lineHeight + cur.h()) / 2.0;
        }
        return end;
    }

    private Box union(List<OcrLine> lines, int start, int end) {
        double minX = 1.0;
        double minY = 1.0;
        double maxX = 0.0;
        double maxY = 0.0;
        for (int i = start; i <= end; i++) {
            OcrLine line = lines.get(i);
            minX = Math.min(minX, line.x());
            minY = Math.min(minY, line.y());
            maxX = Math.max(maxX, line.right());
            maxY = Math.max(maxY, line.bottom());
        }
        double w = maxX - minX;
        double h = maxY - minY;
        if (w < MIN_SIDE || h < MIN_SIDE) {
            return null;
        }
        return new Box(clamp01(minX), clamp01(minY), clamp01(w), clamp01(h));
    }

    /** 题干 / 答案与某一行文字的匹配分，0-1。 */
    private double score(GradedQuestion question, OcrLine line) {
        String text = normalize(line.text());
        if (text.isEmpty()) {
            return 0;
        }
        String stem = normalize(question.stem());
        String answer = normalize(question.studentAnswer());

        double score = 0;
        if (!stem.isEmpty()) {
            score = similarity(stem, text);
        }
        // 题干匹配不上时退而求其次找答案。手写答案常常是卷面上最显眼的那一行，
        // 但短答案（"42"）误匹配风险高，所以要求长度 ≥ 2 且打折
        if (score < 0.2 && answer.length() >= 2) {
            score = similarity(answer, text) * 0.8;
        }
        String no = digits(question.no());
        if (!no.isEmpty() && text.startsWith(no)) {
            score += 0.15;
        }
        return Math.min(score, 1.0);
    }

    /** 包含关系直接给满分，否则用二元组 Dice 系数 —— 对 OCR 少量错字不敏感。 */
    private double similarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.contains(b) || b.contains(a)) {
            return 1.0;
        }
        if (a.length() < 2 || b.length() < 2) {
            return a.equals(b) ? 1.0 : 0;
        }
        Map<String, Integer> grams = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            grams.merge(a.substring(i, i + 2), 1, Integer::sum);
        }
        double hit = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String gram = b.substring(i, i + 2);
            Integer count = grams.get(gram);
            if (count != null && count > 0) {
                hit++;
                grams.put(gram, count - 1);
            }
        }
        return 2 * hit / (a.length() - 1 + b.length() - 1);
    }

    /** 只保留字母、数字和汉字，去掉标点空格并转小写 —— OCR 的标点识别很不稳定。 */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private String digits(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** 供调试：把某一页的行拼成可读文本。 */
    public static List<String> preview(List<OcrLine> lines, int limit) {
        List<String> out = new ArrayList<>();
        if (lines == null) {
            return out;
        }
        for (OcrLine line : lines.subList(0, Math.min(limit, lines.size()))) {
            out.add(String.format("p%d y=%.3f h=%.3f | %s", line.pageSeq(), line.y(), line.h(), line.text()));
        }
        return out;
    }
}
