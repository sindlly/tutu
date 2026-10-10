package com.dxy.homework.service;

import com.dxy.homework.model.GradedQuestion;
import com.dxy.homework.model.GradedQuestion.RegionHint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 模型输出的容错解析。
 *
 * <p>即使开了 JSON Output，实际仍会遇到 ```json 包裹、字段名漂移、布尔值写成 "对"/"true"、
 * 数字写成字符串等情况。这里统一做兜底，避免"解析失败"被误判成"模型判错"。
 */
@Component
public class GradeResultParser {

    private final ObjectMapper objectMapper;

    public GradeResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode parse(String content) {
        String cleaned = stripWrapper(content);
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ex) {
            throw new IllegalArgumentException("模型输出不是合法 JSON：" + abbreviate(content), ex);
        }
    }

    public List<GradedQuestion> toQuestions(JsonNode root) {
        JsonNode array = firstArray(root, "qs", "questions", "list");
        if (array == null) {
            return List.of();
        }
        List<GradedQuestion> questions = new ArrayList<>();
        int seq = 0;
        for (JsonNode node : array) {
            seq++;
            questions.add(new GradedQuestion(
                    defaultText(firstText(node, "n", "no", "question_no", "number", "index"), String.valueOf(seq)),
                    firstInt(node, "pg", "page", "page_no"),
                    defaultText(firstText(node, "t", "type", "question_type"), "other"),
                    firstText(node, "q", "stem", "question", "content", "title"),
                    firstText(node, "sa", "student_answer", "studentAnswer", "answer"),
                    firstText(node, "ca", "correct_answer", "right_answer", "correctAnswer"),
                    firstBool(node, "ok", "is_correct", "correct", "right"),
                    firstDouble(node, "c", "confidence", "score"),
                    firstText(node, "wr", "wrong_reason", "reason", "error_reason"),
                    firstText(node, "an", "analysis", "explanation", "explain"),
                    firstStringList(node, "kp", "knowledge_points", "knowledge", "points"),
                    firstInt(node, "d", "difficulty", "level"),
                    firstBool(node, "hc", "needs_human_check", "need_check", "uncertain"),
                    firstText(node, "rm", "remark", "note"),
                    readRegion(firstNode(node, "r", "region_hint"))));
        }
        return questions;
    }

    /** 字段名兼容：提示词为省 token 用短名，历史结果用长名，两边都要能读。 */
    private JsonNode firstNode(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return MissingNode.getInstance();
    }

    private JsonNode firstArray(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isArray()) {
                return value;
            }
        }
        return null;
    }

    /** 去掉 Markdown 代码块包裹，并截取第一个 { 到最后一个 }。 */
    private String stripWrapper(String content) {
        if (content == null) {
            return "";
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLineBreak = text.indexOf('\n');
            if (firstLineBreak > 0) {
                text = text.substring(firstLineBreak + 1);
            }
            int closing = text.lastIndexOf("```");
            if (closing >= 0) {
                text = text.substring(0, closing);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return text;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isValueNode() && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private Integer firstInt(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                try {
                    return (int) Double.parseDouble(value.asText().trim());
                } catch (NumberFormatException ignored) {
                    // 继续看下一个候选字段
                }
            }
        }
        return null;
    }

    private Double firstDouble(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                String text = value.asText().trim();
                boolean percent = text.endsWith("%");
                if (percent) {
                    text = text.substring(0, text.length() - 1).trim();
                }
                try {
                    double parsed = Double.parseDouble(text);
                    if (percent) {
                        parsed = parsed / 100.0;
                    }
                    // 有些模型会把 0.95 写成 95
                    if (parsed > 1.0 && parsed <= 100.0) {
                        parsed = parsed / 100.0;
                    }
                    return parsed;
                } catch (NumberFormatException ignored) {
                    // 继续看下一个候选字段
                }
            }
        }
        return null;
    }

    /** 兼容 true/false、1/0、"对"/"错"、"正确"/"错误"、"√"/"×" 等写法；无法判定时返回 null。 */
    private Boolean firstBool(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isNumber()) {
                return value.asInt() != 0;
            }
            if (value.isTextual()) {
                String text = value.asText().trim();
                if (text.isEmpty()) {
                    continue;
                }
                String lower = text.toLowerCase();
                if (lower.equals("true") || lower.equals("yes") || lower.equals("1")
                        || text.equals("对") || text.equals("正确") || text.equals("是") || text.equals("√")) {
                    return Boolean.TRUE;
                }
                if (lower.equals("false") || lower.equals("no") || lower.equals("0")
                        || text.equals("错") || text.equals("错误") || text.equals("否") || text.equals("×")) {
                    return Boolean.FALSE;
                }
            }
        }
        return null;
    }

    private List<String> firstStringList(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isArray()) {
                List<String> result = new ArrayList<>();
                for (JsonNode item : value) {
                    if (item.isValueNode() && !item.asText().isBlank()) {
                        result.add(item.asText().trim());
                    }
                }
                return result;
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return Arrays.stream(value.asText().split("[,，、;；/]"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        }
        return List.of();
    }

    /**
     * 读取题目位置。模型有时返回 0-1 归一化坐标，有时返回百分比，这里统一成 0-1。
     * 该坐标仅作裁剪兜底，不保证精确，后续需要配合原图裁剪结果人工微调。
     */
    private RegionHint readRegion(JsonNode node) {
        Double x = firstDouble(node, "x", "left");
        Double y = firstDouble(node, "y", "top");
        Double w = firstDouble(node, "w", "width");
        Double h = firstDouble(node, "h", "height");
        if (x == null || y == null || w == null || h == null) {
            return null;
        }
        boolean looksLikePercent = x > 1.0 || y > 1.0 || w > 1.0 || h > 1.0;
        if (looksLikePercent) {
            x = x / 100.0;
            y = y / 100.0;
            w = w / 100.0;
            h = h / 100.0;
        }
        return new RegionHint(x, y, w, h);
    }

    private String defaultText(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replaceAll("\\s+", " ");
        return oneLine.length() <= 500 ? oneLine : oneLine.substring(0, 500) + "...";
    }
}
