package com.dxy.homework.eval;

import com.dxy.homework.common.Csv;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 从一次批改结果导出标注草稿。
 *
 * <p>为什么要有这个：一页作业 45 道题，纯手工录入标注没人愿意做。这里用模型自己的读数
 * 预填，人工只需要核对并修正不对的行 —— 但**必须真的核对**，否则拿模型输出去评测模型
 * 等于自证，评测结果毫无意义。
 */
@Component
public class LabelDraftExporter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ObjectMapper objectMapper;

    public LabelDraftExporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path export(Path resultJson, Path destDir) throws IOException {
        JsonNode root = objectMapper.readTree(Files.readString(resultJson, StandardCharsets.UTF_8));
        JsonNode questions = root.path("qs");
        if (!questions.isArray()) {
            questions = root.path("questions");
        }
        if (!questions.isArray()) {
            throw new IOException("结果文件里找不到题目数组（qs / questions）：" + resultJson);
        }

        String baseName = stripExtension(resultJson.getFileName().toString());
        Files.createDirectories(destDir);
        Path target = destDir.resolve(baseName + ".labels-" + LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(STAMP) + ".csv");

        List<String> lines = new ArrayList<>();
        lines.add("# 标注草稿：由模型输出预填，必须逐题核对后再用于评测");
        lines.add("# stem            = 题干，跨次运行的匹配键，改动它会导致匹配失败");
        lines.add("# expect_answer   = 学生真实笔迹答案（不是正确答案！）。留空表示该题不考核识别");
        lines.add("# expect_correct  = 学生真实对错，true/false。留空表示该题不考核判分");
        lines.add("# note            = 备注，可留空");
        lines.add("stem,expect_answer,expect_correct,note");

        for (JsonNode node : questions) {
            String stem = text(node, "q", "stem", "question");
            String answer = text(node, "sa", "student_answer", "answer");
            Boolean correct = bool(node, "ok", "is_correct", "correct");
            lines.add(Csv.escape(stem) + ","
                    + Csv.escape(answer) + ","
                    + (correct == null ? "" : correct.toString()) + ","
                    + Csv.escape("核对后删除本备注"));
        }

        Files.write(target, lines, StandardCharsets.UTF_8);
        return target;
    }

    private String text(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isValueNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private Boolean bool(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isTextual()) {
                String v = value.asText().trim().toLowerCase();
                if (v.equals("true") || v.equals("对")) {
                    return Boolean.TRUE;
                }
                if (v.equals("false") || v.equals("错")) {
                    return Boolean.FALSE;
                }
            }
        }
        return null;
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
