package com.pethealth.service;

import com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException;
import com.pethealth.entity.Like;
import com.pethealth.entity.Post;
import com.pethealth.repository.LikeRepository;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRankService postRankService;
    private final MongoTemplate mongoTemplate;

    /**
     * 点赞（防重复，幂等：已点赞则直接返回）
     * <p>
     * #36 幂等兜底：exists 检查与 insert 之间存在并发窗口，
     * 双方同时插入时靠唯一索引拦截，DuplicateKey 视为"已点赞"成功返回且不重复计数
     */
    public Optional<Like> like(String userId, String targetType, String targetId) {
        if (likeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)) {
            return likeRepository.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
        }

        Like like = Like.builder()
                .userId(userId).targetType(targetType).targetId(targetId)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            Like saved = likeRepository.save(like);
            updateTargetCount(targetType, targetId, +1);
            log.info("点赞: userId={}, {}:{}", userId, targetType, targetId);
            return Optional.of(saved);
        } catch (DuplicateKeyException e) {
            log.info("并发重复点赞被唯一索引拦截: userId={}, {}:{}", userId, targetType, targetId);
            return likeRepository.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
        }
    }

    /**
     * 取消点赞（幂等：没点赞也不报错）
     */
    public void unlike(String userId, String targetType, String targetId) {
        Optional<Like> opt = likeRepository.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
        if (opt.isEmpty()) return;

        likeRepository.delete(opt.get());
        updateTargetCount(targetType, targetId, -1);
        log.info("取消点赞: userId={}, {}:{}", userId, targetType, targetId);
    }

    public boolean isLiked(String userId, String targetType, String targetId) {
        return likeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
    }

    private void updateTargetCount(String targetType, String targetId, int delta) {
        // 前端统一传 "POST"，种子数据为 "post" —— 不区分大小写，避免计数漏更
        if ("post".equalsIgnoreCase(targetType)) {
            // #36：Mongo $inc 原子加减，替代"读-改-写"，并发点赞/取消不丢计数
            UpdateResult result = mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(targetId)),
                    new Update().inc("likeCount", delta),
                    Post.class);
            if (result.getMatchedCount() == 0) {
                throw new ResourceNotFoundException("帖子不存在: " + targetId);
            }
            postRankService.updateScore(targetId);
        }
    }
}
