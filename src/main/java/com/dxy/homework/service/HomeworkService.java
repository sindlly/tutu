package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dxy.homework.common.BizException;
import com.dxy.homework.common.ErrorCode;
import com.dxy.homework.common.PageResult;
import com.dxy.homework.dto.SubmitCreateRequest;
import com.dxy.homework.entity.HomeworkSubmit;
import com.dxy.homework.entity.Question;
import com.dxy.homework.entity.SubmitImage;
import com.dxy.homework.entity.User;
import com.dxy.homework.job.GradingPipeline;
import com.dxy.homework.mapper.AppFileMapper;
import com.dxy.homework.mapper.HomeworkSubmitMapper;
import com.dxy.homework.mapper.QuestionMapper;
import com.dxy.homework.mapper.SubmitImageMapper;
import com.dxy.homework.mapper.UserMapper;
import com.dxy.homework.storage.StorageClient;
import com.dxy.homework.vo.QuestionVo;
import com.dxy.homework.vo.SubmitDetailVo;
import com.dxy.homework.vo.SubmitVo;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 批改任务的对外服务：幂等创建、查询、取消、重试。
 *
 * <p>所有按 submitId 的读操作都必须先过 {@link #requireOwned}，避免用户拿到别人的作业。
 */
@Service
public class HomeworkService {

    private static final Logger log = LoggerFactory.getLogger(HomeworkService.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final HomeworkCreationService creationService;
    private final HomeworkSubmitMapper submitMapper;
    private final SubmitImageMapper submitImageMapper;
    private final QuestionMapper questionMapper;
    private final AppFileMapper fileMapper;
    private final UserMapper userMapper;
    private final StorageClient storageClient;
    private final QuotaService quotaService;
    private final GradingPipeline pipeline;

    public HomeworkService(HomeworkCreationService creationService,
                           HomeworkSubmitMapper submitMapper,
                           SubmitImageMapper submitImageMapper,
                           QuestionMapper questionMapper,
                           AppFileMapper fileMapper,
                           UserMapper userMapper,
                           StorageClient storageClient,
                           QuotaService quotaService,
                           GradingPipeline pipeline) {
        this.creationService = creationService;
        this.submitMapper = submitMapper;
        this.submitImageMapper = submitImageMapper;
        this.questionMapper = questionMapper;
        this.fileMapper = fileMapper;
        this.userMapper = userMapper;
        this.storageClient = storageClient;
        this.quotaService = quotaService;
        this.pipeline = pipeline;
    }

    /**
     * 创建批改任务。用 bizNo 做幂等：同一张作业重复提交（网络重试、用户连点）
     * 只会返回同一个任务，不会重复批改也不会重复扣额度。
     */
    public SubmitVo create(Long userId, SubmitCreateRequest request) {
        HomeworkSubmit existing = creationService.findByBizNo(request.bizNo());
        if (existing != null) {
            return SubmitVo.from(requireOwned(userId, existing));
        }

        HomeworkSubmit created;
        try {
            created = creationService.create(userId, request);
        } catch (DuplicateKeyException ex) {
            // 并发下两个请求带同一个 bizNo 同时穿过上面的检查，唯一索引会挡下一个。
            // 此时回查并返回已经落库的那个任务，对客户端来说仍然是幂等的。
            HomeworkSubmit raced = creationService.findByBizNo(request.bizNo());
            if (raced == null) {
                throw ex;
            }
            log.info("bizNo={} 并发重复提交，返回已存在的任务 {}", request.bizNo(), raced.getId());
            return SubmitVo.from(requireOwned(userId, raced));
        }

        // 必须在事务提交之后再入队，否则异步线程可能读不到刚插入的记录
        pipeline.enqueue(created.getId());
        return SubmitVo.from(created);
    }

    public SubmitVo getStatus(Long userId, Long submitId) {
        return SubmitVo.from(requireOwned(userId, submitMapper.selectById(submitId)));
    }

    public SubmitDetailVo getDetail(Long userId, Long submitId) {
        HomeworkSubmit submit = requireOwned(userId, submitMapper.selectById(submitId));

        List<String> images = submitImageMapper.selectList(Wrappers.<SubmitImage>lambdaQuery()
                        .eq(SubmitImage::getSubmitId, submitId)
                        .orderByAsc(SubmitImage::getSeq))
                .stream()
                .map(link -> fileMapper.selectById(link.getFileId()))
                .filter(Objects::nonNull)
                .map(file -> storageClient.presignedUrl(file.getFileKey()))
                .toList();

        List<QuestionVo> questions = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                        .eq(Question::getSubmitId, submitId)
                        .orderByAsc(Question::getSeq))
                .stream()
                .map(this::toVo)
                .toList();

        return new SubmitDetailVo(SubmitVo.from(submit), images, questions);
    }

    public PageResult<SubmitVo> list(Long userId, Long studentId, long page, long size) {
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);

        IPage<HomeworkSubmit> result = submitMapper.selectPage(
                new Page<>(safePage, safeSize),
                Wrappers.<HomeworkSubmit>lambdaQuery()
                        .eq(HomeworkSubmit::getUserId, userId)
                        .eq(studentId != null, HomeworkSubmit::getStudentId, studentId)
                        .orderByDesc(HomeworkSubmit::getId));

        return PageResult.of(safePage, safeSize, result.getTotal(),
                result.getRecords().stream().map(SubmitVo::from).toList());
    }

    /** 取消排队中的任务并退还额度。已经开始批改的无法取消，避免白扣一次调用。 */
    public SubmitVo cancel(Long userId, Long submitId) {
        HomeworkSubmit submit = requireOwned(userId, submitMapper.selectById(submitId));
        if (submit.getStatus() != HomeworkSubmit.STATUS_PENDING) {
            throw BizException.badRequest("任务已在处理中或已结束，无法取消");
        }
        int updated = submitMapper.update(null, Wrappers.<HomeworkSubmit>lambdaUpdate()
                .eq(HomeworkSubmit::getId, submitId)
                .eq(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_PENDING)
                .set(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_CANCELED)
                .set(HomeworkSubmit::getErrorCode, "CANCELED")
                .set(HomeworkSubmit::getErrorMsg, "已取消"));
        if (updated == 1) {
            quotaService.release(userId);
        }
        return SubmitVo.from(submitMapper.selectById(submitId));
    }

    /** 重试失败的任务。失败时额度已退还，这里重新占用一次。 */
    public SubmitVo retry(Long userId, Long submitId) {
        HomeworkSubmit submit = requireOwned(userId, submitMapper.selectById(submitId));
        if (submit.getStatus() != HomeworkSubmit.STATUS_FAILED) {
            throw BizException.badRequest("只有失败的任务可以重试");
        }

        User user = userMapper.selectById(userId);
        int dailyLimit = (user == null || user.getQuotaDaily() == null) ? 0 : user.getQuotaDaily();
        if (!quotaService.tryAcquire(userId, dailyLimit)) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED,
                    "今日 %d 次批改已用完，明天再来吧".formatted(dailyLimit));
        }

        int updated = submitMapper.update(null, Wrappers.<HomeworkSubmit>lambdaUpdate()
                .eq(HomeworkSubmit::getId, submitId)
                .eq(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_FAILED)
                .set(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_PENDING)
                .set(HomeworkSubmit::getRetryCount, orZero(submit.getRetryCount()) + 1));
        if (updated != 1) {
            // 状态被别人改过，把刚占用的额度还回去
            quotaService.release(userId);
            throw BizException.badRequest("任务状态已变更，请刷新后重试");
        }

        pipeline.enqueue(submitId);
        return SubmitVo.from(submitMapper.selectById(submitId));
    }

    private QuestionVo toVo(Question question) {
        String cropUrl = null;
        if (question.getCropFileId() != null) {
            var cropFile = fileMapper.selectById(question.getCropFileId());
            if (cropFile != null) {
                cropUrl = storageClient.presignedUrl(cropFile.getFileKey());
            }
        }
        return QuestionVo.from(question, cropUrl);
    }

    /** 归属校验：不是自己的任务一律按"无权限"拒绝。 */
    private HomeworkSubmit requireOwned(Long userId, HomeworkSubmit submit) {
        if (submit == null) {
            throw BizException.notFound("批改任务");
        }
        if (!submit.getUserId().equals(userId)) {
            throw BizException.forbidden();
        }
        return submit;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
