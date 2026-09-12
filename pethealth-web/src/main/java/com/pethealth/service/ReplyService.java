package com.pethealth.service;

import com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException;
import com.pethealth.entity.Post;
import com.pethealth.entity.Reply;
import com.pethealth.entity.User;
import com.pethealth.exception.AccessDeniedException;
import com.pethealth.repository.PostRepository;
import com.pethealth.repository.ReplyRepository;
import com.pethealth.repository.UserRepository;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplyService {

    private final ReplyRepository replyRepository;
    private final PostRepository postRepository;
    private final PostRankService postRankService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public Reply create(Reply reply) {
        Post post = postRepository.findById(reply.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在: " + reply.getPostId()));

        LocalDateTime now = LocalDateTime.now();
        reply.setLikeCount(0);
        reply.setIsAccepted(false);
        reply.setCreatedAt(now);
        reply.setUpdatedAt(now);
        // authorName 以服务端登录用户为准（authorId 已由 Controller 从登录态注入）
        if (reply.getAuthorId() != null) {
            userRepository.findById(reply.getAuthorId())
                    .ifPresent(u -> reply.setAuthorName(u.getUsername()));
        }

        Reply saved = replyRepository.save(reply);

        // #36：$inc 原子自增回复数，替代"读-改-写"
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(post.getId())),
                new Update().inc("replyCount", 1),
                Post.class);
        postRankService.updateScore(reply.getPostId());

        // 站内信：回复帖子 → 通知帖子作者（自己回复自己的帖子不通知）
        if (post.getAuthorId() != null && !post.getAuthorId().equals(reply.getAuthorId())) {
            notificationService.create(
                    post.getAuthorId(),
                    "REPLY",
                    "收到新回复",
                    reply.getAuthorName() + " 回复了你的帖子《" + post.getTitle() + "》",
                    post.getId());
        }

        log.info("新回复创建: postId={}, replyId={}", reply.getPostId(), saved.getId());
        return saved;
    }

    public List<Reply> findByPostId(String postId) {
        return replyRepository.findByPostIdOrderByCreatedAtAsc(postId);
    }

    /**
     * 删除回复（仅回复作者本人）
     */
    public void delete(String id, String currentUserId) {
        Reply reply = replyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("回复不存在: " + id));
        if (currentUserId == null || !currentUserId.equals(reply.getAuthorId())) {
            throw new AccessDeniedException("无权删除他人回复");
        }
        replyRepository.deleteById(id);

        // #36：$inc 原子自减回复数；帖子已被删则静默跳过
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(reply.getPostId())),
                new Update().inc("replyCount", -1),
                Post.class);
        if (result.getMatchedCount() > 0) {
            postRankService.updateScore(reply.getPostId());
        }
        log.info("回复已删除: {}", id);
    }

    /**
     * 采纳最佳答案（仅帖主可采纳自己帖子下的回复）
     */
    public Reply acceptBest(String id, String currentUserId) {
        Reply reply = replyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("回复不存在: " + id));
        Post post = postRepository.findById(reply.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在: " + reply.getPostId()));
        if (currentUserId == null || !currentUserId.equals(post.getAuthorId())) {
            throw new AccessDeniedException("仅帖主可采纳最佳答案");
        }
        reply.setIsAccepted(true);
        reply.setUpdatedAt(LocalDateTime.now());
        Reply saved = replyRepository.save(reply);

        // 站内信：采纳回复 → 通知回复作者
        String postTitle = post.getTitle();
        notificationService.create(
                reply.getAuthorId(),
                "ACCEPT",
                "回复被采纳",
                "你在《" + postTitle + "》下的回复被作者采纳为最佳答案 🎯",
                reply.getPostId());

        return saved;
    }
}
