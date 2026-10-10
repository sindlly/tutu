package com.dxy.homework.eval;

import com.dxy.homework.eval.EvalResult.Miss;
import com.dxy.homework.eval.EvalResult.QuestionVerdict;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 评测报告：把多次运行的指标、明细和"跨次不一致"集中呈现。
 *
 * <p>刻意把「漏判明细」放在最显眼的位置 —— 漏判会让家长以为孩子做对了，是产品最不能犯的错。
 */
@Component
public class EvalReportWriter {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Path write(Path dir, List<EvalResult> results, LabelSet labels, String model, boolean thinkingEnabled)
            throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve("eval.md");
        Files.writeString(target, buildMarkdown(results, labels, model, thinkingEnabled), StandardCharsets.UTF_8);
        return target;
    }

    /** 控制台摘要，跑完立即可看结论。 */
    public String buildConsoleSummary(List<EvalResult> results, LabelSet labels, String model) {
        int tp = sum(results, EvalResult::tp);
        int fp = sum(results, EvalResult::fp);
        int fn = sum(results, EvalResult::fn);
        int tn = sum(results, EvalResult::tn);
        int matched = sum(results, EvalResult::answerMatched);
        int mismatched = sum(results, EvalResult::answerMismatched);
        int unmatched = sum(results, EvalResult::unmatched);

        StringBuilder sb = new StringBuilder();
        sb.append('\n').append("==================== 评测结果 ====================").append('\n');
        sb.append(String.format("标注条目 %d 条，运行 %d 次，模型 %s%n", labels.entries().size(), results.size(), model));
        sb.append(String.format("题目匹配：匹配上 %d 题，未匹配 %d 条次%n", matched + mismatched, unmatched));
        sb.append("--------------------------------------------------\n");
        sb.append(String.format("识别准确率        %s   (%d/%d)%n",
                percent(matched + mismatched == 0 ? Double.NaN : (double) matched / (matched + mismatched)),
                matched, matched + mismatched));
        sb.append(String.format("判分 TP/FP/FN/TN  %d / %d / %d / %d%n", tp, fp, fn, tn));
        sb.append(String.format("判错精确率        %s%n", percent(ratio(tp, tp + fp))));
        sb.append(String.format("判错召回率        %s%n", percent(ratio(tp, tp + fn))));
        sb.append(String.format(">>> 漏判率        %s   (%d/%d)  漏判=真错题被当成做对%n",
                percent(ratio(fn, tp + fn)), fn, tp + fn));
        sb.append(String.format(">>> 误判率        %s   (%d/%d)  误判=做对的被当成错%n",
                percent(ratio(fp, tn + fp)), fp, tn + fp));
        sb.append("--------------------------------------------------\n");

        Set<String> flippedJudge = findFlipped(results, true);
        Set<String> flippedRead = findFlipped(results, false);
        sb.append(String.format("跨次判定翻转的题  %d 道%n", flippedJudge.size()));
        sb.append(String.format("跨次读数翻转的题  %d 道  ← 识别不稳定的直接证据%n", flippedRead.size()));
        sb.append("==================================================\n");
        return sb.toString();
    }

    private String buildMarkdown(List<EvalResult> results, LabelSet labels, String model, boolean thinkingEnabled) {
        String file = results.isEmpty() ? "-" : results.get(0).file();
        int runs = results.isEmpty() ? 0 : results.stream().mapToInt(EvalResult::runIndex).max().orElse(0);
        int tp = sum(results, EvalResult::tp);
        int fp = sum(results, EvalResult::fp);
        int fn = sum(results, EvalResult::fn);
        int tn = sum(results, EvalResult::tn);
        int matched = sum(results, EvalResult::answerMatched);
        int mismatched = sum(results, EvalResult::answerMismatched);
        int unmatched = sum(results, EvalResult::unmatched);
        int unknown = sum(results, EvalResult::unknownVerdict);
        int needsCheck = sum(results, EvalResult::needsCheck);

        Set<String> flippedJudge = findFlipped(results, true);
        Set<String> flippedRead = findFlipped(results, false);

        StringBuilder md = new StringBuilder();
        md.append("# 评测报告\n\n");
        md.append("- 生成时间：").append(LocalDateTime.now(ZONE).format(STAMP)).append('\n');
        md.append("- 图片：").append(file).append('\n');
        md.append("- 标注集：").append(labels.source()).append("（").append(labels.entries().size()).append(" 条）\n");
        md.append("- 模型：").append(model).append("（思考模式：").append(thinkingEnabled ? "开启" : "关闭").append("）\n");
        md.append("- 每组重复次数：").append(runs).append(" 次\n\n");

        md.append("## 一、总览（").append(runs).append(" 次运行累计）\n\n");
        md.append("| 指标 | 数值 | 含义 |\n|---|---|---|\n");
        md.append("| 识别准确率 | ").append(percent(ratio(matched, matched + mismatched)))
                .append(" (").append(matched).append('/').append(matched + mismatched).append(") | 模型读到的学生答案与标注一致 |\n");
        md.append("| 判错精确率 | ").append(percent(ratio(tp, tp + fp)))
                .append(" | 模型报出的错题里真错的比例 |\n");
        md.append("| 判错召回率 | ").append(percent(ratio(tp, tp + fn)))
                .append(" | 真错题被抓到的比例 |\n");
        md.append("| **漏判率** | **").append(percent(ratio(fn, tp + fn))).append("** (")
                .append(fn).append('/').append(tp + fn).append(") | **真错题被当成做对** |\n");
        md.append("| 误判率 | ").append(percent(ratio(fp, tn + fp))).append(" (")
                .append(fp).append('/').append(tn + fp).append(") | 做对的被当成错 |\n");
        md.append("| F1 | ").append(percent(f1(tp, fp, fn))).append(" | 精确率与召回率的调和平均 |\n");
        md.append('\n');
        md.append("混淆矩阵（以学生真实对错为基准）：\n\n");
        md.append("| | 模型判对 | 模型判错 |\n|---|---|---|\n");
        md.append("| **学生做对** | TN ").append(tn).append(" | FP ").append(fp).append(" |\n");
        md.append("| **学生做错** | FN ").append(fn).append(" | TP ").append(tp).append(" |\n\n");
        md.append("其他：未匹配标注的题 ").append(unmatched).append(" 条次，")
                .append("模型未给出判定的题 ").append(unknown).append(" 条次，")
                .append("被标为需人工核对 ").append(needsCheck).append(" 条次。\n\n");

        md.append("## 二、逐次运行\n\n");
        md.append("| 运行 | 识别正确 | 识别错误 | 未匹配 | TP | FP | FN | TN | 漏判率 | 误判率 |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (EvalResult r : results) {
            md.append("| ").append(r.runIndex()).append(" | ").append(r.answerMatched())
                    .append(" | ").append(r.answerMismatched())
                    .append(" | ").append(r.unmatched())
                    .append(" | ").append(r.tp()).append(" | ").append(r.fp())
                    .append(" | ").append(r.fn()).append(" | ").append(r.tn())
                    .append(" | ").append(percent(r.missRate()))
                    .append(" | ").append(percent(r.falseAlarmRate()))
                    .append(" |\n");
        }
        md.append('\n');

        md.append("## 三、漏判明细（最危险，优先排查）\n\n");
        appendMisses(md, results, EvalResult::falseNegatives, "无漏判。");
        md.append('\n');

        md.append("## 四、误判明细\n\n");
        appendMisses(md, results, EvalResult::falsePositives, "无误判。");
        md.append('\n');

        md.append("## 五、识别错误明细\n\n");
        appendMisses(md, results, EvalResult::answerMisses, "所有已标注题目的读数都与真实笔迹一致。");
        md.append('\n');

        md.append("## 六、跨次不稳定性\n\n");
        md.append("同一张图、同一个手写内容，多次运行结果不一致的题目。这类题说明模型读数或判分不稳定，\n");
        md.append("产品上必须靠低置信度提示让用户核对，不能直接给结论。\n\n");
        md.append("### 6.1 判定翻转（").append(flippedJudge.size()).append(" 道）\n\n");
        appendFlips(md, results, flippedJudge, true);
        md.append('\n');
        md.append("### 6.2 读数翻转（").append(flippedRead.size()).append(" 道）\n\n");
        appendFlips(md, results, flippedRead, false);
        md.append('\n');

        md.append("---\n");
        md.append("本报告基于人工标注计算，仅代表当前这批图片与当前提示词版本的表现。\n");
        md.append("换提示词或换模型后必须重跑，且不要只看单次结果。\n");
        return md.toString();
    }

    private void appendMisses(StringBuilder md, List<EvalResult> results,
                              java.util.function.Function<EvalResult, List<Miss>> extractor, String emptyText) {
        boolean any = false;
        for (EvalResult r : results) {
            List<Miss> misses = extractor.apply(r);
            if (misses.isEmpty()) {
                continue;
            }
            any = true;
            md.append("**第 ").append(r.runIndex()).append(" 次运行**\n\n");
            md.append("| 题号 | 题干 | 标注笔迹 | 模型读数 | 真实 | 模型判定 | 备注 |\n");
            md.append("|---|---|---|---|---|---|---|\n");
            for (Miss m : misses) {
                md.append("| ").append(text(m.no()))
                        .append(" | ").append(cell(m.stem()))
                        .append(" | ").append(cell(m.expectAnswer()))
                        .append(" | ").append(cell(m.modelAnswer()))
                        .append(" | ").append(text(m.expectVerdict()))
                        .append(" | ").append(text(m.modelVerdict()))
                        .append(" | ").append(cell(m.note()))
                        .append(" |\n");
            }
            md.append('\n');
        }
        if (!any) {
            md.append("_").append(emptyText).append("_\n");
        }
    }

    private void appendFlips(StringBuilder md, List<EvalResult> results, Set<String> flippedKeys, boolean judgeMode) {
        if (flippedKeys.isEmpty()) {
            md.append("_无_\n");
            return;
        }
        int runCount = results.stream().mapToInt(EvalResult::runIndex).max().orElse(0);
        md.append("| 题干 | 真实 |");
        for (int i = 1; i <= runCount; i++) {
            md.append(" 第").append(i).append("次 |");
        }
        md.append('\n').append("|---|---|");
        for (int i = 1; i <= runCount; i++) {
            md.append("---|");
        }
        md.append('\n');

        for (String key : flippedKeys) {
            String stem = "";
            String expect = "-";
            Map<Integer, String> byRun = new LinkedHashMap<>();
            for (EvalResult r : results) {
                for (QuestionVerdict v : r.verdicts()) {
                    if (!v.key().equals(key)) {
                        continue;
                    }
                    stem = v.stem();
                    if (v.expectCorrect() != null) {
                        expect = v.expectCorrect() ? "做对" : "做错";
                    }
                    byRun.put(r.runIndex(), judgeMode
                            ? (v.modelCorrect() == null ? "未判定" : (v.modelCorrect() ? "判对" : "判错"))
                            : text(v.modelAnswer()));
                }
            }
            md.append("| ").append(cell(stem)).append(" | ").append(expect).append(" |");
            for (int i = 1; i <= runCount; i++) {
                md.append(' ').append(text(byRun.get(i))).append(" |");
            }
            md.append('\n');
        }
        md.append('\n');
    }

    /** 找出跨次比对出现多个不同值的题目。judgeMode=true 比判定，false 比读数。 */
    private Set<String> findFlipped(List<EvalResult> results, boolean judgeMode) {
        Map<String, Set<String>> valuesByKey = new LinkedHashMap<>();
        for (EvalResult r : results) {
            for (QuestionVerdict v : r.verdicts()) {
                String value = judgeMode
                        ? String.valueOf(v.modelCorrect())
                        : (v.modelAnswer() == null ? "" : AnswerNormalizer.normalize(v.modelAnswer()));
                if (judgeMode && v.modelCorrect() == null) {
                    continue;
                }
                valuesByKey.computeIfAbsent(v.key(), k -> new LinkedHashSet<>()).add(value);
            }
        }
        Set<String> flipped = new LinkedHashSet<>();
        valuesByKey.forEach((key, values) -> {
            if (values.size() > 1) {
                flipped.add(key);
            }
        });
        return flipped;
    }

    private int sum(List<EvalResult> results, java.util.function.ToIntFunction<EvalResult> mapper) {
        return results.stream().mapToInt(mapper).sum();
    }

    private double f1(int tp, int fp, int fn) {
        double p = ratio(tp, tp + fp);
        double r = ratio(tp, tp + fn);
        if (Double.isNaN(p) || Double.isNaN(r) || p + r == 0) {
            return Double.NaN;
        }
        return 2 * p * r / (p + r);
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? Double.NaN : (double) numerator / denominator;
    }

    private String percent(double value) {
        return Double.isNaN(value) ? "-" : String.format("%.1f%%", value * 100);
    }

    private String text(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private String cell(String value) {
        return text(value).replace("|", "\\|").replace("\n", " ");
    }
}
