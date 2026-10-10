package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.config.GradingProperties;
import com.dxy.homework.entity.AppFile;
import com.dxy.homework.entity.HomeworkSubmit;
import com.dxy.homework.entity.Mistake;
import com.dxy.homework.entity.Question;
import com.dxy.homework.mapper.MistakeMapper;
import com.dxy.homework.mapper.QuestionMapper;
import com.dxy.homework.model.GradedQuestion;
import com.dxy.homework.model.PreparedImage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 为错题生成两种图片素材：
 * <ul>
 *   <li><b>错题卡片</b> —— 用结构化结果重新排版，100% 准确、可打印，是主要展示形态</li>
 *   <li><b>原图裁剪</b> —— 保留学生原始书写痕迹，作为补充证据。坐标是模型估算的，可能裁不准</li>
 * </ul>
 *
 * <p>素材生成失败不影响批改结果的正确性，所以这里全部吞掉异常只记日志 ——
 * 绝不能因为画不出一张卡片，就让用户觉得整次批改失败了。
 *
 * <p>注意裁剪要基于**预处理后的图**：模型看到的坐标是相对它收到的那张图（已摆正、已缩放），
 * 拿原图去裁会因为尺寸不同而整体偏移。
 */
@Service
public class MistakeArtifactService {

    private static final Logger log = LoggerFactory.getLogger(MistakeArtifactService.class);

    private final ImageCropper imageCropper;
    private final MistakeCardRenderer cardRenderer;
    private final FileService fileService;
    private final QuestionMapper questionMapper;
    private final MistakeMapper mistakeMapper;
    private final GradingProperties gradingProperties;

    public MistakeArtifactService(ImageCropper imageCropper,
                                  MistakeCardRenderer cardRenderer,
                                  FileService fileService,
                                  QuestionMapper questionMapper,
                                  MistakeMapper mistakeMapper,
                                  GradingProperties gradingProperties) {
        this.imageCropper = imageCropper;
        this.cardRenderer = cardRenderer;
        this.fileService = fileService;
        this.questionMapper = questionMapper;
        this.mistakeMapper = mistakeMapper;
        this.gradingProperties = gradingProperties;
    }

    /** 落库后的错题：题目实体（已带主键）+ 模型原始结果 + 错题本条目主键。 */
    public record WrongItem(Question question, GradedQuestion graded, Long mistakeId) {
    }

    public void generate(HomeworkSubmit submit, List<WrongItem> wrongItems,
                         List<PreparedImage> pages, String footerText) {
        if (wrongItems == null || wrongItems.isEmpty() || pages == null || pages.isEmpty()) {
            return;
        }

        int cardCount = 0;
        int cropCount = 0;
        for (WrongItem item : wrongItems) {
            Long cardFileId = null;
            Long cropFileId = null;

            try {
                byte[] card = cardRenderer.render(buildCardModel(submit, item, footerText));
                cardFileId = fileService.storeBytes(
                        submit.getUserId(), AppFile.BIZ_MISTAKE_CARD, card, "image/png").getId();
                cardCount++;
            } catch (Exception ex) {
                log.warn("错题卡片生成失败 questionId={}：{}", item.question().getId(), ex.getMessage());
            }

            // 裁剪只在拿到可靠坐标时才会真的发生（OCR 行坐标匹配，或用户手动框选）。
            // 模型自报的坐标一律不用 —— 偏差可达数行，裁出来常是相邻的别的题。
            if (gradingProperties.mistakeCropEnabled()) {
                try {
                    byte[] crop = cropQuestion(item.question(), pages);
                    if (crop != null) {
                        cropFileId = fileService.storeBytes(
                                submit.getUserId(), AppFile.BIZ_MISTAKE_CROP, crop, "image/jpeg").getId();
                        cropCount++;
                    }
                } catch (Exception ex) {
                    log.warn("错题裁剪失败 questionId={}：{}", item.question().getId(), ex.getMessage());
                }
            }

            // 部分成功也写回，不要因为裁剪失败就把卡片也丢了
            try {
                writeBack(item, cardFileId, cropFileId);
            } catch (Exception ex) {
                log.warn("错题素材回写失败 questionId={}：{}", item.question().getId(), ex.getMessage());
            }
        }
        log.info("错题素材生成完成：卡片 {} 张，裁剪 {} 张（共 {} 道错题）",
                cardCount, cropCount, wrongItems.size());
    }

    /** 单张错题的卡片，供"重新生成卡片"接口复用。 */
    public Long renderCardFor(HomeworkSubmit submit, Question question, String footerText) {
        byte[] card = cardRenderer.render(new MistakeCardRenderer.Model(
                GradingCore.displaySubject(submit.getSubject()),
                GradingCore.displayGrade(submit.getGrade()),
                question.getQuestionNo(),
                question.getStem(),
                question.getStudentAnswer(),
                question.getCorrectAnswer(),
                question.getWrongReason(),
                question.getAnalysis(),
                question.getKnowledgePoints(),
                footerText));
        return fileService.storeBytes(submit.getUserId(), AppFile.BIZ_MISTAKE_CARD, card, "image/png").getId();
    }

    private MistakeCardRenderer.Model buildCardModel(HomeworkSubmit submit, WrongItem item, String footerText) {
        Question question = item.question();
        return new MistakeCardRenderer.Model(
                GradingCore.displaySubject(submit.getSubject()),
                GradingCore.displayGrade(submit.getGrade()),
                question.getQuestionNo(),
                question.getStem(),
                question.getStudentAnswer(),
                question.getCorrectAnswer(),
                question.getWrongReason(),
                question.getAnalysis(),
                question.getKnowledgePoints(),
                footerText);
    }

    private byte[] cropQuestion(Question question, List<PreparedImage> pages) {
        if (question.getBboxX() == null) {
            return null;
        }
        // bbox_source：1=AI 估算 2=OCR 行坐标匹配 3=用户手动。
        // 模型自报坐标实测偏差可达数行，默认不拿它裁 —— 宁可没有裁剪图，也不要裁出别的题。
        Integer bboxSource = question.getBboxSource();
        if (!gradingProperties.cropAllowAiBbox() && (bboxSource == null || bboxSource == 1)) {
            log.debug("题目 {} 的坐标来自模型估算（bbox_source={}），跳过裁剪", question.getId(), bboxSource);
            return null;
        }
        Integer pageSeq = question.getPageSeq();
        int index = (pageSeq == null || pageSeq < 1 || pageSeq > pages.size()) ? 0 : pageSeq - 1;
        // 坐标是相对模型收到的那张（预处理后的）图，必须用它来裁
        byte[] source = pages.get(index).content();
        return imageCropper.crop(source, question.getBboxX(), question.getBboxY(),
                question.getBboxW(), question.getBboxH());
    }

    private void writeBack(WrongItem item, Long cardFileId, Long cropFileId) {
        if (cardFileId == null && cropFileId == null) {
            return;
        }
        Long questionId = item.question().getId();

        if (cropFileId != null) {
            questionMapper.update(null, Wrappers.<Question>lambdaUpdate()
                    .eq(Question::getId, questionId)
                    .set(Question::getCropFileId, cropFileId));
        }
        if (item.mistakeId() != null) {
            var wrapper = Wrappers.<Mistake>lambdaUpdate().eq(Mistake::getId, item.mistakeId());
            boolean any = false;
            if (cardFileId != null) {
                wrapper.set(Mistake::getCardFileId, cardFileId);
                any = true;
            }
            if (cropFileId != null) {
                wrapper.set(Mistake::getCropFileId, cropFileId);
                any = true;
            }
            if (any) {
                mistakeMapper.update(null, wrapper);
            }
        }
    }
}
