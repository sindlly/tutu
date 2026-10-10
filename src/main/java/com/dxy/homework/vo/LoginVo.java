package com.dxy.homework.vo;

/** 登录结果。token 由客户端存放并在后续请求的 Authorization 头里带上。 */
public record LoginVo(
        String token,
        long expiresInSeconds,
        boolean newUser,
        UserVo user,
        StudentVo student
) {
}
