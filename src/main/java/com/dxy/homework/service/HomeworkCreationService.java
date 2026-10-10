package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.common.BizException;
import com.dxy.homework.common.ErrorCode;
import com.dxy.homework.dto.SubmitCreateRequest;
import com.dxy.homework.entity.AppFile;
import com.dxy.homework.entity.HomeworkSubmit;
import com.dxy.homework.entity.Student;
import com.dxy.homework.entity.SubmitImage;
import com.dxy.homework.entity.User;
import com.dxy.homework.mapper.AppFileMapper;
import com.dxy.homework.mapper.HomeworkSubmitMapper;
import com.dxy.homework.mapper.StudentMapper;
import com.dxy.homework.mapper.SubmitImageMapper;
import com.dxy.homework.mapper.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 批改任务的落库部分，单独成 bean 是为了让事务边界清晰。
 *
 * <p>之所以不让调用方直接 @Transactional：提交任务需要在**事务提交之后**才能入队，
 * 否则异步线程可能读不到刚插入的记录。拆出来之后，外层方法在事务返回后再入队，天然安全。
 */
@Service
public class HomeworkCreationService {

    private final HomeworkSubmitMapper submitMapper;
    private final SubmitImageMapper submitImageMapper;
    private final AppFileMapper fileMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final QuotaService quotaService;

    public HomeworkCreationService(HomeworkSubmitMapper submitMapper,
                                   SubmitImageMapper submitImageMapper,
                                   AppFileMapper fileMapper,
                                   StudentMapper studentMapper,
                                   UserMapper userMapper,
                                   QuotaService quotaService) {
        this.submitMapper = submitMapper;
        this.submitImageMapper = submitImageMapper;
        this.fileMapper = fileMapper;
        this.studentMapper = studentMapper;
        this.userMapper = userMapper;
        this.quotaService = quotaService;
    }

    /**
     * 校验通过后落库并占用额度。整个过程在一个事务里：
     * 额度占用是同事务内的 UPDATE，一旦后续插入失败会随事务一起回滚，不会白扣用户次数。
     */
    @Transactional(rollbackFor = Exception.class)
    public HomeworkSubmit create(Long userId, SubmitCreateRequest request) {
        Student student = requireOwnedStudent(userId, request.studentId());
        List<AppFile> images = requireOwnedImages(userId, request.fileIds());

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized();
        }
        int dailyLimit = user.getQuotaDaily() == null ? 0 : user.getQuotaDaily();
        if (!quotaService.tryAcquire(userId, dailyLimit)) {
            throw new BizException(ErrorCode.QUOTA_EXCEEDED,
                    "今日 %d 次批改已用完，明天再来吧".formatted(dailyLimit));
        }

        HomeworkSubmit submit = new HomeworkSubmit();
        submit.setBizNo(request.bizNo());
        submit.setUserId(userId);
        submit.setStudentId(student.getId());
        submit.setSubject(normalizeSubject(request.subject()));
        submit.setGrade(request.grade() == null ? student.getGrade() : request.grade());
        submit.setStatus(HomeworkSubmit.STATUS_PENDING);
        submit.setImageCount(images.size());
        submit.setRetryCount(0);
        submitMapper.insert(submit);

        int seq = 0;
        for (AppFile image : images) {
            seq++;
            SubmitImage link = new SubmitImage();
            link.setSubmitId(submit.getId());
            link.setSeq(seq);
            link.setFileId(image.getId());
            submitImageMapper.insert(link);
        }
        return submit;
    }

    private Student requireOwnedStudent(Long userId, Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw BizException.notFound("学生");
        }
        if (!student.getUserId().equals(userId)) {
            throw BizException.forbidden();
        }
        return student;
    }

    /** 逐个校验图片归属与类型。只要有一张不是自己的就直接拒绝，不给"部分可用"的余地。 */
    private List<AppFile> requireOwnedImages(Long userId, List<Long> fileIds) {
        List<AppFile> images = new java.util.ArrayList<>(fileIds.size());
        for (Long fileId : fileIds) {
            AppFile file = fileMapper.selectById(fileId);
            if (file == null || !Integer.valueOf(1).equals(file.getStatus())) {
                throw BizException.notFound("文件");
            }
            if (!file.getUserId().equals(userId)) {
                throw BizException.forbidden();
            }
            if (!Integer.valueOf(AppFile.BIZ_HOMEWORK_IMAGE).equals(file.getBizType())) {
                throw BizException.badRequest("只能提交作业原图");
            }
            images.add(file);
        }
        return images;
    }

    private String normalizeSubject(String subject) {
        String value = subject == null ? "" : subject.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.isBlank()) {
            throw BizException.badRequest("学科不能为空");
        }
        return value.length() > 16 ? value.substring(0, 16) : value;
    }

    /** 供外层做幂等判断使用。 */
    public HomeworkSubmit findByBizNo(String bizNo) {
        return submitMapper.selectOne(Wrappers.<HomeworkSubmit>lambdaQuery()
                .eq(HomeworkSubmit::getBizNo, bizNo)
                .last("LIMIT 1"));
    }
}
