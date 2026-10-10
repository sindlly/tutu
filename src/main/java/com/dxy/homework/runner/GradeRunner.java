package com.dxy.homework.runner;

import com.dxy.homework.config.DeepSeekProperties;
import com.dxy.homework.config.DemoProperties;
import com.dxy.homework.eval.EvalReportWriter;
import com.dxy.homework.eval.EvalResult;
import com.dxy.homework.eval.Evaluator;
import com.dxy.homework.eval.LabelDraftExporter;
import com.dxy.homework.eval.LabelSet;
import com.dxy.homework.model.GradeOutcome;
import com.dxy.homework.report.ReportWriter;
import com.dxy.homework.service.GradingService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 命令行批改入口：给定图片或目录，逐张跑完整链路并落盘报告。
 *
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.arguments="--demo.dir=samples"
 * </pre>
 */
@Component
@Order(1)
public class GradeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GradeRunner.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");

    private final DemoProperties demoProperties;
    private final DeepSeekProperties deepSeekProperties;
    private final GradingService gradingService;
    private final ReportWriter reportWriter;
    private final ApplicationContext applicationContext;
    private final LabelDraftExporter labelDraftExporter;
    private final Evaluator evaluator;
    private final EvalReportWriter evalReportWriter;

    public GradeRunner(DemoProperties demoProperties,
                       DeepSeekProperties deepSeekProperties,
                       GradingService gradingService,
                       ReportWriter reportWriter,
                       ApplicationContext applicationContext,
                       LabelDraftExporter labelDraftExporter,
                       Evaluator evaluator,
                       EvalReportWriter evalReportWriter) {
        this.demoProperties = demoProperties;
        this.deepSeekProperties = deepSeekProperties;
        this.gradingService = gradingService;
        this.reportWriter = reportWriter;
        this.applicationContext = applicationContext;
        this.labelDraftExporter = labelDraftExporter;
        this.evaluator = evaluator;
        this.evalReportWriter = evalReportWriter;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 模式 A：从已有批改结果导出标注草稿，不需要图片也不需要密钥
        if (StringUtils.hasText(demoProperties.evalExport())) {
            runExportLabels();
            return;
        }

        // 模式 B / C：批量批改 或 带标注的评测
        List<Path> images = collectImages();
        if (images.isEmpty()) {
            boolean requested = StringUtils.hasText(demoProperties.images())
                    || StringUtils.hasText(demoProperties.dir());
            if (requested) {
                // 明确指定了位置却一张图都没有，属于用法错误，不能默默挂起当服务跑
                log.error("指定的位置没有找到可批改的图片（仅支持 jpg/jpeg/png/gif）：dir={} images={}",
                        demoProperties.dir(), demoProperties.images());
                if (demoProperties.exitAfterRun()) {
                    shutdown(3);
                }
            } else {
                log.info("未指定待批改图片，跳过批改。用法：./run-demo.sh 或 --demo.dir=samples");
            }
            return;
        }

        if (!deepSeekProperties.hasApiKey() && !demoProperties.mock()) {
            log.error("未设置 DEEPSEEK_API_KEY，无法调用真实模型。");
            log.error("请检查项目根目录的 .env 文件，或 export DEEPSEEK_API_KEY=sk-xxxx");
            log.error("只想验证链路（不评准确率）：追加 --demo.mock=true");
            if (demoProperties.exitAfterRun()) {
                shutdown(2);
            }
            return;
        }

        if (StringUtils.hasText(demoProperties.evalLabels())) {
            runEvaluation(images);
        } else {
            runBatch(images);
        }
    }

    // ------------------------------------------------------------------ 导出标注草稿

    private void runExportLabels() {
        int code = 0;
        try {
            Path target = labelDraftExporter.export(
                    Paths.get(demoProperties.evalExport()).toAbsolutePath().normalize(),
                    Paths.get(demoProperties.evalLabelDir()).toAbsolutePath().normalize());
            log.info("标注草稿已导出：{}", target);
            log.info("下一步：打开它逐题核对原图，修正 expect_answer / expect_correct，删除预填备注，然后去掉 # 注释外的占位内容即可用于评测。");
        } catch (IOException ex) {
            log.error("导出标注草稿失败：{}", ex.getMessage());
            code = 4;
        }
        if (demoProperties.exitAfterRun()) {
            shutdown(code);
        }
    }

    // ------------------------------------------------------------------ 批量批改

    private void runBatch(List<Path> images) {
        log.info("开始批改：{} 张图片，学科={}，年级={}，模式={}，模型={}，mock={}",
                images.size(), demoProperties.subject(), demoProperties.grade(), demoProperties.mode(),
                deepSeekProperties.model(), demoProperties.mock());

        List<GradeOutcome> outcomes = new ArrayList<>();
        for (Path image : images) {
            log.info("→ 处理 {}", image);
            outcomes.add(gradingService.grade(
                    image, demoProperties.subject(), demoProperties.grade(), demoProperties.mode()));
        }

        printSummary(outcomes);

        try {
            Path dir = reportWriter.write(outcomes, demoProperties.outDir(),
                    deepSeekProperties.model(), deepSeekProperties.thinkingEnabled());
            log.info("报告已写入：{}", dir.toAbsolutePath());
            log.info("请打开 summary.md 逐题与作业原件比对，再判断是否值得继续投入。");
        } catch (IOException ex) {
            log.error("报告写入失败：{}", ex.getMessage());
        }

        if (demoProperties.exitAfterRun()) {
            boolean allFailed = outcomes.stream().noneMatch(GradeOutcome::success);
            shutdown(allFailed ? 1 : 0);
        }
    }

    // ------------------------------------------------------------------ 带标注的评测

    /**
     * 每张图重复批改 N 次再统计。单次运行的噪声比信号大，必须重复跑才能分辨
     * "提示词改好了"和"这次运气好"。
     */
    private void runEvaluation(List<Path> images) {
        LabelSet labels;
        try {
            labels = LabelSet.load(Paths.get(demoProperties.evalLabels()).toAbsolutePath().normalize());
        } catch (IOException ex) {
            log.error("标注集加载失败：{}", ex.getMessage());
            if (demoProperties.exitAfterRun()) {
                shutdown(4);
            }
            return;
        }

        int repeat = Math.max(1, demoProperties.evalRepeat());
        log.info("评测模式：{} 张图片 × {} 次 = {} 次批改，标注 {} 条，模型 {}（思考：{}）",
                images.size(), repeat, images.size() * repeat, labels.entries().size(),
                deepSeekProperties.model(), deepSeekProperties.thinkingEnabled() ? "开启" : "关闭");

        List<GradeOutcome> outcomes = new ArrayList<>();
        List<EvalResult> results = new ArrayList<>();
        int runIndex = 0;
        for (Path image : images) {
            for (int i = 1; i <= repeat; i++) {
                runIndex++;
                log.info("→ 第 {} 次处理 {}", runIndex, image.getFileName());
                GradeOutcome outcome = gradingService.grade(
                        image, demoProperties.subject(), demoProperties.grade(), demoProperties.mode());
                outcomes.add(outcome);
                results.add(evaluator.evaluate(outcome, labels, runIndex));
            }
        }

        for (String line : evalReportWriter.buildConsoleSummary(results, labels, deepSeekProperties.model())
                .split("\n")) {
            log.info("{}", line);
        }

        try {
            Path dir = reportWriter.write(outcomes, demoProperties.outDir(),
                    deepSeekProperties.model(), deepSeekProperties.thinkingEnabled());
            Path evalFile = evalReportWriter.write(dir, results, labels,
                    deepSeekProperties.model(), deepSeekProperties.thinkingEnabled());
            log.info("批改结果：{}", dir.toAbsolutePath());
            log.info("评测报告：{}  ← 先看这一份", evalFile.toAbsolutePath());
        } catch (IOException ex) {
            log.error("报告写入失败：{}", ex.getMessage());
        }

        if (demoProperties.exitAfterRun()) {
            boolean allFailed = outcomes.stream().noneMatch(GradeOutcome::success);
            shutdown(allFailed ? 1 : 0);
        }
    }

    private List<Path> collectImages() {
        Set<Path> images = new LinkedHashSet<>();

        if (StringUtils.hasText(demoProperties.images())) {
            for (String item : demoProperties.images().split(",")) {
                if (item.isBlank()) {
                    continue;
                }
                Path path = Paths.get(item.trim()).toAbsolutePath().normalize();
                if (Files.isRegularFile(path)) {
                    images.add(path);
                } else {
                    log.warn("图片不存在，已跳过：{}", path);
                }
            }
        }

        if (StringUtils.hasText(demoProperties.dir())) {
            Path dir = Paths.get(demoProperties.dir()).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                log.warn("目录不存在，已跳过：{}", dir);
            } else {
                try (Stream<Path> stream = Files.list(dir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> isSupported(path.getFileName().toString()))
                            .sorted()
                            .forEach(images::add);
                } catch (IOException ex) {
                    log.error("读取目录失败 {}：{}", dir, ex.getMessage());
                }
            }
        }

        return new ArrayList<>(images);
    }

    private boolean isSupported(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return SUPPORTED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private void printSummary(List<GradeOutcome> outcomes) {
        log.info("");
        log.info("==================== 批改结果汇总 ====================");
        log.info(String.format("%-4s %-28s %-6s %10s %6s %6s %6s %14s %10s",
                "序号", "文件", "结果", "耗时(s)", "题数", "判错", "存疑", "token(入/出)", "成本(元)"));
        int index = 0;
        for (GradeOutcome o : outcomes) {
            index++;
            log.info(String.format("%-4d %-28s %-6s %10.1f %6d %6d %6d %14s %10s",
                    index,
                    abbreviate(o.file(), 28),
                    o.success() ? "成功" : "失败",
                    o.latencyMs() / 1000.0,
                    o.questionCount(),
                    o.wrongCount(),
                    o.needsCheckCount(),
                    value(o.promptTokens()) + "/" + value(o.completionTokens()),
                    o.costYuan() == null ? "-" : String.format("%.4f", o.costYuan())));
            if (!o.success()) {
                log.info("        └─ 失败原因：{}", o.error());
            }
        }

        double totalCost = outcomes.stream()
                .filter(o -> o.costYuan() != null)
                .mapToDouble(GradeOutcome::costYuan)
                .sum();
        int totalQuestions = outcomes.stream().mapToInt(GradeOutcome::questionCount).sum();
        int totalWrong = outcomes.stream().mapToInt(GradeOutcome::wrongCount).sum();
        log.info("------------------------------------------------------");
        log.info("合计 {} 题，AI 判错 {} 题，估算成本 {} 元（{}）",
                totalQuestions, totalWrong, String.format("%.4f", totalCost),
                outcomes.stream().anyMatch(GradeOutcome::peakPricing) ? "高峰时段" : "空闲时段");
        log.info("======================================================");
        log.info("");
    }

    private void shutdown(int code) {
        try {
            System.exit(SpringApplication.exit(applicationContext, () -> code));
        } catch (Exception ex) {
            System.exit(code);
        }
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : "..." + text.substring(text.length() - (maxLength - 3));
    }

    private String value(Object v) {
        return v == null ? "-" : String.valueOf(v);
    }
}
