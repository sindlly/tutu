package com.dxy.homework.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * DeepSeek 调用配置。
 * <p>apiKey 只来自环境变量 DEEPSEEK_API_KEY，禁止写入配置文件或代码。
 */
@ConfigurationProperties(prefix = "deepseek")
public record DeepSeekProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://api.deepseek.com") String baseUrl,
        /** 必须用 deepseek-flash：deepseek-v4-pro 不支持图像输入。 */
        @DefaultValue("deepseek-flash") String model,
        @DefaultValue("15s") Duration connectTimeout,
        @DefaultValue("180s") Duration readTimeout,
        @DefaultValue("2") int maxRetries,
        @DefaultValue("0.1") double temperature,
        Thinking thinking,
        @DefaultValue("medium") String reasoningEffort
) {

    public record Thinking(@DefaultValue("false") boolean enabled) {
    }

    public boolean thinkingEnabled() {
        return thinking != null && thinking.enabled();
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
