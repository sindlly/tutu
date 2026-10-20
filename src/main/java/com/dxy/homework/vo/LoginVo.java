package com.dxy.homework.vo;

import java.util.List;

/**
 * 登录结果。token 由客户端存放并在后续请求的 Authorization 头里带上。
 *
 * @param students 当前用户可访问的全部孩子。多于 1 个时，客户端应先让孩子选择
 * @param student  默认孩子（第一个），向后兼容保留；单孩用户直接用它
 */
public record LoginVo(
        String token,
        long expiresInSeconds,
        boolean newUser,
        UserVo user,
        StudentVo student,
        List<StudentVo> students
) {
}
