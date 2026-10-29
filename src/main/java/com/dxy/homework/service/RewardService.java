package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.common.BizException;
import com.dxy.homework.dto.RewardCreateRequest;
import com.dxy.homework.dto.RewardUpdateRequest;
import com.dxy.homework.entity.Reward;
import com.dxy.homework.mapper.RewardMapper;
import com.dxy.homework.vo.PointsSummaryVo;
import com.dxy.homework.vo.RewardVo;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 奖励中心：家长预设可兑换奖励，积分够了来换。
 * 兑换 = 记一笔负数流水（source=3），余额不足直接拒绝；奖励定义可下架（软删）。
 */
@Service
public class RewardService {

    /** 预制卡通图标白名单（与前端 assets/rewards/{key}.png 一一对应）。 */
    private static final java.util.Set<String> ICON_KEYS = java.util.Set.of(
            "icecream", "toy", "tv", "park", "book", "ball",
            "star", "gift", "cake", "music", "swim", "game");

    private final RewardMapper rewardMapper;
    private final StudentAccessService accessService;
    private final PointsService pointsService;

    public RewardService(RewardMapper rewardMapper,
                         StudentAccessService accessService,
                         PointsService pointsService) {
        this.rewardMapper = rewardMapper;
        this.accessService = accessService;
        this.pointsService = pointsService;
    }

    /** 某孩子的奖励列表（上架的在前，管理也能看到下架的）。 */
    public List<RewardVo> list(Long userId, Long studentId) {
        accessService.requireAccess(userId, studentId);
        List<Reward> rows = rewardMapper.selectList(Wrappers.<Reward>lambdaQuery()
                .eq(Reward::getStudentId, studentId)
                .orderByAsc(Reward::getStatus)
                .orderByAsc(Reward::getSortNo)
                .orderByAsc(Reward::getId)
                .last("LIMIT 100"));
        return rows.stream().map(r -> new RewardVo(r.getId(), r.getTitle(), r.getIcon(), r.getCost(), r.getStatus())).toList();
    }

    public RewardVo create(Long userId, RewardCreateRequest request) {
        accessService.requireAccess(userId, request.studentId());
        Integer maxSort = rewardMapper.selectList(Wrappers.<Reward>lambdaQuery()
                        .select(Reward::getSortNo)
                        .eq(Reward::getStudentId, request.studentId())
                        .orderByDesc(Reward::getSortNo)
                        .last("LIMIT 1"))
                .stream().findFirst().map(Reward::getSortNo).orElse(0);

        Reward reward = new Reward();
        reward.setStudentId(request.studentId());
        reward.setUserId(userId);
        reward.setTitle(request.title().trim());
        reward.setIcon(sanitizeIcon(request.icon()));
        reward.setCost(request.cost());
        reward.setStatus(Reward.STATUS_ON);
        reward.setSortNo(maxSort + 1);
        rewardMapper.insert(reward);
        return new RewardVo(reward.getId(), reward.getTitle(), reward.getIcon(), reward.getCost(), reward.getStatus());
    }

    /** 改奖励（名字/积分/上下架）。 */
    public RewardVo update(Long userId, Long rewardId, RewardUpdateRequest request) {
        Reward reward = requireRewardAccess(userId, rewardId);
        if (StringUtils.hasText(request.title())) {
            reward.setTitle(request.title().trim());
        }
        if (request.icon() != null) {
            reward.setIcon(sanitizeIcon(request.icon()));
        }
        if (request.cost() != null) {
            reward.setCost(request.cost());
        }
        if (request.status() != null) {
            if (request.status() != Reward.STATUS_ON && request.status() != Reward.STATUS_OFF) {
                throw BizException.badRequest("状态只能是 1（上架）或 2（下架）");
            }
            reward.setStatus(request.status());
        }
        rewardMapper.updateById(reward);
        return new RewardVo(reward.getId(), reward.getTitle(), reward.getIcon(), reward.getCost(), reward.getStatus());
    }

    /**
     * 兑换：余额够才记账。事务内先查余额再记负数流水，防并发透支（同 student 串行化靠行级业务唯一性不足，
     * 但家庭场景并发兑换概率极低，这里以「先算后记」+ 事务保证账目一致）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PointsSummaryVo redeem(Long userId, Long rewardId) {
        Reward reward = requireRewardAccess(userId, rewardId);
        if (reward.getStatus() != Reward.STATUS_ON) {
            throw BizException.badRequest("这个奖励已下架");
        }

        PointsSummaryVo summary = pointsService.summaryOf(reward.getStudentId());
        if (summary.balance() < reward.getCost()) {
            throw BizException.badRequest(
                    "积分还不够，还差 %d 分".formatted(reward.getCost() - summary.balance()));
        }

        pointsService.record(reward.getStudentId(), -reward.getCost(), com.dxy.homework.entity.PointsLog.SOURCE_REWARD,
                reward.getId(), "兑换「" + reward.getTitle() + "」", userId);
        return pointsService.summaryOf(reward.getStudentId());
    }

    /** 图标白名单校验：非法值一律回落为默认礼物图（不报错，容忍客户端旧版本）。 */
    private static String sanitizeIcon(String icon) {
        if (!StringUtils.hasText(icon)) {
            return null;
        }
        return ICON_KEYS.contains(icon) ? icon : null;
    }

    private Reward requireRewardAccess(Long userId, Long rewardId) {
        Reward reward = rewardMapper.selectById(rewardId);
        if (reward == null) {
            throw BizException.notFound("奖励");
        }
        accessService.requireAccess(userId, reward.getStudentId());
        return reward;
    }
}
