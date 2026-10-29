package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.common.BizException;
import com.dxy.homework.dto.TaskCreateRequest;
import com.dxy.homework.dto.TaskUpdateRequest;
import com.dxy.homework.entity.AppFile;
import com.dxy.homework.entity.Task;
import com.dxy.homework.entity.TaskCompletion;
import com.dxy.homework.mapper.TaskCompletionMapper;
import com.dxy.homework.mapper.TaskMapper;
import com.dxy.homework.vo.PointsSummaryVo;
import com.dxy.homework.vo.TaskCompletionsVo;
import com.dxy.homework.vo.TaskTodayVo;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 每日任务：列表、添加、打卡、撤销。
 *
 * <p>跨天不需要任何「重置」动作 —— 完成度按 complete_date = 今天 过滤，
 * 昨天的打卡自然不在今天的面板上。积分已入账不动。
 *
 * <p>打卡幂等：当天已打过直接返回，不重复加分（uk_task_date 兜并发）。
 * 撤销不删历史流水，写反向流水，账目永远可审计。
 */
@Service
public class TaskService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** 新孩子第一次打开任务面板时种下的默认任务，家长可改可删 */
    private static final int[][] DEFAULT_TASKS = {
            {10, 1}, {5, 2}, {5, 3}, {3, 4}
    };
    private static final String[] DEFAULT_TITLES = {
            "完成学校作业", "口算练习一页", "课外阅读 20 分钟", "主动收拾书桌"
    };

    private final TaskMapper taskMapper;
    private final TaskCompletionMapper completionMapper;
    private final StudentAccessService accessService;
    private final PointsService pointsService;
    private final FileService fileService;

    public TaskService(TaskMapper taskMapper,
                       TaskCompletionMapper completionMapper,
                       StudentAccessService accessService,
                       PointsService pointsService,
                       FileService fileService) {
        this.taskMapper = taskMapper;
        this.completionMapper = completionMapper;
        this.accessService = accessService;
        this.pointsService = pointsService;
        this.fileService = fileService;
    }

    /** 今日任务面板：任务列表（含打卡状态）+ 积分概览。 */
    public TaskTodayVo today(Long userId, Long studentId) {
        accessService.requireAccess(userId, studentId);
        seedDefaultTasks(studentId);

        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getStudentId, studentId)
                .eq(Task::getStatus, Task.STATUS_ACTIVE)
                .orderByAsc(Task::getSortNo)
                .orderByAsc(Task::getId));

        Map<Long, TaskCompletion> todayDone = completionMapper.selectList(
                        Wrappers.<TaskCompletion>lambdaQuery()
                                .eq(TaskCompletion::getStudentId, studentId)
                                .eq(TaskCompletion::getCompleteDate, LocalDate.now()))
                .stream()
                .collect(Collectors.toMap(TaskCompletion::getTaskId, Function.identity(), (a, b) -> a));

        List<TaskTodayVo.Item> items = tasks.stream().map(t -> {
            TaskCompletion done = todayDone.get(t.getId());
            return new TaskTodayVo.Item(t.getId(), t.getTitle(), t.getPoints(),
                    done != null,
                    done == null ? null : done.getCreatedAt().format(TIME),
                    presignImage(t.getImageFileId()));
        }).toList();

        PointsSummaryVo points = pointsService.summaryOf(studentId);
        TaskTodayVo.Summary summary = new TaskTodayVo.Summary(
                points.balance(), points.todayEarned(),
                (int) items.stream().filter(TaskTodayVo.Item::completed).count(), items.size());
        return new TaskTodayVo(LocalDate.now().toString(), items, summary);
    }

    /** 添加任务。对该孩子有访问权的家人都能加（挂孩子名下）。 */
    public TaskTodayVo.Item create(Long userId, TaskCreateRequest request) {
        accessService.requireAccess(userId, request.studentId());
        String title = sanitize(request.title());

        Integer maxSort = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                        .select(Task::getSortNo)
                        .eq(Task::getStudentId, request.studentId())
                        .orderByDesc(Task::getSortNo)
                        .last("LIMIT 1"))
                .stream().findFirst().map(Task::getSortNo).orElse(0);

        Task task = new Task();
        task.setStudentId(request.studentId());
        task.setUserId(userId);
        task.setTitle(title);
        task.setPoints(request.points());
        task.setImageFileId(request.imageFileId() == null
                ? null : requireOwnedTaskImage(userId, request.imageFileId()));
        task.setSortNo(maxSort + 1);
        task.setStatus(Task.STATUS_ACTIVE);
        taskMapper.insert(task);
        return new TaskTodayVo.Item(task.getId(), task.getTitle(), task.getPoints(),
                false, null, presignImage(task.getImageFileId()));
    }

    /** 改任务（名字/积分/归档）。仅任务创建者或孩子档案创建者。 */
    public TaskTodayVo.Item update(Long userId, Long taskId, TaskUpdateRequest request) {
        Task task = requireTaskAccess(userId, taskId);
        if (StringUtils.hasText(request.title())) {
            task.setTitle(sanitize(request.title()));
        }
        if (request.points() != null) {
            task.setPoints(request.points());
        }
        if (request.imageFileId() != null) {
            task.setImageFileId(requireOwnedTaskImage(userId, request.imageFileId()));
        }
        if (request.status() != null) {
            if (request.status() != Task.STATUS_ACTIVE && request.status() != Task.STATUS_ARCHIVED) {
                throw BizException.badRequest("状态只能是 1（启用）或 2（归档）");
            }
            task.setStatus(request.status());
        }
        taskMapper.updateById(task);
        return new TaskTodayVo.Item(task.getId(), task.getTitle(), task.getPoints(),
                false, null, presignImage(task.getImageFileId()));
    }

    /**
     * 打卡。幂等：已打过返回现状不重复加分；此时若带了新凭证图则仅更新凭证。
     * 打卡记录 + 积分流水在一个事务里，两边要么都成要么都不成。
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskTodayVo.Item complete(Long userId, Long taskId, Long imageFileId) {
        Task task = requireTaskAccess(userId, taskId);

        TaskCompletion existing = completionMapper.selectOne(Wrappers.<TaskCompletion>lambdaQuery()
                .eq(TaskCompletion::getTaskId, taskId)
                .eq(TaskCompletion::getCompleteDate, LocalDate.now())
                .last("LIMIT 1"));
        if (existing != null) {
            if (imageFileId != null && !imageFileId.equals(existing.getImageFileId())) {
                existing.setImageFileId(requireOwnedProof(userId, imageFileId));
                completionMapper.updateById(existing);
            }
            return new TaskTodayVo.Item(task.getId(), task.getTitle(), task.getPoints(),
                    true, existing.getCreatedAt().format(TIME),
                    presignImage(task.getImageFileId()));
        }

        TaskCompletion completion = new TaskCompletion();
        completion.setTaskId(taskId);
        completion.setStudentId(task.getStudentId());
        completion.setCompleteDate(LocalDate.now());
        completion.setPointsAwarded(task.getPoints());
        completion.setImageFileId(imageFileId == null ? null : requireOwnedProof(userId, imageFileId));
        completion.setUserId(userId);
        completionMapper.insert(completion);

        pointsService.record(task.getStudentId(), task.getPoints(),
                com.dxy.homework.entity.PointsLog.SOURCE_TASK, completion.getId(),
                "完成「" + task.getTitle() + "」", userId);
        return new TaskTodayVo.Item(task.getId(), task.getTitle(), task.getPoints(),
                true, completion.getCreatedAt().format(TIME),
                presignImage(task.getImageFileId()));
    }

    /** 撤销今日打卡：删记录 + 反向流水。昨天以前的不能撤（积分已算进历史）。 */
    @Transactional(rollbackFor = Exception.class)
    public void undo(Long userId, Long taskId) {
        Task task = requireTaskAccess(userId, taskId);
        TaskCompletion completion = completionMapper.selectOne(Wrappers.<TaskCompletion>lambdaQuery()
                .eq(TaskCompletion::getTaskId, taskId)
                .eq(TaskCompletion::getCompleteDate, LocalDate.now())
                .last("LIMIT 1"));
        if (completion == null) {
            return;
        }
        completionMapper.deleteById(completion.getId());
        pointsService.record(task.getStudentId(), -completion.getPointsAwarded(),
                com.dxy.homework.entity.PointsLog.SOURCE_TASK, completion.getId(),
                "撤销「" + task.getTitle() + "」", userId);
    }

    /** 任务归档（软删，历史流水还能对上账）。权限同 update。 */
    public void archive(Long userId, Long taskId) {
        Task task = requireTaskAccess(userId, taskId);
        task.setStatus(Task.STATUS_ARCHIVED);
        taskMapper.updateById(task);
    }

    /**
     * 打卡历史：按天/周/月查某孩子的完成记录（含任务详情图 URL）。
     * range=day → date 当天；week → date 所在周（周一~周日）；month → date 所在月。
     */
    public TaskCompletionsVo completions(Long userId, Long studentId, String range, String dateText) {
        accessService.requireAccess(userId, studentId);

        LocalDate date = parseDate(dateText);
        LocalDate start;
        LocalDate end;
        switch (range == null ? "day" : range) {
            case "day" -> {
                start = date;
                end = date;
            }
            case "week" -> {
                start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                end = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            }
            case "month" -> {
                start = date.withDayOfMonth(1);
                end = date.withDayOfMonth(date.lengthOfMonth());
            }
            default -> throw BizException.badRequest("range 只能是 day / week / month");
        }

        List<TaskCompletion> rows = completionMapper.selectList(Wrappers.<TaskCompletion>lambdaQuery()
                .eq(TaskCompletion::getStudentId, studentId)
                .between(TaskCompletion::getCompleteDate, start, end)
                .orderByDesc(TaskCompletion::getCompleteDate)
                .orderByDesc(TaskCompletion::getId)
                .last("LIMIT 500"));

        List<Task> taskRefs = rows.isEmpty() ? List.of()
                : taskMapper.selectBatchIds(rows.stream().map(TaskCompletion::getTaskId).distinct().toList());
        Map<Long, String> titles = taskRefs.stream()
                .collect(Collectors.toMap(Task::getId, Task::getTitle));

        // 任务详情图 presign（同任务多记录共用，按任务去重后逐个签）
        Map<Long, String> taskImages = taskRefs.stream()
                .filter(t -> t.getImageFileId() != null)
                .collect(Collectors.toMap(Task::getId, t -> presignImage(t.getImageFileId())));

        List<TaskCompletionsVo.Item> items = rows.stream().map(c -> new TaskCompletionsVo.Item(
                c.getId(), c.getTaskId(),
                titles.getOrDefault(c.getTaskId(), "已删除任务"),
                c.getPointsAwarded(),
                c.getCompleteDate().toString(),
                c.getCreatedAt().format(TIME),
                taskImages.get(c.getTaskId())
        )).toList();

        int earned = rows.stream().mapToInt(TaskCompletion::getPointsAwarded).sum();
        return new TaskCompletionsVo(range, start, end, items,
                new TaskCompletionsVo.Summary(items.size(), earned));
    }

    /** 校验打卡凭证图：存在、有效、且是当前用户上传的任务凭证（bizType=5）。 */
    private Long requireOwnedProof(Long userId, Long imageFileId) {
        AppFile file = fileService.requireOwned(userId, imageFileId);
        if (!Integer.valueOf(AppFile.BIZ_TASK_PROOF).equals(file.getBizType())) {
            throw BizException.badRequest("凭证图片类型不正确");
        }
        return imageFileId;
    }

    /** 校验任务详情图：存在、有效、且是当前用户上传的任务图（bizType=6）。 */
    private Long requireOwnedTaskImage(Long userId, Long imageFileId) {
        AppFile file = fileService.requireOwned(userId, imageFileId);
        if (!Integer.valueOf(AppFile.BIZ_TASK_IMAGE).equals(file.getBizType())) {
            throw BizException.badRequest("任务图片类型不正确");
        }
        return imageFileId;
    }

    /** 任务图 presign；家人共享场景由外层 studentId 访问权兜底（见 FileService#presignById 注释）。 */
    private String presignImage(Long imageFileId) {
        if (imageFileId == null) {
            return null;
        }
        return fileService.presignById(imageFileId);
    }

    private static LocalDate parseDate(String text) {
        if (!StringUtils.hasText(text)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw BizException.badRequest("date 格式应为 yyyy-MM-dd");
        }
    }

    private Task requireTaskAccess(Long userId, Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw BizException.notFound("任务");
        }
        accessService.requireAccess(userId, task.getStudentId());
        return task;
    }

    /**
     * 默认任务只种一次：判断依据是「曾创建过任何任务（含归档）」，
     * 不是「当前启用任务数」，家长删光后不会被硬塞回来。
     */
    private void seedDefaultTasks(Long studentId) {
        Long count = taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
                .eq(Task::getStudentId, studentId));
        if (count != null && count > 0) {
            return;
        }
        List<Task> seeds = new ArrayList<>(DEFAULT_TASKS.length);
        for (int i = 0; i < DEFAULT_TASKS.length; i++) {
            Task t = new Task();
            t.setStudentId(studentId);
            t.setUserId(null);
            t.setTitle(DEFAULT_TITLES[i]);
            t.setPoints(DEFAULT_TASKS[i][0]);
            t.setSortNo(DEFAULT_TASKS[i][1]);
            t.setStatus(Task.STATUS_ACTIVE);
            seeds.add(t);
            taskMapper.insert(t);
        }
    }

    private static String sanitize(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isBlank()) {
            throw BizException.badRequest("任务名不能为空");
        }
        return title;
    }
}
