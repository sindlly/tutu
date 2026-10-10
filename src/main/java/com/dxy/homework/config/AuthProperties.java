package com.dxy.homework.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 登录态配置。token 是随机不透明串，映射关系存 Redis，可随时吊销。
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        @DefaultValue("30d") Duration tokenTtl,
        /** 开发模式下用这个假 openid 登录，生产必须关闭 dev-mode */
        @DefaultValue("dev-openid-local-001") String devOpenId
) {
}
