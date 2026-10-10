package com.dxy.homework.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 小程序登录请求。
 *
 * @param code        wx.login 拿到的临时凭证。正常登录必填
 * @param devIdentity 仅本地开发用：带了它就直接用该标识登录，不调用微信接口。
 *                    需要服务端开启 dev-login-enabled，且请求显式携带，真实用户不会触发
 * @param grade       首次注册时用于创建默认孩子，可选
 * @param studentName 首次注册时用于创建默认孩子，可选
 */
public record WxLoginRequest(
        @Size(max = 128, message = "凭证过长") String code,
        @Size(max = 24, message = "开发身份最长 24 字符") String devIdentity,
        @Min(value = 1, message = "年级范围 1-12") @Max(value = 12, message = "年级范围 1-12") Integer grade,
        @Size(max = 32, message = "称呼最长 32 字") String studentName
) {
}
