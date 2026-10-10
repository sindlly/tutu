package com.dxy.homework.service;

import com.dxy.homework.config.DeepSeekProperties;
import com.dxy.homework.model.GradeOutcome;
import com.dxy.homework.model.GradedQuestion;
import com.dxy.homework.model.PreparedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 命令行验证入口的批改服务：薄封装，把 {@link GradingCore} 的结果整理成落盘报告需要的结构。
 * 线上 API 走的是 {@link com.dxy.homework.job.GradingPipeline}，两者共用 GradingCore。
 */
@Service
public class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    /** 置信度低于该值的题目视为"存疑"，UI 上应提示用户核对而不是直接判错。 */
    private static final double LOW_CONFIDENCE = 0.7;

    private final GradingCore gradingCore;
    private final CostCalculator costCalculator;
    private final DeepSeekProperties deepSeekProperties;

    public GradingService(GradingCore gradingCore,
                          CostCalculator costCalculator,
                          DeepSeekProperties deepSeekProperties) {
        this.gradingCore = gradingCore;
        this.costCalculator = costCalculator;
        this.deepSeekProperties = deepSeekProperties;
    }

    public GradeOutcome grade(Path imagePath, String subject, int grade, String mode) {
        long startedAt = System.currentTimeMillis();
        PreparedImage prepared = null;
        try {
            byte[] content = Files.readAllBytes(imagePath);
            GradingCore.Result result = gradingCore.run(
                    List.of(new GradingCore.RawImage(content, imagePath.getFileName().toString())),
                    subject, grade, mode);
            prepared = result.firstImage();

            List<GradedQuestion> questions = result.questions();
            int wrong = 0;
            int unknown = 0;
            int needsCheck = 0;
            for (GradedQuestion question : questions) {
                if (Boolean.FALSE.equals(question.isCorrect())) {
                    wrong++;
                }
                if (question.isCorrect() == null) {
                    unknown++;
                }
                if (Boolean.TRUE.equals(question.needsHumanCheck())
                        || (question.confidence() != null && question.confidence() < LOW_CONFIDENCE)) {
                    needsCheck++;
                }
            }

            int promptTokens = orZero(result.call().promptTokens());
            int completionTokens = orZero(result.call().completionTokens());
            int cacheHit = orZero(result.call().cacheHitTokens());
            boolean peak = costCalculator.isPeakPricing();
            double cost = costCalculator.estimate(deepSeekProperties.model(),
                    promptTokens, cacheHit, completionTokens, peak);

            log.info("批改完成 {}：{} 题，错 {} 题，存疑 {} 题，耗时 {}ms，成本约 {} 元",
                    imagePath.getFileName(), questions.size(), wrong, needsCheck, result.call().latencyMs(),
                    String.format("%.4f", cost));
            log.info("输出构成：正文 {} 字符 / 思考 {} 字符 / 输出 token {}（思考字符不为 0 说明思考模式实际是开启的）",
                    result.call().content() == null ? 0 : result.call().content().length(),
                    result.call().reasoningContent() == null ? 0 : result.call().reasoningContent().length(),
                    completionTokens);

            return new GradeOutcome(
                    imagePath.getFileName().toString(), mode, subject, grade,
                    true, null,
                    result.call().attempts(), result.call().latencyMs(),
                    result.call().promptTokens(), result.call().completionTokens(), result.call().cacheHitTokens(),
                    cost, peak, true,
                    questions.size(), wrong, unknown, needsCheck,
                    prepared.width(), prepared.height(), prepared.originalBytes(),
                    questions, result.rawJson().toPrettyString(), result.call().content());
        } catch (IOException ex) {
            long elapsed = System.currentTimeMillis() - startedAt;
            log.error("读取图片失败 {}：{}", imagePath.getFileName(), ex.getMessage());
            return GradeOutcome.failed(imagePath.getFileName().toString(), mode, subject, grade,
                    "读取图片失败：" + ex.getMessage(), elapsed, prepared);
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - startedAt;
            log.error("批改失败 {}：{}", imagePath.getFileName(), ex.getMessage());
            return GradeOutcome.failed(imagePath.getFileName().toString(), mode, subject, grade,
                    ex.getMessage(), elapsed, prepared);
        }
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
