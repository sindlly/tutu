package com.dxy.homework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 跨域配置。
 *
 * <p>小程序端不受同源策略限制，本来不需要这个 —— 之所以留着，是因为 H5 端（本地调试、
 * 以及将来可能的网页版）必须跨域访问后端。
 *
 * <p>**默认关闭**：留空即不注册任何 CORS 映射。只有显式配了来源才生效，
 * 避免生产环境被误开成一个"谁都能调"的接口。
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        /** 允许的来源，逗号分隔，例如 http://127.0.0.1:10086 */
        @DefaultValue("") String allowedOrigins
) {

    public boolean enabled() {
        return allowedOrigins != null && !allowedOrigins.isBlank();
    }

    public String[] origins() {
        return allowedOrigins.split(",");
    }
}
