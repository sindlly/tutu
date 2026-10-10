package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.PageResult;
import com.dxy.homework.common.R;
import com.dxy.homework.dto.MistakeReviewRequest;
import com.dxy.homework.dto.MistakeUpdateRequest;
import com.dxy.homework.service.MistakeService;
import com.dxy.homework.vo.MistakeDetailVo;
import com.dxy.homework.vo.MistakeStatsVo;
import com.dxy.homework.vo.MistakeVo;
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

@RestController
@RequestMapping("/api/v1/mistakes")
public class MistakeController {

    private final MistakeService mistakeService;

    public MistakeController(MistakeService mistakeService) {
        this.mistakeService = mistakeService;
    }

    @GetMapping
    public R<PageResult<MistakeVo>> list(
            @RequestParam(value = "studentId", required = false) Long studentId,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "size", defaultValue = "20") long size) {
        return R.ok(mistakeService.list(AuthContext.requireUserId(), studentId, subject, status, page, size));
    }

    /** 今日待复习。含之前欠下的，按到期时间升序，建议的复习顺序。 */
    @GetMapping("/review-today")
    public R<List<MistakeVo>> reviewToday(
            @RequestParam(value = "studentId", required = false) Long studentId,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return R.ok(mistakeService.reviewToday(AuthContext.requireUserId(), studentId, limit));
    }

    @GetMapping("/stats")
    public R<MistakeStatsVo> stats(@RequestParam(value = "studentId", required = false) Long studentId) {
        return R.ok(mistakeService.stats(AuthContext.requireUserId(), studentId));
    }

    @GetMapping("/{id}")
    public R<MistakeDetailVo> detail(@PathVariable("id") Long id) {
        return R.ok(mistakeService.detail(AuthContext.requireUserId(), id));
    }

    /** 复习打卡。答对升掌握度并延长下次复习间隔，答错降级并清零连对。 */
    @PostMapping("/{id}/review")
    public R<MistakeDetailVo> review(@PathVariable("id") Long id,
                                     @Valid @RequestBody MistakeReviewRequest request) {
        return R.ok(mistakeService.review(AuthContext.requireUserId(), id, request.correct()));
    }

    /** 重新生成错题卡片。批改时卡片生成失败会被容忍，用户在这里补。 */
    @PostMapping("/{id}/card")
    public R<MistakeVo> regenerateCard(@PathVariable("id") Long id) {
        return R.ok(mistakeService.regenerateCard(AuthContext.requireUserId(), id));
    }

    /** 手动标记：1 已掌握，2 移除。 */
    @PutMapping("/{id}")
    public R<MistakeVo> update(@PathVariable("id") Long id,
                               @Valid @RequestBody MistakeUpdateRequest request) {
        return R.ok(mistakeService.updateStatus(AuthContext.requireUserId(), id, request.status()));
    }
}
