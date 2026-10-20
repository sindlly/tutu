package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.common.BizException;
import com.dxy.homework.dto.StudentBindRequest;
import com.dxy.homework.dto.StudentCreateRequest;
import com.dxy.homework.dto.StudentUpdateRequest;
import com.dxy.homework.entity.Student;
import com.dxy.homework.mapper.StudentMapper;
import com.dxy.homework.mapper.UserStudentMapper;
import com.dxy.homework.vo.StudentVo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 孩子档案：添加、绑定、更新、列表。
 *
 * <p>绑定用 6 位「孩子编号」而不是数据库 id —— 自增 id 可以被连续猜测，
 * 绑定接口一旦暴露等于把别人孩子的错题数据拱手送人。
 */
@Service
public class StudentService {

    private static final String BIND_SALT = "homework-grader-bind-2026";

    private final StudentMapper studentMapper;
    private final UserStudentMapper userStudentMapper;
    private final StudentAccessService accessService;

    public StudentService(StudentMapper studentMapper,
                          UserStudentMapper userStudentMapper,
                          StudentAccessService accessService) {
        this.studentMapper = studentMapper;
        this.userStudentMapper = userStudentMapper;
        this.accessService = accessService;
    }

    /** 我能访问的孩子：创建的 + 绑定的，按创建时间稳定排序。 */
    public List<StudentVo> myStudents(Long userId) {
        List<Long> ids = accessService.accessibleStudentIds(userId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return studentMapper.selectList(Wrappers.<Student>lambdaQuery()
                        .in(Student::getId, ids)
                        .orderByAsc(Student::getSortNo)
                        .orderByAsc(Student::getId))
                .stream()
                .map(s -> StudentVo.from(s, accessService.isOwner(userId, s.getId())))
                .sorted(Comparator.comparing(StudentVo::owner).reversed()
                        .thenComparing(StudentVo::id))
                .toList();
    }

    /** 添加孩子。最多 20 个，再多基本是绑定码输错成新建了。 */
    @Transactional(rollbackFor = Exception.class)
    public StudentVo create(Long userId, StudentCreateRequest request) {
        Long owned = studentMapper.selectCount(Wrappers.<Student>lambdaQuery()
                .eq(Student::getUserId, userId));
        if (owned != null && owned >= 20) {
            throw BizException.badRequest("孩子档案最多 20 个，够用了");
        }

        Student student = new Student();
        student.setUserId(userId);
        student.setName(sanitizeName(request.name()));
        student.setGrade(request.grade());
        student.setSchool(StringUtils.hasText(request.school()) ? request.school().trim() : null);
        student.setSortNo(0);
        studentMapper.insert(student);

        regenerateBindCode(student);
        accessService.bind(userId, student.getId(), StudentAccessService.RELATION_OWNER);
        return StudentVo.from(student, true);
    }

    /** 凭孩子编号绑定。家人（另一位家长、祖辈、家教）共享同一份错题数据。 */
    @Transactional(rollbackFor = Exception.class)
    public StudentVo bindByCode(Long userId, StudentBindRequest request) {
        String code = request.code() == null ? "" : request.code().trim().toUpperCase();
        if (code.length() != 6) {
            throw BizException.badRequest("孩子编号是 6 位");
        }
        Student student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery()
                .eq(Student::getBindCode, code)
                .last("LIMIT 1"));
        if (student == null) {
            throw BizException.notFound("孩子编号不存在，请核对后重输");
        }
        if (student.getUserId().equals(userId)) {
            throw BizException.badRequest("这是你自己创建的孩子，不用绑定");
        }
        accessService.bind(userId, student.getId(), StudentAccessService.RELATION_FAMILY);
        return StudentVo.from(student, false);
    }

    /** 更新档案。只允许创建者改，绑定的家人只能看。 */
    public StudentVo update(Long userId, Long studentId, StudentUpdateRequest request) {
        Student student = accessService.requireAccess(userId, studentId);
        if (!accessService.isOwner(userId, studentId)) {
            throw BizException.forbidden();
        }
        if (StringUtils.hasText(request.name())) {
            student.setName(sanitizeName(request.name()));
        }
        if (request.grade() != null) {
            student.setGrade(request.grade());
        }
        if (request.school() != null) {
            student.setSchool(request.school().isBlank() ? null : request.school().trim());
        }
        studentMapper.updateById(student);
        return StudentVo.from(student, true);
    }

    /** 家人解绑自己。 */
    public void unbind(Long userId, Long studentId) {
        accessService.requireAccess(userId, studentId);
        accessService.unbind(userId, studentId);
    }

    /** 生成孩子编号并落库。V2 之前的老档案缺编号时在登录时补。 */
    public String regenerateBindCode(Student student) {
        String code = md5Hex(student.getId() + BIND_SALT).substring(0, 6).toUpperCase();
        Long clash = studentMapper.selectCount(Wrappers.<Student>lambdaQuery()
                .eq(Student::getBindCode, code));
        // 冲突概率约 1/1600 万，真撞上了换个盐重试
        if (clash != null && clash > 0) {
            code = md5Hex(student.getId() + BIND_SALT + "-retry").substring(0, 6).toUpperCase();
        }
        student.setBindCode(code);
        studentMapper.updateById(student);
        return code;
    }

    private static String sanitizeName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isBlank()) {
            throw BizException.badRequest("称呼不能为空");
        }
        if (name.length() > 12) {
            throw BizException.badRequest("称呼最长 12 个字");
        }
        return name;
    }

    private static String md5Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("MD5 不可用", ex);
        }
    }
}
