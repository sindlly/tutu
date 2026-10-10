package com.dxy.homework.common;

/**
 * 当前登录用户上下文。
 *
 * <p>由 AuthInterceptor 在请求进入时写入、请求结束时清理。业务代码一律从这里取 userId，
 * 不要相信任何请求参数里的 userId —— 这是越权防护的第一道闸门。
 */
public final class AuthContext {

    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(Long userId) {
        CURRENT_USER.set(userId);
    }

    public static Long userId() {
        return CURRENT_USER.get();
    }

    /** 取当前用户，未登录直接抛 401。 */
    public static Long requireUserId() {
        Long userId = CURRENT_USER.get();
        if (userId == null) {
            throw BizException.unauthorized();
        }
        return userId;
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
