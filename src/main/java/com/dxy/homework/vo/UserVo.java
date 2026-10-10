package com.dxy.homework.vo;

import com.dxy.homework.entity.User;

/** 对外暴露的用户信息。绝不包含 openId 等第三方标识。 */
public record UserVo(
        Long id,
        String nickname,
        String avatarUrl,
        Integer quotaDaily
) {

    public static UserVo from(User user) {
        return new UserVo(user.getId(), user.getNickname(), user.getAvatarUrl(), user.getQuotaDaily());
    }
}
