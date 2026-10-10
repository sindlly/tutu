package com.dxy.homework.eval;

import com.dxy.homework.common.Csv;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 人工标注集。
 *
 * <p>用"规范化后的题干"作为匹配键，而不是题号 —— 因为题号在不同运行里不稳定
 * （同一道题可能被写成"口算15"或"口算练习25"），题干是印刷体，读出来基本一致。
 */
public record LabelSet(Path source, List<LabelEntry> entries) {

    private static final String HEADER_STEM = "stem";
    private static final String HEADER_ANSWER = "expect_answer";
    private static final String HEADER_CORRECT = "expect_correct";

    /** 跳过空行与 # 注释行；按表头定位列，允许列顺序变化。 */
    public static LabelSet load(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<LabelEntry> entries = new ArrayList<>();

        List<String> header = null;
        int stemIdx = -1;
        int answerIdx = -1;
        int correctIdx = -1;
        int noteIdx = -1;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (header == null) {
                header = Csv.splitLine(rawLine);
                for (int i = 0; i < header.size(); i++) {
                    String name = header.get(i).trim().toLowerCase(Locale.ROOT);
                    switch (name) {
                        case HEADER_STEM -> stemIdx = i;
                        case HEADER_ANSWER -> answerIdx = i;
                        case HEADER_CORRECT -> correctIdx = i;
                        case "note" -> noteIdx = i;
                        default -> {
                            // 忽略未知列
                        }
                    }
                }
                if (stemIdx < 0 || answerIdx < 0 || correctIdx < 0) {
                    throw new IOException("标注文件缺少必需列，需要同时包含 stem / expect_answer / expect_correct：" + path);
                }
                continue;
            }

            List<String> fields = Csv.splitLine(rawLine);
            String rawStem = field(fields, stemIdx);
            if (rawStem.isBlank()) {
                continue;
            }
            entries.add(new LabelEntry(
                    AnswerNormalizer.normalize(rawStem),
                    rawStem.trim(),
                    blankToNull(field(fields, answerIdx)),
                    parseCorrect(field(fields, correctIdx)),
                    blankToNull(field(fields, noteIdx))));
        }

        if (entries.isEmpty()) {
            throw new IOException("标注文件里没有任何有效数据行：" + path);
        }
        return new LabelSet(path, List.copyOf(entries));
    }

    /**
     * 匹配题目。先精确匹配规范化题干，再退回唯一包含匹配。
     * 包含匹配有多个候选时返回 null（歧义不猜），交由报告列出人工处理。
     */
    public LabelEntry match(String stem) {
        String key = AnswerNormalizer.normalize(stem);
        if (key.isEmpty()) {
            return null;
        }
        for (LabelEntry entry : entries) {
            if (entry.stemKey().equals(key)) {
                return entry;
            }
        }

        LabelEntry found = null;
        for (LabelEntry entry : entries) {
            if (entry.stemKey().isEmpty()) {
                continue;
            }
            if (key.contains(entry.stemKey()) || entry.stemKey().contains(key)) {
                if (found != null) {
                    return null;
                }
                found = entry;
            }
        }
        return found;
    }

    private static String field(List<String> fields, int index) {
        return index >= 0 && index < fields.size() ? fields.get(index).trim() : "";
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Boolean parseCorrect(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "true", "1", "t", "y", "yes", "对", "正确" -> Boolean.TRUE;
            case "false", "0", "f", "n", "no", "错", "错误" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException(
                    "expect_correct 只能是 true/false（或 对/错），实际是：" + value);
        };
    }
}
