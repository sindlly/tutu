package com.dxy.homework.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dxy.homework.common.BizException;
import com.dxy.homework.entity.Student;
import com.dxy.homework.entity.UserStudent;
import com.dxy.homework.mapper.StudentMapper;
import com.dxy.homework.mapper.UserStudentMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 「用户对孩子」的访问模型。
 *
 * <p>数据主权在孩子身上：错题、批改记录挂在 student_id 下，
 * 只要对这个孩子有访问权（创建者或家人绑定），就能看到同一份数据 ——
 * 妈妈绑定了爸爸添加的孩子后，看到的是孩子名下的全部错题，而不只是自己提交的。
 *
 * <p>关系统一存在 t_user_student（创建者也会补一行 relation=1），
 * 所以「我能访问哪些孩子」只查这一张表，不用再 union 归属人。
 */
@Service
public class StudentAccessService {

    /** 创建者 */
    public static final int RELATION_OWNER = 1;
    /** 家人绑定 */
    public static final int RELATION_FAMILY = 2;

    private final StudentMapper studentMapper;
    private final UserStudentMapper userStudentMapper;

    public StudentAccessService(StudentMapper studentMapper, UserStudentMapper userStudentMapper) {
        this.studentMapper = studentMapper;
        this.userStudentMapper = userStudentMapper;
    }

    /** 当前用户能访问的全部孩子 id。绝大多数列表查询的越权防线就是它。 */
    public List<Long> accessibleStudentIds(Long userId) {
        return userStudentMapper.selectList(Wrappers.<UserStudent>lambdaQuery()
                        .select(UserStudent::getStudentId)
                        .eq(UserStudent::getUserId, userId))
                .stream().map(UserStudent::getStudentId).distinct().toList();
    }

    /** 孩子存在且当前用户有访问权，否则抛异常。 */
    public Student requireAccess(Long userId, Long studentId) {
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw BizException.notFound("孩子");
        }
        Long bound = userStudentMapper.selectCount(Wrappers.<UserStudent>lambdaQuery()
                .eq(UserStudent::getUserId, userId)
                .eq(UserStudent::getStudentId, studentId));
        if (bound == null || bound == 0) {
            throw BizException.forbidden();
        }
        return student;
    }

    public boolean isOwner(Long userId, Long studentId) {
        Student student = studentMapper.selectById(studentId);
        return student != null && student.getUserId() != null && student.getUserId().equals(userId);
    }

    /** 建立关系。已存在则幂等返回，不重复插入。 */
    public void bind(Long userId, Long studentId, int relation) {
        Long existing = userStudentMapper.selectCount(Wrappers.<UserStudent>lambdaQuery()
                .eq(UserStudent::getUserId, userId)
                .eq(UserStudent::getStudentId, studentId));
        if (existing != null && existing > 0) {
            return;
        }
        UserStudent us = new UserStudent();
        us.setUserId(userId);
        us.setStudentId(studentId);
        us.setRelation(relation);
        userStudentMapper.insert(us);
    }

    /** 家人解绑。创建者不能解绑自己 —— 孩子档案归创建者所有，要移除走删除（暂未提供）。 */
    public void unbind(Long userId, Long studentId) {
        if (isOwner(userId, studentId)) {
            throw BizException.badRequest("孩子的创建者不能解绑，只能由绑定的家人自己移除");
        }
        userStudentMapper.delete(Wrappers.<UserStudent>lambdaQuery()
                .eq(UserStudent::getUserId, userId)
                .eq(UserStudent::getStudentId, studentId));
    }
}
