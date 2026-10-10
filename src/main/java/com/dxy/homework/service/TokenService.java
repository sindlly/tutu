package com.dxy.homework.service;

import com.dxy.homework.config.AuthProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 登录态管理：随机不透明 token → userId 的映射存在 Redis。
 *
 * <p>相比自签 JWT，这里的好处是**可以随时吊销**（退出登录、封号、token 泄露），
 * 代价是每次请求多一次 Redis 查询 —— 对小程序场景完全可接受。
 */
@Service
public class TokenService {

    private static final String KEY_PREFIX = "auth:token:";
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public TokenService(StringRedisTemplate redis, AuthProperties authProperties) {
        this.redis = redis;
        this.ttl = authProperties.tokenTtl();
    }

    public String issue(Long userId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(KEY_PREFIX + token, String.valueOf(userId), ttl);
        return token;
    }

    /** 返回 null 表示 token 不存在或已过期。 */
    public Long resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = redis.opsForValue().get(KEY_PREFIX + token);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(KEY_PREFIX + token);
        }
    }

    public Duration ttl() {
        return ttl;
    }
}
