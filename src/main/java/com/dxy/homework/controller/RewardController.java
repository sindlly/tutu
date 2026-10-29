package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.R;
import com.dxy.homework.dto.RewardCreateRequest;
import com.dxy.homework.dto.RewardUpdateRequest;
import com.dxy.homework.service.RewardService;
import com.dxy.homework.vo.PointsSummaryVo;
import com.dxy.homework.vo.RewardVo;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 奖励中心：预设可兑换奖励，积分够就能换。 */
@RestController
@RequestMapping("/api/v1/rewards")
public class RewardController {

    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @GetMapping
    public R<List<RewardVo>> list(@RequestParam("studentId") Long studentId) {
        return R.ok(rewardService.list(AuthContext.requireUserId(), studentId));
    }

    @PostMapping
    public R<RewardVo> create(@Valid @RequestBody RewardCreateRequest request) {
        return R.ok(rewardService.create(AuthContext.requireUserId(), request));
    }

    @PutMapping("/{id}")
    public R<RewardVo> update(@PathVariable("id") Long id, @Valid @RequestBody RewardUpdateRequest request) {
        return R.ok(rewardService.update(AuthContext.requireUserId(), id, request));
    }

    /** 兑换：余额不足 400，成功返回最新积分汇总。 */
    @PostMapping("/{id}/redeem")
    public R<PointsSummaryVo> redeem(@PathVariable("id") Long id) {
        return R.ok(rewardService.redeem(AuthContext.requireUserId(), id));
    }
}
