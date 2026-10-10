package com.dxy.homework.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.client.DeepSeekException;
import com.dxy.homework.config.AsyncConfig;
import com.dxy.homework.config.DeepSeekProperties;
import com.dxy.homework.service.OcrBoxLocator;
import com.dxy.homework.service.OcrLayoutService;
import com.dxy.homework.entity.AiCallLog;
import com.dxy.homework.entity.AppFile;
import com.dxy.homework.entity.HomeworkSubmit;
import com.dxy.homework.entity.Mistake;
import com.dxy.homework.entity.Question;
import com.dxy.homework.entity.SubmitImage;
import com.dxy.homework.mapper.AiCallLogMapper;
import com.dxy.homework.mapper.AppFileMapper;
import com.dxy.homework.mapper.HomeworkSubmitMapper;
import com.dxy.homework.mapper.MistakeMapper;
import com.dxy.homework.mapper.QuestionMapper;
import com.dxy.homework.mapper.SubmitImageMapper;
import com.dxy.homework.model.GradedQuestion;
import com.dxy.homework.model.PreparedImage;
import com.dxy.homework.service.CostCalculator;
import com.dxy.homework.service.GradingCore;
import com.dxy.homework.service.MistakeArtifactService;
import com.dxy.homework.service.QuestionTypeMapper;
import com.dxy.homework.service.QuotaService;
import com.dxy.homework.storage.StorageClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 批改流水线：异步执行「取原图 → 调模型 → 落库 → 错题入本」。
 *
 * <p>为什么必须异步：单页 45 题实测耗时 6 秒左右，多页更久。同步接口会把小程序请求
 * 拖到超时，用户只能干等。客户端拿 submitId 后轮询状态即可。
 *
 * <p>状态流转：{@code PENDING → GRADING → SUCCESS / FAILED}。
 * 每次流转都用带条件的 UPDATE 做 CAS，保证同一任务只会被处理一次 ——
 * 队列重投、应用重启后的补偿扫描都可能重复触发，没有这道闸门会重复扣额度、重复写错题。
 */
@Component
public class GradingPipeline {

    private static final Logger log = LoggerFactory.getLogger(GradingPipeline.class);

    /** 艾宾浩斯第一档：错题当天入本，次日首次复习 */
    private static final int FIRST_REVIEW_DAYS = 1;

    private final HomeworkSubmitMapper submitMapper;
    private final SubmitImageMapper submitImageMapper;
    private final QuestionMapper questionMapper;
    private final MistakeMapper mistakeMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final AppFileMapper fileMapper;

    private final StorageClient storageClient;
    private final GradingCore gradingCore;
    private final CostCalculator costCalculator;
    private final DeepSeekProperties deepSeekProperties;
    private final QuotaService quotaService;
    private final MistakeArtifactService artifactService;
    private final OcrLayoutService ocrLayoutService;
    private final Executor gradingExecutor;
    private final TransactionTemplate transactionTemplate;

    public GradingPipeline(HomeworkSubmitMapper submitMapper,
                           SubmitImageMapper submitImageMapper,
                           QuestionMapper questionMapper,
                           MistakeMapper mistakeMapper,
                           AiCallLogMapper aiCallLogMapper,
                           AppFileMapper fileMapper,
                           StorageClient storageClient,
                           GradingCore gradingCore,
                           CostCalculator costCalculator,
                           DeepSeekProperties deepSeekProperties,
                           QuotaService quotaService,
                           MistakeArtifactService artifactService,
                           OcrLayoutService ocrLayoutService,
                           @Qualifier(AsyncConfig.GRADING_EXECUTOR) Executor gradingExecutor,
                           PlatformTransactionManager transactionManager) {
        this.submitMapper = submitMapper;
        this.submitImageMapper = submitImageMapper;
        this.questionMapper = questionMapper;
        this.mistakeMapper = mistakeMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.fileMapper = fileMapper;
        this.storageClient = storageClient;
        this.gradingCore = gradingCore;
        this.costCalculator = costCalculator;
        this.deepSeekProperties = deepSeekProperties;
        this.quotaService = quotaService;
        this.artifactService = artifactService;
        this.ocrLayoutService = ocrLayoutService;
        this.gradingExecutor = gradingExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 提交到线程池。队列满时必须立刻失败并退还额度，不能让任务悄悄消失。 */
    public void enqueue(Long submitId) {
        try {
            gradingExecutor.execute(() -> process(submitId));
        } catch (RejectedExecutionException ex) {
            log.error("批改队列已满，任务 {} 直接失败", submitId);
            fail(submitId, "QUEUE_FULL", "当前批改任务较多，请稍后重试", 0L, true);
        }
    }

    void process(Long submitId) {
        HomeworkSubmit submit = submitMapper.selectById(submitId);
        if (submit == null) {
            log.warn("任务 {} 不存在，跳过", submitId);
            return;
        }

        // CAS 抢占：只有还处于 PENDING 的线程能继续，其余直接退出
        int acquired = submitMapper.update(null, Wrappers.<HomeworkSubmit>lambdaUpdate()
                .eq(HomeworkSubmit::getId, submitId)
                .eq(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_PENDING)
                .set(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_GRADING));
        if (acquired != 1) {
            log.info("任务 {} 已被处理或状态非待处理，跳过重复执行", submitId);
            return;
        }

        long startedAt = System.currentTimeMillis();
        // 只有模型真的返回了内容才算"钱已经花了"，此时失败不退还额度
        boolean aiResponded = false;

        try {
            List<GradingCore.RawImage> rawImages = loadRawImages(submitId);
            GradingCore.Result result = gradingCore.run(
                    rawImages, submit.getSubject(), submit.getGrade(), "grade");
            aiResponded = true;

            // 只用在这时候调 OCR：没有错题就没有裁剪图，没必要为一次批改多花一次 OCR 的钱
            Map<Integer, OcrBoxLocator.Box> boxes = hasWrong(result.questions())
                    ? ocrLayoutService.locate(result.images(), result.questions())
                    : Map.of();

            // 顺序很关键：先落库，再生成素材，最后才标记成功。
            // 如果把标记成功放在素材生成之前，客户端一看到 finished=true 就去取卡片，可能拿到空图。
            List<MistakeArtifactService.WrongItem> wrongItems = persistResult(submit, result, boxes);
            generateArtifacts(submit, wrongItems, result.images());
            long durationMs = System.currentTimeMillis() - startedAt;
            markSuccess(submit.getId(), durationMs);

            log.info("任务 {} 批改完成：{} 题，错 {} 题，耗时 {}ms",
                    submitId, result.questions().size(), wrongItems.size(), durationMs);
        } catch (DeepSeekException ex) {
            log.error("任务 {} AI 调用失败：{}", submitId, ex.getMessage());
            fail(submitId, "AI_ERROR", aiResponded ? "批改结果处理失败" : "批改服务暂时不可用，请稍后重试",
                    startedAt, !aiResponded);
        } catch (Exception ex) {
            log.error("任务 {} 批改失败", submitId, ex);
            fail(submitId, "GRADING_ERROR", "批改失败，请重试", startedAt, !aiResponded);
        }
    }

    private List<GradingCore.RawImage> loadRawImages(Long submitId) {
        List<SubmitImage> links = submitImageMapper.selectList(Wrappers.<SubmitImage>lambdaQuery()
                .eq(SubmitImage::getSubmitId, submitId)
                .orderByAsc(SubmitImage::getSeq));
        if (links.isEmpty()) {
            throw new IllegalStateException("任务没有关联任何图片");
        }

        List<GradingCore.RawImage> rawImages = new ArrayList<>(links.size());
        for (SubmitImage link : links) {
            AppFile file = fileMapper.selectById(link.getFileId());
            if (file == null) {
                throw new IllegalStateException("原图记录缺失 fileId=" + link.getFileId());
            }
            rawImages.add(new GradingCore.RawImage(storageClient.read(file.getFileKey()), file.getFileKey()));
        }
        return rawImages;
    }

    /**
     * 生成错题卡片与原图裁剪。
     *
     * <p>刻意放在落库之后、且整体吞异常：批改结果已经写好了，画不出卡片只是少了个附件，
     * 不能因此让用户看到"批改失败"。
     */
    private void generateArtifacts(HomeworkSubmit submit,
                                   List<MistakeArtifactService.WrongItem> wrongItems,
                                   List<PreparedImage> pages) {
        if (wrongItems.isEmpty()) {
            return;
        }
        try {
            String footer = "拍照批改作业 · " + LocalDate.now(ZoneId.of("Asia/Shanghai"));
            artifactService.generate(submit, wrongItems, pages, footer);
        } catch (Exception ex) {
            log.warn("错题素材生成整体失败，不影响批改结果：{}", ex.getMessage());
        }
    }

    /**
     * 收尾：把任务置为成功并记录耗时。
     *
     * <p>刻意放在素材生成之后，这样客户端看到 finished=true 时卡片和裁剪图都已经就绪。
     * 代价是若进程恰好在这两步之间挂掉，任务会停在"批改中"，由 StuckTaskRecoveryJob 兜底。
     */
    private void markSuccess(Long submitId, long durationMs) {
        submitMapper.update(null, Wrappers.<HomeworkSubmit>lambdaUpdate()
                .eq(HomeworkSubmit::getId, submitId)
                .set(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_SUCCESS)
                .set(HomeworkSubmit::getDurationMs, (int) durationMs)
                .set(HomeworkSubmit::getFinishedAt, LocalDateTime.now()));
    }

    /**
     * 落库必须在一个事务里：题目、错题、调用日志、统计要么都成功要么都不写。
     * 注意这里**不**修改 status —— 状态要等素材生成完才翻转。
     */
    private List<MistakeArtifactService.WrongItem> persistResult(
            HomeworkSubmit submit, GradingCore.Result result, Map<Integer, OcrBoxLocator.Box> boxes) {

        List<MistakeArtifactService.WrongItem> wrongItems = new ArrayList<>();

        transactionTemplate.executeWithoutResult(status -> {
            // 重试场景先清掉上一次的产物，否则会重复写错题、错题还会指向已删除的题目
            mistakeMapper.delete(Wrappers.<Mistake>lambdaQuery().eq(Mistake::getSubmitId, submit.getId()));
            questionMapper.delete(Wrappers.<Question>lambdaQuery().eq(Question::getSubmitId, submit.getId()));

            int seq = 0;
            int right = 0;
            int wrong = 0;
            for (GradedQuestion graded : result.questions()) {
                seq++;
                Question question = toEntity(submit, graded, seq, boxes.get(seq));
                questionMapper.insert(question);

                if (Boolean.TRUE.equals(graded.isCorrect())) {
                    right++;
                } else if (Boolean.FALSE.equals(graded.isCorrect())) {
                    wrong++;
                    Mistake mistake = toMistake(submit, question, graded);
                    mistakeMapper.insert(mistake);
                    // 素材生成需要用错题本条目主键回写图片 id，所以在这里收集
                    wrongItems.add(new MistakeArtifactService.WrongItem(question, graded, mistake.getId()));
                }
            }

            BigDecimal score = seq == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(right * 100.0 / seq).setScale(2, RoundingMode.HALF_UP);

            int promptTokens = orZero(result.call().promptTokens());
            int completionTokens = orZero(result.call().completionTokens());
            BigDecimal cost = BigDecimal.valueOf(costCalculator.estimate(
                            deepSeekProperties.model(), promptTokens, orZero(result.call().cacheHitTokens()),
                            completionTokens, costCalculator.isPeakPricing()))
                    .setScale(6, RoundingMode.HALF_UP);

            submitMapper.update(null, Wrappers.<HomeworkSubmit>lambdaUpdate()
                    .eq(HomeworkSubmit::getId, submit.getId())
                    .set(HomeworkSubmit::getTotalCount, seq)
                    .set(HomeworkSubmit::getRightCount, right)
                    .set(HomeworkSubmit::getWrongCount, wrong)
                    .set(HomeworkSubmit::getScore, score)
                    .set(HomeworkSubmit::getModel, deepSeekProperties.model())
                    .set(HomeworkSubmit::getPromptTokens, promptTokens)
                    .set(HomeworkSubmit::getCompletionTokens, completionTokens)
                    .set(HomeworkSubmit::getCost, cost)
                    .set(HomeworkSubmit::getFinishedAt, LocalDateTime.now()));

            AiCallLog callLog = new AiCallLog();
            callLog.setSubmitId(submit.getId());
            callLog.setScene("grade_homework");
            callLog.setModel(deepSeekProperties.model());
            callLog.setRequestId(result.call().requestId());
            callLog.setHttpStatus(200);
            callLog.setLatencyMs((int) result.call().latencyMs());
            callLog.setPromptTokens(result.call().promptTokens());
            callLog.setCompletionTokens(result.call().completionTokens());
            callLog.setCacheHitTokens(result.call().cacheHitTokens());
            callLog.setCost(cost);
            callLog.setRetryNo(result.call().attempts() - 1);
            aiCallLogMapper.insert(callLog);
        });

        return wrongItems;
    }

    private Question toEntity(HomeworkSubmit submit, GradedQuestion graded, int seq,
                              OcrBoxLocator.Box ocrBox) {
        Question question = new Question();
        question.setSubmitId(submit.getId());
        question.setSeq(seq);
        question.setQuestionNo(graded.no());
        question.setQuestionType(QuestionTypeMapper.toCode(graded.type()));
        question.setStem(graded.stem());
        question.setStudentAnswer(graded.studentAnswer());
        question.setCorrectAnswer(graded.correctAnswer());
        // is_correct 是非空列：模型没给判定时按"对"存，但会置 needs_check 让用户核对
        question.setIsCorrect(Boolean.FALSE.equals(graded.isCorrect()) ? 0 : 1);
        question.setConfidence(graded.confidence() == null ? null
                : BigDecimal.valueOf(graded.confidence()).setScale(3, RoundingMode.HALF_UP));
        question.setWrongReason(graded.wrongReason());
        question.setAnalysis(graded.analysis());
        question.setKnowledgePoints(graded.knowledgePoints());
        question.setDifficulty(graded.difficulty());
        question.setPageSeq(graded.page() == null ? 1 : graded.page());
        question.setNeedsCheck(isNeedsCheck(graded) ? 1 : 0);

        // OCR 算出来的区域优先：它是像素级行坐标匹配出来的，比模型自报的估算值可靠得多。
        // 只有 OCR 没匹配上时才退回模型坐标，并标记为「AI 估算」—— 裁剪环节会拒绝用它。
        if (ocrBox != null) {
            question.setBboxX(toScale5(ocrBox.x()));
            question.setBboxY(toScale5(ocrBox.y()));
            question.setBboxW(toScale5(ocrBox.w()));
            question.setBboxH(toScale5(ocrBox.h()));
            // 2 表示由 OCR 行坐标匹配得出
            question.setBboxSource(2);
            return question;
        }

        GradedQuestion.RegionHint hint = graded.regionHint();
        if (hint != null && hint.usable()) {
            question.setBboxX(toScale5(hint.x()));
            question.setBboxY(toScale5(hint.y()));
            question.setBboxW(toScale5(hint.w()));
            question.setBboxH(toScale5(hint.h()));
            // 1 表示由 AI 给出，未经 OCR 校准，也未经用户确认
            question.setBboxSource(1);
        }
        return question;
    }

    private boolean hasWrong(List<GradedQuestion> questions) {
        if (questions == null) {
            return false;
        }
        return questions.stream().anyMatch(q -> Boolean.FALSE.equals(q.isCorrect()));
    }

    private Mistake toMistake(HomeworkSubmit submit, Question question, GradedQuestion graded) {
        Mistake mistake = new Mistake();
        mistake.setUserId(submit.getUserId());
        mistake.setStudentId(submit.getStudentId());
        mistake.setQuestionId(question.getId());
        mistake.setSubmitId(submit.getId());
        mistake.setSubject(submit.getSubject());
        mistake.setGrade(submit.getGrade());
        mistake.setQuestionType(question.getQuestionType());
        // 存快照：题目后续被修正或删除，历史错题也不能失真
        mistake.setStemSnapshot(question.getStem());
        mistake.setAnswerSnapshot(question.getStudentAnswer());
        mistake.setKnowledgeKey(firstKnowledge(graded));
        mistake.setMasteryLevel(0);
        mistake.setReviewCount(0);
        mistake.setRightStreak(0);
        mistake.setNextReviewAt(LocalDateTime.now().plusDays(FIRST_REVIEW_DAYS));
        mistake.setStatus(Mistake.STATUS_ACTIVE);
        return mistake;
    }

    private boolean isNeedsCheck(GradedQuestion graded) {
        if (Boolean.TRUE.equals(graded.needsHumanCheck())) {
            return true;
        }
        // 模型没给出判定本身就是不确定信号
        if (graded.isCorrect() == null) {
            return true;
        }
        return graded.confidence() != null && graded.confidence() < 0.7;
    }

    private String firstKnowledge(GradedQuestion graded) {
        List<String> points = graded.knowledgePoints();
        return (points == null || points.isEmpty()) ? null : points.get(0);
    }

    private BigDecimal toScale5(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(5, RoundingMode.HALF_UP);
    }

    /**
     * 标记失败。
     *
     * @param refund 是否退还额度。只有能确认"模型没有产生计费"时才退还，
     *               否则用户可以通过制造失败来白嫖调用
     */
    private void fail(Long submitId, String code, String message, long startedAt, boolean refund) {
        HomeworkSubmit submit = submitMapper.selectById(submitId);
        int durationMs = startedAt <= 0 ? 0 : (int) (System.currentTimeMillis() - startedAt);

        submitMapper.update(null, Wrappers.<HomeworkSubmit>lambdaUpdate()
                .eq(HomeworkSubmit::getId, submitId)
                .set(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_FAILED)
                .set(HomeworkSubmit::getErrorCode, code)
                .set(HomeworkSubmit::getErrorMsg, message)
                .set(HomeworkSubmit::getDurationMs, durationMs)
                .set(HomeworkSubmit::getFinishedAt, LocalDateTime.now()));

        if (refund && submit != null) {
            quotaService.release(submit.getUserId());
            log.info("任务 {} 失败，已退还一次额度给用户 {}", submitId, submit.getUserId());
        }
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
