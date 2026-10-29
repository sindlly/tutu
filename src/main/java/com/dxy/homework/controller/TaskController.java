package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.R;
import com.dxy.homework.dto.TaskCompleteRequest;
import com.dxy.homework.dto.TaskCreateRequest;
import com.dxy.homework.dto.TaskUpdateRequest;
import com.dxy.homework.service.TaskService;
import com.dxy.homework.vo.TaskCompletionsVo;
import com.dxy.homework.vo.TaskTodayVo;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 每日任务。 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /** 今日面板：任务 + 打卡状态 + 积分概览。首次访问会种下默认任务。 */
    @GetMapping("/today")
    public R<TaskTodayVo> today(@RequestParam("studentId") Long studentId) {
        return R.ok(taskService.today(AuthContext.requireUserId(), studentId));
    }

    @PostMapping
    public R<TaskTodayVo.Item> create(@Valid @RequestBody TaskCreateRequest request) {
        return R.ok(taskService.create(AuthContext.requireUserId(), request));
    }

    @PutMapping("/{id}")
    public R<TaskTodayVo.Item> update(@PathVariable("id") Long id,
                                      @Valid @RequestBody TaskUpdateRequest request) {
        return R.ok(taskService.update(AuthContext.requireUserId(), id, request));
    }

    /** 打卡（幂等，可带凭证图）。 */
    @PostMapping("/{id}/complete")
    public R<TaskTodayVo.Item> complete(@PathVariable("id") Long id,
                                        @RequestBody(required = false) TaskCompleteRequest request) {
        return R.ok(taskService.complete(AuthContext.requireUserId(), id,
                request == null ? null : request.imageFileId()));
    }

    /** 撤销今日打卡（写反向积分流水）。 */
    @PostMapping("/{id}/undo")
    public R<Void> undo(@PathVariable("id") Long id) {
        taskService.undo(AuthContext.requireUserId(), id);
        return R.ok(null);
    }

    /** 归档（软删）。 */
    @PostMapping("/{id}/archive")
    public R<Void> archive(@PathVariable("id") Long id) {
        taskService.archive(AuthContext.requireUserId(), id);
        return R.ok(null);
    }

    /** 打卡历史：range=day/week/month，date=区间锚点日期（默认今天）。 */
    @GetMapping("/completions")
    public R<TaskCompletionsVo> completions(@RequestParam("studentId") Long studentId,
                                            @RequestParam(value = "range", defaultValue = "day") String range,
                                            @RequestParam(value = "date", required = false) String date) {
        return R.ok(taskService.completions(AuthContext.requireUserId(), studentId, range, date));
    }
}
