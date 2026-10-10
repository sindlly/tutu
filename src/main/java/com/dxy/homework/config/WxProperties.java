package com.dxy.homework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 微信小程序配置。 */
@ConfigurationProperties(prefix = "wx.miniapp")
public record WxProperties(
        @DefaultValue("") String appid,
        @DefaultValue("") String secret,
        /**
         * 是否允许用 devIdentity 直接登录。
         *
         * <p>刻意做成独立开关而不是"没配 AppID 才允许"：那样一配好 AppID 就没法本地调试了。
         * 默认关闭，开启时启动日志会告警；请求还必须显式带 devIdentity 才会走这条路径，
         * 所以真实用户的正常请求不可能被误当成开发登录。
         */
        @DefaultValue("false") boolean devLoginEnabled
) {

    public boolean configured() {
        return appid != null && !appid.isBlank() && secret != null && !secret.isBlank();
    }
}
