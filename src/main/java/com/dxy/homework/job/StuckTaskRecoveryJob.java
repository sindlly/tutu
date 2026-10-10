package com.dxy.homework.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.entity.HomeworkSubmit;
import com.dxy.homework.mapper.HomeworkSubmitMapper;
import com.dxy.homework.service.QuotaService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 卡住任务的兜底扫描。
 *
 * <p>解决的问题：应用在批改过程中重启、或者任务入队后线程池异常，任务会永远停在
 * PENDING 或 GRADING，用户一直看到"批改中"。
 *
 * <p>两类处理：
 * <ul>
 *   <li>PENDING 超过 1 分钟 —— 说明当时没被消费掉，重新入队重跑（幂等 CAS 保证不会重复处理）</li>
 *   <li>GRADING 超过 10 分钟 —— 基本是进程被杀或线程异常退出，标记失败并退还额度</li>
 * </ul>
 */
@Component
public class StuckTaskRecoveryJob {

    private static final Logger log = LoggerFactory.getLogger(StuckTaskRecoveryJob.class);

    private static final int PENDING_TIMEOUT_SECONDS = 60;
    private static final int GRADING_TIMEOUT_MINUTES = 10;
    private static final int BATCH_SIZE = 20;

    private final HomeworkSubmitMapper submitMapper;
    private final GradingPipeline pipeline;
    private final QuotaService quotaService;

    public StuckTaskRecoveryJob(HomeworkSubmitMapper submitMapper,
                                GradingPipeline pipeline,
                                QuotaService quotaService) {
        this.submitMapper = submitMapper;
        this.pipeline = pipeline;
        this.quotaService = quotaService;
    }

    @Scheduled(fixedDelayString = "PT2M", initialDelayString = "PT1M")
    public void recover() {
        requeueStalePending();
        failStaleGrading();
    }

    private void requeueStalePending() {
        List<HomeworkSubmit> stale = submitMapper.selectList(Wrappers.<HomeworkSubmit>lambdaQuery()
                .eq(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_PENDING)
                .lt(HomeworkSubmit::getCreatedAt, LocalDateTime.now().minusSeconds(PENDING_TIMEOUT_SECONDS))
                .orderByAsc(HomeworkSubmit::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (HomeworkSubmit submit : stale) {
            log.warn("任务 {} 长时间处于待处理，重新入队", submit.getId());
            pipeline.enqueue(submit.getId());
        }
    }

    private void failStaleGrading() {
        List<HomeworkSubmit> stale = submitMapper.selectList(Wrappers.<HomeworkSubmit>lambdaQuery()
                .eq(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_GRADING)
                .lt(HomeworkSubmit::getCreatedAt, LocalDateTime.now().minusMinutes(GRADING_TIMEOUT_MINUTES))
                .orderByAsc(HomeworkSubmit::getId)
                .last("LIMIT " + BATCH_SIZE));
        for (HomeworkSubmit submit : stale) {
            // 用带条件的更新兜底，避免和正在正常收尾的线程打架
            int updated = submitMapper.update(null, Wrappers.<HomeworkSubmit>lambdaUpdate()
                    .eq(HomeworkSubmit::getId, submit.getId())
                    .eq(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_GRADING)
                    .set(HomeworkSubmit::getStatus, HomeworkSubmit.STATUS_FAILED)
                    .set(HomeworkSubmit::getErrorCode, "TIMEOUT")
                    .set(HomeworkSubmit::getErrorMsg, "批改超时，请重试")
                    .set(HomeworkSubmit::getFinishedAt, LocalDateTime.now()));
            if (updated == 1) {
                log.error("任务 {} 在批改中卡住超过 {} 分钟，标记为超时失败", submit.getId(), GRADING_TIMEOUT_MINUTES);
                // 无法确认模型是否已计费，但任务确实没产出结果，按对用户有利处理
                quotaService.release(submit.getUserId());
            }
        }
    }
}
