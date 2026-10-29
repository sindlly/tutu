package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dxy.homework.common.BizException;
import com.dxy.homework.common.PageResult;
import com.dxy.homework.dto.PointsAdjustRequest;
import com.dxy.homework.entity.PointsLog;
import com.dxy.homework.mapper.PointsLogMapper;
import com.dxy.homework.vo.PointsLogVo;
import com.dxy.homework.vo.PointsSummaryVo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 积分：流水 + 汇总 + 家长调整。
 *
 * <p>积分跟孩子走（不是跟家长走），家人绑定后看到的是同一份余额与流水。
 * 余额永远从流水求和，见 {@link PointsLog} 的说明。
 */
@Service
public class PointsService {

    private final PointsLogMapper pointsLogMapper;
    private final StudentAccessService accessService;

    public PointsService(PointsLogMapper pointsLogMapper, StudentAccessService accessService) {
        this.pointsLogMapper = pointsLogMapper;
        this.accessService = accessService;
    }

    /** 供打卡/撤销复用的记账入口，绕过访问校验（调用方自己已校验）。 */
    public void record(Long studentId, int delta, int source, Long refId, String reason, Long userId) {
        PointsLog log = new PointsLog();
        log.setStudentId(studentId);
        log.setDelta(delta);
        log.setSource(source);
        log.setRefId(refId);
        log.setReason(reason);
        log.setUserId(userId);
        pointsLogMapper.insert(log);
    }

    public PointsSummaryVo summary(Long userId, Long studentId) {
        accessService.requireAccess(userId, studentId);
        return summaryOf(studentId);
    }

    /** 不带权限校验的汇总，内部（任务面板）使用。 */
    public PointsSummaryVo summaryOf(Long studentId) {
        long balance = 0;
        long earned = 0;
        long spent = 0;
        for (PointsLog log : pointsLogMapper.selectList(Wrappers.<PointsLog>lambdaQuery()
                .select(PointsLog::getDelta)
                .eq(PointsLog::getStudentId, studentId))) {
            int d = log.getDelta() == null ? 0 : log.getDelta();
            balance += d;
            if (d > 0) {
                earned += d;
            } else {
                spent += -d;
            }
        }
        // 「今日获得」按当天流水求和：打卡 +10 又撤销 -10 = 今日 0，符合直觉
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = LocalDate.now().plusDays(1).atStartOfDay();
        long today = 0;
        for (PointsLog log : pointsLogMapper.selectList(Wrappers.<PointsLog>lambdaQuery()
                .select(PointsLog::getDelta)
                .eq(PointsLog::getStudentId, studentId)
                .ge(PointsLog::getCreatedAt, dayStart)
                .lt(PointsLog::getCreatedAt, dayEnd))) {
            today += log.getDelta() == null ? 0 : log.getDelta();
        }
        return new PointsSummaryVo((int) balance, (int) today, (int) earned, (int) spent);
    }

    public PageResult<PointsLogVo> logs(Long userId, Long studentId, long page, long size) {
        accessService.requireAccess(userId, studentId);
        long safePage = Math.max(1, page);
        long safeSize = Math.min(Math.max(1, size), 50);
        IPage<PointsLog> result = pointsLogMapper.selectPage(
                new Page<>(safePage, safeSize),
                Wrappers.<PointsLog>lambdaQuery()
                        .eq(PointsLog::getStudentId, studentId)
                        .orderByDesc(PointsLog::getId));
        return PageResult.of(safePage, safeSize, result.getTotal(),
                result.getRecords().stream().map(PointsLogVo::from).toList());
    }

    /** 家长手动调整：奖励或兑换消耗。负数必须写备注，不然流水看不懂。 */
    public PointsLogVo adjust(Long userId, PointsAdjustRequest request) {
        accessService.requireAccess(userId, request.studentId());
        if (request.delta() == null || request.delta() == 0) {
            throw BizException.badRequest("变动值不能为 0");
        }
        if (request.delta() < 0 && !StringUtils.hasText(request.reason())) {
            throw BizException.badRequest("消耗积分要写清楚兑换了什么");
        }
        record(request.studentId(), request.delta(), PointsLog.SOURCE_MANUAL, null,
                request.reason(), userId);
        return null;
    }
}
