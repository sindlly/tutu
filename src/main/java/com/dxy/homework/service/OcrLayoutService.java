package com.dxy.homework.service;

import com.dxy.homework.client.OcrClient;
import com.dxy.homework.client.OcrException;
import com.dxy.homework.config.OcrProperties;
import com.dxy.homework.model.GradedQuestion;
import com.dxy.homework.model.OcrLine;
import com.dxy.homework.model.PreparedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 串联「调 OCR → 拿行坐标 → 匹配题干 → 算区域」。
 *
 * <p>这里刻意把失败全部吞掉并返回空结果：OCR 是锦上添花的能力，它挂了只影响有没有裁剪图，
 * 绝不能让整次批改失败 —— 批改结果本身已经拿到了，钱也已经花了。
 */
@Service
public class OcrLayoutService {

    private static final Logger log = LoggerFactory.getLogger(OcrLayoutService.class);

    private final OcrClient ocrClient;
    private final OcrBoxLocator locator;
    private final OcrProperties properties;

    public OcrLayoutService(OcrClient ocrClient, OcrBoxLocator locator, OcrProperties properties) {
        this.ocrClient = ocrClient;
        this.locator = locator;
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.boxEnabled() && ocrClient.enabled();
    }

    /**
     * 为每道题算出原图区域。
     *
     * @return key 为题目序号（从 1 开始），匹配不上的题目不在结果里
     */
    public Map<Integer, OcrBoxLocator.Box> locate(List<PreparedImage> pages, List<GradedQuestion> questions) {
        if (!enabled() || pages == null || pages.isEmpty() || questions == null || questions.isEmpty()) {
            return Map.of();
        }

        List<OcrLine> lines = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            try {
                lines.addAll(ocrClient.recognize(pages.get(i), i + 1));
            } catch (OcrException ex) {
                // 多页时前面几页的结果还有用，不整体放弃
                log.warn("OCR 识别第 {} 页失败（{}）：{}", i + 1, ex.code(), ex.getMessage());
            }
        }
        if (lines.isEmpty()) {
            return Map.of();
        }

        Map<Integer, OcrBoxLocator.Box> boxes = locator.locate(lines, questions);
        log.info("OCR 定位完成：识别 {} 行，为 {} 道题算出区域（共 {} 题）",
                lines.size(), boxes.size(), questions.size());
        return boxes;
    }

    /** 只做识别、不做匹配，给调试接口用。 */
    public List<OcrLine> recognize(List<PreparedImage> pages) {
        if (!ocrClient.enabled()) {
            throw new OcrException("CONFIG", "OCR 未配置：请检查 OCR_SECRET_ID / OCR_SECRET_KEY 或 OCR_ENABLED");
        }
        List<OcrLine> lines = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            lines.addAll(ocrClient.recognize(pages.get(i), i + 1));
        }
        return lines;
    }

    public String name() {
        return ocrClient.name();
    }
}
