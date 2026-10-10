package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dxy.homework.common.BizException;
import com.dxy.homework.common.PageResult;
import com.dxy.homework.entity.AppFile;
import com.dxy.homework.entity.HomeworkSubmit;
import com.dxy.homework.entity.Mistake;
import com.dxy.homework.entity.MistakeReview;
import com.dxy.homework.entity.Question;
import com.dxy.homework.mapper.AppFileMapper;
import com.dxy.homework.mapper.HomeworkSubmitMapper;
import com.dxy.homework.mapper.MistakeMapper;
import com.dxy.homework.mapper.MistakeReviewMapper;
import com.dxy.homework.mapper.QuestionMapper;
import com.dxy.homework.storage.StorageClient;
import com.dxy.homework.vo.MistakeDetailVo;
import com.dxy.homework.vo.MistakeStatsVo;
import com.dxy.homework.vo.MistakeVo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 错题本：列表、详情、复习打卡、掌握度与复习排期、统计。
 *
 * <p>复习节奏用固定间隔表按掌握度取：1 / 2 / 4 / 7 / 15 / 30 天（艾宾浩斯式递增）。
 * 答对升一级，答错降一级并清零连对 —— 避免"蒙对一次就当掌握"。
 */
@Service
public class MistakeService {

    /** 索引即掌握度，值是以天为单位的复习间隔 */
    private static final int[] REVIEW_INTERVALS = {1, 2, 4, 7, 15, 30};
    private static final int MAX_MASTERY = 5;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int STATS_KNOWLEDGE_LIMIT = 10;

    private final MistakeMapper mistakeMapper;
    private final MistakeReviewMapper reviewMapper;
    private final QuestionMapper questionMapper;
    private final AppFileMapper fileMapper;
    private final HomeworkSubmitMapper submitMapper;
    private final StorageClient storageClient;
    private final MistakeArtifactService artifactService;

    public MistakeService(MistakeMapper mistakeMapper,
                          MistakeReviewMapper reviewMapper,
                          QuestionMapper questionMapper,
                          AppFileMapper fileMapper,
                          HomeworkSubmitMapper submitMapper,
                          StorageClient storageClient,
                          MistakeArtifactService artifactService) {
        this.mistakeMapper = mistakeMapper;
        this.reviewMapper = reviewMapper;
        this.questionMapper = questionMapper;
        this.fileMapper = fileMapper;
        this.submitMapper = submitMapper;
        this.storageClient = storageClient;
        this.artifactService = artifactService;
    }

    public PageResult<MistakeVo> list(Long userId, Long studentId, String subject, Integer status,
                                      long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        IPage<Mistake> result = mistakeMapper.selectPage(
                new Page<>(safePage, safeSize),
                Wrappers.<Mistake>lambdaQuery()
                        .eq(Mistake::getUserId, userId)
                        .eq(studentId != null, Mistake::getStudentId, studentId)
                        .eq(subject != null && !subject.isBlank(), Mistake::getSubject, subject)
                        .eq(status != null, Mistake::getStatus, status)
                        .orderByDesc(Mistake::getId));

        return PageResult.of(safePage, safeSize, result.getTotal(),
                result.getRecords().stream().map(this::toVo).toList());
    }

    public MistakeDetailVo detail(Long userId, Long mistakeId) {
        Mistake mistake = requireOwned(userId, mistakeId);

        // 题目行理论上不会被删，但历史错题以快照为准，这里只做增强
        Question question = mistake.getQuestionId() == null
                ? null : questionMapper.selectById(mistake.getQuestionId());

        List<MistakeDetailVo.ReviewLog> reviews = reviewMapper.selectList(
                        Wrappers.<MistakeReview>lambdaQuery()
                                .eq(MistakeReview::getMistakeId, mistakeId)
                                .orderByDesc(MistakeReview::getId)
                                .last("LIMIT 30"))
                .stream()
                .map(log -> new MistakeDetailVo.ReviewLog(
                        log.getId(),
                        Integer.valueOf(1).equals(log.getResult()),
                        log.getCreatedAt()))
                .toList();

        return new MistakeDetailVo(
                toVo(mistake),
                question == null ? null : question.getCorrectAnswer(),
                question == null ? null : question.getWrongReason(),
                question == null ? null : question.getAnalysis(),
                question == null || question.getKnowledgePoints() == null
                        ? List.of() : question.getKnowledgePoints(),
                presign(mistake.getCropFileId()),
                mistake.getQuestionId(),
                reviews);
    }

    /** 复习打卡：更新掌握度与下次复习时间，并留一条记录。 */
    @Transactional(rollbackFor = Exception.class)
    public MistakeDetailVo review(Long userId, Long mistakeId, boolean correct) {
        Mistake mistake = requireOwned(userId, mistakeId);

        int level = orZero(mistake.getMasteryLevel());
        int streak = orZero(mistake.getRightStreak());
        if (correct) {
            streak++;
            level = Math.min(MAX_MASTERY, level + 1);
        } else {
            streak = 0;
            level = Math.max(0, level - 1);
        }

        LocalDateTime now = LocalDateTime.now();
        int intervalDays = REVIEW_INTERVALS[Math.min(level, REVIEW_INTERVALS.length - 1)];
        int newStatus = (correct && level >= MAX_MASTERY) ? Mistake.STATUS_MASTERED : Mistake.STATUS_ACTIVE;

        // 条件里带上 user_id 双保险，即便上层漏校验也改不到别人的数据
        mistakeMapper.update(null, Wrappers.<Mistake>lambdaUpdate()
                .eq(Mistake::getId, mistakeId)
                .eq(Mistake::getUserId, userId)
                .set(Mistake::getMasteryLevel, level)
                .set(Mistake::getRightStreak, streak)
                .set(Mistake::getReviewCount, orZero(mistake.getReviewCount()) + 1)
                .set(Mistake::getLastReviewAt, now)
                .set(Mistake::getNextReviewAt, now.plusDays(intervalDays))
                .set(Mistake::getStatus, newStatus));

        MistakeReview log = new MistakeReview();
        log.setMistakeId(mistakeId);
        log.setUserId(userId);
        log.setResult(correct ? 1 : 0);
        reviewMapper.insert(log);

        return detail(userId, mistakeId);
    }

    /**
     * 重新生成错题卡片。
     *
     * <p>必须有的补偿入口：批改时卡片生成失败是被容忍的（不能让整次批改失败），
     * 用户后来点开这张错题时就需要能补上。
     */
    public MistakeVo regenerateCard(Long userId, Long mistakeId) {
        Mistake mistake = requireOwned(userId, mistakeId);
        Question question = mistake.getQuestionId() == null
                ? null : questionMapper.selectById(mistake.getQuestionId());
        if (question == null) {
            throw BizException.notFound("题目");
        }
        HomeworkSubmit submit = submitMapper.selectById(mistake.getSubmitId());
        if (submit == null) {
            throw BizException.notFound("批改任务");
        }

        String footer = "拍照批改作业 · " + LocalDate.now();
        Long cardFileId = artifactService.renderCardFor(submit, question, footer);
        mistakeMapper.update(null, Wrappers.<Mistake>lambdaUpdate()
                .eq(Mistake::getId, mistakeId)
                .eq(Mistake::getUserId, userId)
                .set(Mistake::getCardFileId, cardFileId));

        return toVo(requireOwned(userId, mistakeId));
    }

    /** 手动调整状态：标为已掌握或从错题本移除。 */
    public MistakeVo updateStatus(Long userId, Long mistakeId, Integer status) {
        if (status == null) {
            return toVo(requireOwned(userId, mistakeId));
        }
        requireOwned(userId, mistakeId);
        mistakeMapper.update(null, Wrappers.<Mistake>lambdaUpdate()
                .eq(Mistake::getId, mistakeId)
                .eq(Mistake::getUserId, userId)
                .set(Mistake::getStatus, status));
        return toVo(requireOwned(userId, mistakeId));
    }

    /** 今日待复习（含已过期的），按到期时间升序。 */
    public List<MistakeVo> reviewToday(Long userId, Long studentId, int limit) {
        int safeLimit = Math.min(Math.max(1, limit), MAX_PAGE_SIZE);
        return mistakeMapper.selectList(Wrappers.<Mistake>lambdaQuery()
                        .eq(Mistake::getUserId, userId)
                        .eq(studentId != null, Mistake::getStudentId, studentId)
                        .eq(Mistake::getStatus, Mistake.STATUS_ACTIVE)
                        .le(Mistake::getNextReviewAt, LocalDateTime.now())
                        .orderByAsc(Mistake::getNextReviewAt)
                        .last("LIMIT " + safeLimit))
                .stream()
                .map(this::toVo)
                .toList();
    }

    public MistakeStatsVo stats(Long userId, Long studentId) {
        long total = count(userId, studentId, null);
        long active = count(userId, studentId, Mistake.STATUS_ACTIVE);
        long mastered = count(userId, studentId, Mistake.STATUS_MASTERED);

        // "今日待复习"= 到期时间早于今天结束，包含之前欠下的
        LocalDateTime endOfToday = LocalDate.now().atTime(LocalTime.MAX);
        Long dueToday = mistakeMapper.selectCount(Wrappers.<Mistake>lambdaQuery()
                .eq(Mistake::getUserId, userId)
                .eq(studentId != null, Mistake::getStudentId, studentId)
                .eq(Mistake::getStatus, Mistake.STATUS_ACTIVE)
                .le(Mistake::getNextReviewAt, endOfToday));

        return new MistakeStatsVo(total, active, mastered, dueToday == null ? 0 : dueToday,
                groupCount(userId, studentId, "subject"), groupCount(userId, studentId, "knowledge_key"));
    }

    private long count(Long userId, Long studentId, Integer status) {
        Long value = mistakeMapper.selectCount(Wrappers.<Mistake>lambdaQuery()
                .eq(Mistake::getUserId, userId)
                .eq(studentId != null, Mistake::getStudentId, studentId)
                .eq(status != null, Mistake::getStatus, status));
        return value == null ? 0 : value;
    }

    /** 按某列分组计数，只在册错题参与统计。 */
    private List<MistakeStatsVo.CountItem> groupCount(Long userId, Long studentId, String column) {
        QueryWrapper<Mistake> wrapper = new QueryWrapper<>();
        wrapper.select(column + " AS name", "COUNT(*) AS cnt")
                .eq("user_id", userId)
                .eq("status", Mistake.STATUS_ACTIVE)
                .isNotNull(column)
                .groupBy(column)
                .orderByDesc("cnt")
                .last("LIMIT " + STATS_KNOWLEDGE_LIMIT);
        if (studentId != null) {
            wrapper.eq("student_id", studentId);
        }

        return mistakeMapper.selectMaps(wrapper).stream()
                .map(row -> new MistakeStatsVo.CountItem(
                        String.valueOf(row.get("name")),
                        ((Number) row.getOrDefault("cnt", 0)).longValue()))
                .toList();
    }

    private MistakeVo toVo(Mistake mistake) {
        return MistakeVo.from(mistake, presign(mistake.getCardFileId()));
    }

    private String presign(Long fileId) {
        if (fileId == null) {
            return null;
        }
        AppFile file = fileMapper.selectById(fileId);
        return file == null ? null : storageClient.presignedUrl(file.getFileKey());
    }

    private Mistake requireOwned(Long userId, Long mistakeId) {
        Mistake mistake = mistakeMapper.selectById(mistakeId);
        if (mistake == null) {
            throw BizException.notFound("错题");
        }
        if (!mistake.getUserId().equals(userId)) {
            throw BizException.forbidden();
        }
        return mistake;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
