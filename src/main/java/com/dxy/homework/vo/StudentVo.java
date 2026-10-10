package com.dxy.homework.vo;

import com.dxy.homework.entity.Student;

public record StudentVo(
        Long id,
        String name,
        Integer grade,
        String school
) {

    public static StudentVo from(Student student) {
        return new StudentVo(student.getId(), student.getName(), student.getGrade(), student.getSchool());
    }
}
