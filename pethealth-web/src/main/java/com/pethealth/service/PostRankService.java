package com.pethealth.service;

import com.pethealth.entity.Post;
import com.pethealth.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 帖子热门排行榜服务
 * <p>
 * 核心设计：
 * 1. 热度分数 = 点赞×3 + 回复×5 + 浏览×1
 * 2. Redis Sorted Set 存帖子ID和分数，实时更新
 * 3. 排行榜查询结果再缓存 1 分钟（避免频繁查数据库）
 * 4. 每 5 分钟全量同步一次，防止实时更新有遗漏
 * <p>
 * Redis 降级（#26）：排行榜属于"锦上添花"数据，Redis 不可用时
 * - 读路径：回源数据库，按热度分数内存排序返回（牺牲一点性能换可用性）
 * - 写路径：静默跳过热度更新，保证点赞/浏览/发帖主流程不受影响，
 *   待 Redis 恢复后由全量同步任务重建排行榜
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostRankService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PostRepository postRepository;

    private static final String RANK_KEY        = "pethealth:post:rank";
    private static final String RANK_CACHE_KEY  = "pethealth:post:rank:cache";
    private static final String STATS_CACHE_KEY = "pethealth:statistics:home";
    /** DB 降级时的候选集大小（取最近发布的帖子做内存排序） */
    private static final int FALLBACK_CANDIDATES = 200;
    private static final long CACHE_TTL_MIN   = 1;

    /**
     * 更新单个帖子的热度分数（点赞/回复/浏览变化时调用）
     * Redis 不可用时静默跳过（#26），等待全量同步重建
     */
    public void updateScore(String postId) {
        try {
            Optional<Post> opt = postRepository.findById(postId);
            if (opt.isEmpty()) return;

            Post post = opt.get();
            double score = calculateHotScore(post);

            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
            zSetOps.add(RANK_KEY, postId, score);

            redisTemplate.delete(RANK_CACHE_KEY);
            redisTemplate.delete(STATS_CACHE_KEY);
            log.debug("更新帖子 {} 热度为 {}", postId, score);
        } catch (Exception e) {
            log.warn("排行榜热度更新失败（非关键，等待全量同步）: postId={}, err={}", postId, e.getMessage());
        }
    }

    /**
     * 获取 Top N 热门帖子（先查缓存，缓存 miss 再查 Sorted Set + 数据库）
     * Redis 不可用时降级回源数据库（#26）
     */
    @SuppressWarnings("unchecked")
    public List<Post> getTopPosts(int limit) {
        try {
            Object cached = redisTemplate.opsForValue().get(RANK_CACHE_KEY);
            if (cached instanceof List) {
                log.debug("从缓存获取热门排行榜");
                return (List<Post>) cached;
            }

            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
            Set<Object> postIds = zSetOps.reverseRange(RANK_KEY, 0, limit - 1);

            List<Post> result = new ArrayList<>();
            if (postIds != null) {
                List<String> idList = postIds.stream().map(Object::toString).collect(Collectors.toList());
                Map<String, Post> map = new HashMap<>();
                postRepository.findAllById(idList).forEach(p -> map.put(p.getId(), p));
                for (String id : idList) {
                    if (map.containsKey(id)) result.add(map.get(id));
                }
            }

            redisTemplate.opsForValue().set(RANK_CACHE_KEY, result, CACHE_TTL_MIN, TimeUnit.MINUTES);
            return result;
        } catch (Exception e) {
            log.warn("排行榜 Redis 不可用，降级数据库查询: {}", e.getMessage());
            return topPostsFromDb(limit);
        }
    }

    /**
     * 数据库降级路径：取最近发布的候选帖做内存热度排序
     */
    private List<Post> topPostsFromDb(int limit) {
        return postRepository
                .findByStatusOrderByCreatedAtDesc("published", PageRequest.of(0, FALLBACK_CANDIDATES))
                .getContent()
                .stream()
                .sorted(Comparator.comparingDouble(this::calculateHotScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 定时全量同步（每 5 分钟）；Redis 不可用时仅告警，不影响其他任务
     */
    @Scheduled(fixedRate = 300_000)
    public void syncFromDatabase() {
        try {
            log.info("开始全量同步热门排行榜...");
            ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
            redisTemplate.delete(RANK_KEY);

            postRepository.findAll().forEach(p -> {
                double score = calculateHotScore(p);
                zSetOps.add(RANK_KEY, p.getId(), score);
            });

            redisTemplate.delete(RANK_CACHE_KEY);
            log.info("热门排行榜同步完成，共 {} 条", zSetOps.size(RANK_KEY));
        } catch (Exception e) {
            log.warn("排行榜全量同步失败（Redis 不可用？）: {}", e.getMessage());
        }
    }

    private double calculateHotScore(Post p) {
        int view  = p.getViewCount()  != null ? p.getViewCount()  : 0;
        int like  = p.getLikeCount()  != null ? p.getLikeCount()  : 0;
        int reply = p.getReplyCount() != null ? p.getReplyCount() : 0;
        return like * 3.0 + reply * 5.0 + view * 1.0;
    }

    /**
     * 帖子被删除时从排行榜移除
     */
    public void removePost(String postId) {
        try {
            redisTemplate.opsForZSet().remove(RANK_KEY, postId);
            redisTemplate.delete(RANK_CACHE_KEY);
            redisTemplate.delete(STATS_CACHE_KEY);
        } catch (Exception e) {
            log.warn("排行榜移除帖子失败（非关键）: postId={}, err={}", postId, e.getMessage());
        }
    }
}
