package com.pethealth.service;

import com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException;
import com.pethealth.entity.Like;
import com.pethealth.entity.Post;
import com.pethealth.repository.LikeRepository;
import com.pethealth.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final PostRepository postRepository;
    private final PostRankService postRankService;

    /**
     * 点赞（防重复，幂等：已点赞则直接返回）
     */
    public Optional<Like> like(String userId, String targetType, String targetId) {
        if (likeRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)) {
            return likeRepository.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
        }

        Like like = Like.builder()
                .userId(userId).targetType(targetType).targetId(targetId)
                .createdAt(LocalDateTime.now())
                .build();
        Like saved = likeRepository.save(like);

        updateTargetCount(targetType, targetId, +1);
        log.info("点赞: userId={}, {}:{}", userId, targetType, targetId);
        return Optional.of(saved);
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
            Post post = postRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("帖子不存在: " + targetId));
            int cnt = post.getLikeCount() != null ? post.getLikeCount() : 0;
            post.setLikeCount(Math.max(0, cnt + delta));
            postRepository.save(post);
            postRankService.updateScore(targetId);
        }
    }
}
