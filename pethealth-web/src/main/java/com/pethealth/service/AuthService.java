package com.pethealth.service;

import com.pethealth.entity.User;
import com.pethealth.exception.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 基于 Redis Token 的登录会话管理
 * <p>
 * Key 设计：pethealth:auth:token:{token} → Hash{userId, username, loginAt}
 * TTL：7 天
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_KEY_PREFIX = "pethealth:auth:token:";
    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 登录成功后创建 Token 并写入 Redis
     */
    public String createToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");

        Map<String, String> session = new HashMap<>();
        session.put("userId", user.getId());
        session.put("username", user.getUsername());
        session.put("loginAt", String.valueOf(System.currentTimeMillis()));

        try {
            redisTemplate.opsForHash().putAll(TOKEN_KEY_PREFIX + token, session);
            redisTemplate.expire(TOKEN_KEY_PREFIX + token, TOKEN_TTL);
            log.info("为用户 {} 创建登录 Token", user.getUsername());
        } catch (Exception e) {
            // #23：会话写不进 Redis 等于没登录成功——绝不能把"假 token"发给前端
            // （否则用户以为已登录，后续所有写接口全部 401，体验割裂且难排查）
            log.error("Redis 写入 Token 失败，拒绝登录: {}", e.getMessage());
            throw new ServiceUnavailableException("登录服务暂不可用，请稍后再试");
        }
        return token;
    }

    /**
     * 根据 Token 获取 userId（Redis 不可用时返回空，由拦截器降级为匿名）
     */
    public Optional<String> getUserIdByToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Object userId = redisTemplate.opsForHash().get(TOKEN_KEY_PREFIX + token, "userId");
            if (userId == null) return Optional.empty();
            // 续期（活跃用户保持登录）
            redisTemplate.expire(TOKEN_KEY_PREFIX + token, TOKEN_TTL);
            return Optional.of(userId.toString());
        } catch (Exception e) {
            log.warn("Redis 读取 Token 失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 登出，删除 Token
     */
    public boolean invalidate(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Boolean deleted = redisTemplate.delete(TOKEN_KEY_PREFIX + token);
            return Boolean.TRUE.equals(deleted);
        } catch (Exception e) {
            log.warn("Redis 删除 Token 失败: {}", e.getMessage());
            return false;
        }
    }
}
