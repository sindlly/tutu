package com.dxy.homework.report;

import com.dxy.homework.model.GradeOutcome;
import com.dxy.homework.model.GradedQuestion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 把验证结果落盘，便于人工逐题核对准确率。
 *
 * <p>产出：summary.md（汇总 + 逐题明细）、summary.csv（便于统计）、每张图的原始 JSON。
 */
@Component
public class ReportWriter {

    private static final DateTimeFormatter RUN_ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public Path write(List<GradeOutcome> outcomes, String outDir, String model, boolean thinkingEnabled) throws IOException {
        Path dir = Paths.get(outDir, LocalDateTime.now(ZONE).format(RUN_ID));
        Files.createDirectories(dir);

        int index = 0;
        for (GradeOutcome outcome : outcomes) {
            index++;
            String base = String.format("%02d-%s", index, stripExtension(outcome.file()));
            if (outcome.rawJson() != null) {
                Files.writeString(dir.resolve(base + ".json"), outcome.rawJson(), StandardCharsets.UTF_8);
            }
            if (!outcome.success()) {
                Files.writeString(dir.resolve(base + ".error.txt"),
                        String.valueOf(outcome.error()), StandardCharsets.UTF_8);
            }
            // 原始返回必须落盘：按 token 计费的是全文，而 JSON 之外可能还有模型多写的文字，
            // 只看解析出的 JSON 会低估输出成本、也看不出模型是否被 prompt 带偏。
            if (outcome.rawResponse() != null) {
                Files.writeString(dir.resolve(base + ".raw.txt"), outcome.rawResponse(), StandardCharsets.UTF_8);
            }
        }

        Files.writeString(dir.resolve("summary.md"),
                buildMarkdown(outcomes, model, thinkingEnabled), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("summary.csv"),
                buildCsv(outcomes), StandardCharsets.UTF_8);
        return dir;
    }

    private String buildMarkdown(List<GradeOutcome> outcomes, String model, boolean thinkingEnabled) {
        long successCount = outcomes.stream().filter(GradeOutcome::success).count();
        double totalCost = outcomes.stream()
                .filter(o -> o.costYuan() != null)
                .mapToDouble(GradeOutcome::costYuan)
                .sum();
        int totalQuestions = outcomes.stream().mapToInt(GradeOutcome::questionCount).sum();
        int totalWrong = outcomes.stream().mapToInt(GradeOutcome::wrongCount).sum();
        int totalNeedsCheck = outcomes.stream().mapToInt(GradeOutcome::needsCheckCount).sum();
        boolean peak = outcomes.stream().anyMatch(GradeOutcome::peakPricing);

        StringBuilder md = new StringBuilder();
        md.append("# 批改验证报告\n\n");
        md.append("- 运行时间：").append(LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        md.append("- 模型：").append(model).append("（思考模式：").append(thinkingEnabled ? "开启" : "关闭").append("）\n");
        md.append("- 图片：").append(outcomes.size()).append(" 张，成功 ").append(successCount)
                .append(" 张，失败 ").append(outcomes.size() - successCount).append(" 张\n");
        md.append("- 计价时段：").append(peak ? "高峰时段" : "空闲时段").append('\n');
        md.append("- 题目：").append(totalQuestions).append(" 题，AI 判错 ").append(totalWrong)
                .append(" 题，需人工核对 ").append(totalNeedsCheck).append(" 题\n");
        md.append("- 估算总成本：").append(String.format("%.4f", totalCost)).append(" 元\n\n");

        md.append("> 这是模型自述的结果，**不等于准确率**。请逐题与作业原件比对，重点看：\n");
        md.append("> 1）题干转写是否忠实（识别错）；2）对错判定是否与老师一致（判分错）；3）存疑题是否真的存疑。\n\n");

        md.append("## 一、按图汇总\n\n");
        md.append("| # | 文件 | 结果 | 耗时(s) | 题目 | 判错 | 存疑 | 待核对 | 输入token | 输出token | 成本(元) | 图片 |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        int index = 0;
        for (GradeOutcome o : outcomes) {
            index++;
            String imageSize = (o.imageWidth() == null) ? "-" : o.imageWidth() + "x" + o.imageHeight();
            md.append("| ").append(index)
                    .append(" | ").append(nullSafe(o.file()))
                    .append(" | ").append(o.success() ? "成功" : "失败：" + nullSafe(o.error()))
                    .append(" | ").append(String.format("%.1f", o.latencyMs() / 1000.0))
                    .append(" | ").append(o.questionCount())
                    .append(" | ").append(o.wrongCount())
                    .append(" | ").append(o.needsCheckCount())
                    .append(" | ").append(o.unknownCount())
                    .append(" | ").append(value(o.promptTokens()))
                    .append(" | ").append(value(o.completionTokens()))
                    .append(" | ").append(o.costYuan() == null ? "-" : String.format("%.4f", o.costYuan()))
                    .append(" | ").append(imageSize)
                    .append(" |\n");
        }

        md.append("\n## 二、逐题明细\n");
        index = 0;
        for (GradeOutcome o : outcomes) {
            index++;
            if (!o.success()) {
                continue;
            }
            md.append("\n### ").append(index).append(". ").append(nullSafe(o.file()))
                    .append("（").append(o.questions().size()).append(" 题）\n\n");
            if (o.questions().isEmpty()) {
                md.append("_未解析出题目，请检查 ").append(stripExtension(o.file())).append(".json_\n");
                continue;
            }
            md.append("| 题号 | 类型 | 判定 | 置信度 | 学生答案 | 正确答案 | 知识点 | 需要核对 |\n");
            md.append("|---|---|---|---|---|---|---|---|\n");
            for (GradedQuestion q : o.questions()) {
                md.append("| ").append(nullSafe(q.no()))
                        .append(" | ").append(nullSafe(q.type()))
                        .append(" | ").append(verdict(q.isCorrect(), q.needsHumanCheck()))
                        .append(" | ").append(q.confidence() == null ? "-" : String.format("%.2f", q.confidence()))
                        .append(" | ").append(cell(q.studentAnswer()))
                        .append(" | ").append(cell(q.correctAnswer()))
                        .append(" | ").append(q.knowledgePoints().isEmpty() ? "-" : String.join("、", q.knowledgePoints()))
                        .append(" | ").append(Boolean.TRUE.equals(q.needsHumanCheck()) ? "是" : "")
                        .append(" |\n");
            }

            boolean hasDetail = o.questions().stream().anyMatch(q ->
                    hasText(q.wrongReason()) || hasText(q.analysis()));
            if (hasDetail) {
                md.append("\n**判错原因与解析**\n\n");
                for (GradedQuestion q : o.questions()) {
                    if (!hasText(q.wrongReason()) && !hasText(q.analysis())) {
                        continue;
                    }
                    md.append("- 第 ").append(nullSafe(q.no())).append(" 题");
                    md.append("｜题干：").append(cell(q.stem())).append('\n');
                    if (hasText(q.wrongReason())) {
                        md.append("  - 错因：").append(q.wrongReason()).append('\n');
                    }
                    if (hasText(q.analysis())) {
                        md.append("  - 解析：").append(q.analysis()).append('\n');
                    }
                }
            }
        }
        md.append("\n---\n本报告由验证 Demo 自动生成，仅用于人工评估，不可直接作为产品批改结果。\n");
        return md.toString();
    }

    private String buildCsv(List<GradeOutcome> outcomes) {
        StringBuilder csv = new StringBuilder();
        csv.append("file,success,mode,latency_ms,question_count,wrong_count,needs_check_count,unknown_count,")
                .append("prompt_tokens,completion_tokens,cache_hit_tokens,cost_yuan,image_width,image_height,error\n");
        for (GradeOutcome o : outcomes) {
            csv.append(csvCell(o.file())).append(',')
                    .append(o.success()).append(',')
                    .append(csvCell(o.mode())).append(',')
                    .append(o.latencyMs()).append(',')
                    .append(o.questionCount()).append(',')
                    .append(o.wrongCount()).append(',')
                    .append(o.needsCheckCount()).append(',')
                    .append(o.unknownCount()).append(',')
                    .append(value(o.promptTokens())).append(',')
                    .append(value(o.completionTokens())).append(',')
                    .append(value(o.cacheHitTokens())).append(',')
                    .append(o.costYuan() == null ? "" : String.format("%.6f", o.costYuan())).append(',')
                    .append(value(o.imageWidth())).append(',')
                    .append(value(o.imageHeight())).append(',')
                    .append(csvCell(o.error()))
                    .append('\n');
        }
        return csv.toString();
    }

    private String verdict(Boolean isCorrect, Boolean needsHumanCheck) {
        if (isCorrect == null) {
            return "未判定";
        }
        if (Boolean.TRUE.equals(needsHumanCheck)) {
            return isCorrect ? "对（存疑）" : "错（存疑）";
        }
        return isCorrect ? "对" : "错";
    }

    private String stripExtension(String fileName) {
        if (fileName == null) {
            return "unknown";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String cell(String text) {
        if (!hasText(text)) {
            return "-";
        }
        // 表格里不能出现裸竖线，否则列会错位
        return text.replace("|", "\\|").replace("\n", " ");
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private String nullSafe(String text) {
        return text == null ? "" : text;
    }

    private String value(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String csvCell(String text) {
        if (text == null) {
            return "";
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
