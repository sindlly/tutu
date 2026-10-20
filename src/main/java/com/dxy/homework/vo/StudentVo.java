package com.dxy.homework.vo;

import com.dxy.homework.entity.Student;

/**
 * 孩子档案。
 *
 * @param bindCode 孩子编号。分享给家人即可绑定同一孩子、共享错题数据
 * @param owner    当前用户是否创建者（能改档案），绑定的家人只读
 */
public record StudentVo(
        Long id,
        String name,
        Integer grade,
        String school,
        String bindCode,
        boolean owner
) {

    public static StudentVo from(Student student, boolean owner) {
        return new StudentVo(student.getId(), student.getName(), student.getGrade(),
                student.getSchool(), student.getBindCode(), owner);
    }
}
