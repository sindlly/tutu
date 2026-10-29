package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.PageResult;
import com.dxy.homework.common.R;
import com.dxy.homework.dto.PointsAdjustRequest;
import com.dxy.homework.service.PointsService;
import com.dxy.homework.vo.PointsLogVo;
import com.dxy.homework.vo.PointsSummaryVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 积分：余额、流水、家长调整。 */
@RestController
@RequestMapping("/api/v1/points")
public class PointsController {

    private final PointsService pointsService;

    public PointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @GetMapping("/summary")
    public R<PointsSummaryVo> summary(@RequestParam("studentId") Long studentId) {
        return R.ok(pointsService.summary(AuthContext.requireUserId(), studentId));
    }

    @GetMapping("/logs")
    public R<PageResult<PointsLogVo>> logs(@RequestParam("studentId") Long studentId,
                                           @RequestParam(value = "page", defaultValue = "1") long page,
                                           @RequestParam(value = "size", defaultValue = "20") long size) {
        return R.ok(pointsService.logs(AuthContext.requireUserId(), studentId, page, size));
    }

    /** 家长调整：正数奖励，负数兑换消耗（必须写备注）。 */
    @PostMapping("/adjust")
    public R<PointsSummaryVo> adjust(@Valid @RequestBody PointsAdjustRequest request) {
        pointsService.adjust(AuthContext.requireUserId(), request);
        return R.ok(pointsService.summary(AuthContext.requireUserId(), request.studentId()));
    }
}
