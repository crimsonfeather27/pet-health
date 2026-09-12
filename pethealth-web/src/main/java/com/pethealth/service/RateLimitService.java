package com.pethealth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis 固定窗口的简易限流器（#40）。
 * <p>
 * 使用 StringRedisTemplate（String 序列化器）执行 INCR + EXPIRE：
 * 同 key 首次写入时设过期时间（窗口起点），后续 INCR 不累加 TTL，
 * 从而形成自然的固定窗口；窗口结束后 key 自动过期，计数归零。
 * <p>
 * 注意：不能用业务 RedisTemplate（Jackson 值序列化器），
 * 其反序列化 INCR 返回的原始整数会抛异常。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private static final String KEY_PREFIX = "pethealth:ratelimit:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取一次调用额度。
     *
     * @param key           限流维度标识（如 "ai-diagnosis:{userId}"）
     * @param limit         窗口内最大调用次数
     * @param windowSeconds 窗口长度（秒）
     * @return true=允许调用，false=已超限
     */
    public boolean tryAcquire(String key, int limit, long windowSeconds) {
        String redisKey = KEY_PREFIX + key;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count == null) return true;
            if (count == 1) {
                // 首次写入，设置窗口过期时间
                stringRedisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }
            return count <= limit;
        } catch (Exception e) {
            // Redis 不可用时降级放行，避免影响主流程
            log.warn("限流检查失败，降级放行: key={}, err={}", key, e.getMessage());
            return true;
        }
    }

    /**
     * 返回当前窗口内已用次数（用于响应头展示，可选）。
     */
    public long currentCount(String key) {
        try {
            String v = stringRedisTemplate.opsForValue().get(KEY_PREFIX + key);
            return v != null ? Long.parseLong(v) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
