package com.dxy.homework.config;

import com.dxy.homework.common.AuthInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final AuthInterceptor authInterceptor;
    private final CorsProperties corsProperties;

    public WebConfig(AuthInterceptor authInterceptor, CorsProperties corsProperties) {
        this.authInterceptor = authInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/wx-login",
                        "/api/v1/health",
                        // 预签名地址会直接用于 <image src>，浏览器不带 Authorization 头。
                        // 该接口内部会校验 key 已登记在 t_file，且仅在本地存储兜底时可用。
                        "/api/v1/files/raw");
    }

    /** 仅在显式配置了来源时才注册，生产默认不开。 */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (!corsProperties.enabled()) {
            return;
        }
        String[] origins = corsProperties.origins();
        log.info("已开启跨域，允许来源：{}", String.join(", ", origins));

        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 必须为 true：Taro 在 H5 端的 uploadFile 会带上 withCredentials，
                // 此时浏览器要求响应里必须有 Access-Control-Allow-Credentials: true，
                // 否则预检直接失败。我们鉴权走 Bearer 头而非 Cookie，
                // 且来源是显式白名单，因此放开凭证不构成额外风险。
                .allowCredentials(true)
                .maxAge(3600);
    }
}
