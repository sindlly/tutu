package com.dxy.homework.controller;

import com.dxy.homework.common.AuthContext;
import com.dxy.homework.common.R;
import com.dxy.homework.dto.StudentBindRequest;
import com.dxy.homework.dto.StudentCreateRequest;
import com.dxy.homework.dto.StudentUpdateRequest;
import com.dxy.homework.service.StudentService;
import com.dxy.homework.vo.StudentVo;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 孩子档案：添加、绑定、管理。 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /** 我能访问的孩子（创建的 + 绑定的）。登录后多孩选择就靠这个列表。 */
    @GetMapping
    public R<List<StudentVo>> myStudents() {
        return R.ok(studentService.myStudents(AuthContext.requireUserId()));
    }

    /** 添加孩子。 */
    @PostMapping
    public R<StudentVo> create(@Valid @RequestBody StudentCreateRequest request) {
        return R.ok(studentService.create(AuthContext.requireUserId(), request));
    }

    /** 凭 6 位孩子编号绑定，家人共享同一份错题数据。 */
    @PostMapping("/bind")
    public R<StudentVo> bind(@Valid @RequestBody StudentBindRequest request) {
        return R.ok(studentService.bindByCode(AuthContext.requireUserId(), request));
    }

    /** 更新档案，仅创建者。 */
    @PutMapping("/{id}")
    public R<StudentVo> update(@PathVariable("id") Long id,
                               @Valid @RequestBody StudentUpdateRequest request) {
        return R.ok(studentService.update(AuthContext.requireUserId(), id, request));
    }

    /** 家人解绑自己（创建者不可）。 */
    @PostMapping("/{id}/unbind")
    public R<Void> unbind(@PathVariable("id") Long id) {
        studentService.unbind(AuthContext.requireUserId(), id);
        return R.ok(null);
    }
}
