package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.PageResult;
import com.dxy.homework.common.R;
import com.dxy.homework.dto.SubmitCreateRequest;
import com.dxy.homework.service.HomeworkService;
import com.dxy.homework.vo.SubmitDetailVo;
import com.dxy.homework.vo.SubmitVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/homework")
public class HomeworkController {

    private final HomeworkService homeworkService;

    public HomeworkController(HomeworkService homeworkService) {
        this.homeworkService = homeworkService;
    }

    /**
     * 创建批改任务。返回后任务在后台异步执行，客户端拿 submitId 轮询状态。
     *
     * <p>同一份作业重复提交请复用同一个 bizNo，服务端会返回同一个任务而不是重复批改。
     */
    @PostMapping("/submits")
    public R<SubmitVo> create(@Valid @RequestBody SubmitCreateRequest request) {
        return R.ok(homeworkService.create(AuthContext.requireUserId(), request));
    }

    /** 任务状态，用于轮询。finished=true 时可以直接去取结果。 */
    @GetMapping("/submits/{id}")
    public R<SubmitVo> status(@PathVariable("id") Long id) {
        return R.ok(homeworkService.getStatus(AuthContext.requireUserId(), id));
    }

    /** 批改结果：原图地址（按页序）+ 逐题明细。 */
    @GetMapping("/submits/{id}/result")
    public R<SubmitDetailVo> result(@PathVariable("id") Long id) {
        return R.ok(homeworkService.getDetail(AuthContext.requireUserId(), id));
    }

    @GetMapping("/submits")
    public R<PageResult<SubmitVo>> list(@RequestParam(value = "studentId", required = false) Long studentId,
                                        @RequestParam(value = "page", defaultValue = "1") long page,
                                        @RequestParam(value = "size", defaultValue = "20") long size) {
        return R.ok(homeworkService.list(AuthContext.requireUserId(), studentId, page, size));
    }

    /** 取消排队中的任务并退还额度。已在批改中的无法取消。 */
    @PostMapping("/submits/{id}/cancel")
    public R<SubmitVo> cancel(@PathVariable("id") Long id) {
        return R.ok(homeworkService.cancel(AuthContext.requireUserId(), id));
    }

    /** 重试失败的任务。 */
    @PostMapping("/submits/{id}/retry")
    public R<SubmitVo> retry(@PathVariable("id") Long id) {
        return R.ok(homeworkService.retry(AuthContext.requireUserId(), id));
    }
}
