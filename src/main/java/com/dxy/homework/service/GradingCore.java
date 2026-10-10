package com.dxy.homework.service;

import com.dxy.homework.client.ChatCallResult;
import com.dxy.homework.client.GradingModelClient;
import com.dxy.homework.model.GradedQuestion;
import com.dxy.homework.model.PreparedImage;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 批改的纯计算部分：图片预处理 → 组装提示词 → 调用模型 → 容错解析。
 *
 * <p>抽出来的目的只有一个：命令行验证工具和线上 API 必须走同一套提示词与解析逻辑。
 * 验证阶段为了准确率反复调过这套东西，两条路径各写一份迟早会漂移。
 */
@Component
public class GradingCore {

    private static final Map<String, String> SUBJECT_NAMES = Map.of(
            "math", "数学",
            "english", "英语",
            "chinese", "语文",
            "physics", "物理",
            "chemistry", "化学");

    private final GradingModelClient modelClient;
    private final PromptLoader promptLoader;
    private final GradeResultParser resultParser;
    private final ImagePreparer imagePreparer;

    public GradingCore(GradingModelClient modelClient,
                       PromptLoader promptLoader,
                       GradeResultParser resultParser,
                       ImagePreparer imagePreparer) {
        this.modelClient = modelClient;
        this.promptLoader = promptLoader;
        this.resultParser = resultParser;
        this.imagePreparer = imagePreparer;
    }

    /**
     * @param rawImages 原始图片字节，按页序排列。多页会一次性送给模型，由模型的 pg 字段区分页
     */
    public record Result(
            List<PreparedImage> images,
            ChatCallResult call,
            JsonNode rawJson,
            List<GradedQuestion> questions
    ) {
        public PreparedImage firstImage() {
            return images.isEmpty() ? null : images.get(0);
        }
    }

    public Result run(List<RawImage> rawImages, String subject, int grade, String mode) {
        List<PreparedImage> prepared = new ArrayList<>(rawImages.size());
        for (RawImage raw : rawImages) {
            prepared.add(imagePreparer.prepare(raw.content(), raw.fileName()));
        }
        return runPrepared(prepared, subject, grade, mode);
    }

    public Result runPrepared(List<PreparedImage> prepared, String subject, int grade, String mode) {
        boolean ocrOnly = "ocr".equalsIgnoreCase(mode);
        String systemPrompt = promptLoader.load(ocrOnly ? "vision_ocr" : "grade_homework");
        String userText = buildUserText(subject, grade, prepared.size(), ocrOnly);

        List<String> dataUrls = prepared.stream().map(PreparedImage::dataUrl).toList();
        ChatCallResult call = modelClient.chatWithImages(systemPrompt, userText, dataUrls);

        JsonNode root = resultParser.parse(call.content());
        List<GradedQuestion> questions = resultParser.toQuestions(root);
        return new Result(List.copyOf(prepared), call, root, questions);
    }

    /** 原始图片字节 + 文件名，用于日志与格式判定。 */
    public record RawImage(byte[] content, String fileName) {
    }

    private String buildUserText(String subject, int grade, int pageCount, boolean ocrOnly) {
        String action = ocrOnly
                ? "请按 system 中的规则如实转写这些作业照片的内容，不要判分，只输出 JSON。"
                : "请按 system 中的规则批改这些作业照片，只输出 JSON。";
        return """
                【本次任务】
                学科：%s
                年级：%s
                页数：%d

                %s
                """.formatted(displaySubject(subject), displayGrade(grade), pageCount, action);
    }

    public static String displaySubject(String subject) {
        return SUBJECT_NAMES.getOrDefault(subject == null ? "" : subject.toLowerCase(), "数学");
    }

    public static String displayGrade(Integer grade) {
        if (grade == null) {
            return "未知年级";
        }
        String[] names = {"一", "二", "三", "四", "五", "六", "七", "八", "九"};
        if (grade >= 1 && grade <= 9) {
            return names[grade - 1] + "年级";
        }
        return switch (grade) {
            case 10 -> "高一";
            case 11 -> "高二";
            case 12 -> "高三";
            default -> grade + "年级";
        };
    }
}
