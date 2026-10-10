package com.dxy.homework.common;

import com.dxy.homework.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验拦截器：把 token 解析成 userId 放进 AuthContext。
 *
 * <p>业务代码一律通过 {@code AuthContext.requireUserId()} 取当前用户，
 * 页面传入的任何 userId 都不可信。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final TokenService tokenService;

    public AuthInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String token = extractToken(request);
        Long userId = tokenService.resolve(token);
        if (userId == null) {
            throw BizException.unauthorized();
        }
        AuthContext.set(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程复用，必须清理，否则会串用户
        AuthContext.clear();
    }

    public static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (StringUtils.hasText(header) && header.startsWith(PREFIX)) {
            return header.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
