package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.repository.HealthRecordRepository;
import com.pethealth.repository.LikeRepository;
import com.pethealth.repository.PetProfileRepository;
import com.pethealth.repository.PostRepository;
import com.pethealth.repository.ReplyRepository;
import com.pethealth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final PetProfileRepository petProfileRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HOME_CACHE_KEY = "pethealth:statistics:home";
    private static final long   HOME_CACHE_TTL = 1; // 分钟

    /**
     * GET /api/statistics/home — 首页聚合统计（Redis 缓存 1 分钟）
     */
    @GetMapping("/home")
    public ApiResponse<Map<String, Object>> home() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(HOME_CACHE_KEY);
            if (cached != null) {
                log.debug("命中首页统计缓存");
                return ApiResponse.success(cached);
            }
        } catch (Exception e) {
            log.warn("Redis 读取失败，降级查 MongoDB: {}", e.getMessage());
        }

        Map<String, Object> stats = computeHomeStats();

        try {
            redisTemplate.opsForValue().set(HOME_CACHE_KEY, stats, HOME_CACHE_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 写入失败（非关键）");
        }

        return ApiResponse.success(stats);
    }

    /**
     * GET /api/statistics/dashboard — Dashboard（实时，不缓存）
     */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> stats = computeHomeStats();
        stats.put("notes", "Phase 3 已接入 Redis 统计缓存 + 热门榜 + 健康趋势缓存");
        return ApiResponse.success(stats);
    }

    private Map<String, Object> computeHomeStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalPosts", postRepository.count());
        stats.put("totalReplies", replyRepository.count());
        stats.put("totalUsers", userRepository.count());
        stats.put("totalLikes", likeRepository.count());
        stats.put("totalPets", petProfileRepository.count());
        stats.put("totalHealthRecords", healthRecordRepository.count());
        return stats;
    }
}
