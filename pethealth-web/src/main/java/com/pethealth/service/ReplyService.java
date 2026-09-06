package com.pethealth.service;

import com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException;
import com.pethealth.entity.Post;
import com.pethealth.entity.Reply;
import com.pethealth.repository.PostRepository;
import com.pethealth.repository.ReplyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public Reply create(Reply reply) {
        Post post = postRepository.findById(reply.getPostId())
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在: " + reply.getPostId()));

        LocalDateTime now = LocalDateTime.now();
        reply.setLikeCount(0);
        reply.setIsAccepted(false);
        reply.setCreatedAt(now);
        reply.setUpdatedAt(now);

        Reply saved = replyRepository.save(reply);

        post.setReplyCount(post.getReplyCount() != null ? post.getReplyCount() + 1 : 1);
        postRepository.save(post);
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

    public void delete(String id) {
        Reply reply = replyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("回复不存在: " + id));
        replyRepository.deleteById(id);

        Post post = postRepository.findById(reply.getPostId()).orElse(null);
        if (post != null) {
            int cnt = post.getReplyCount() != null ? post.getReplyCount() : 0;
            post.setReplyCount(Math.max(0, cnt - 1));
            postRepository.save(post);
            postRankService.updateScore(reply.getPostId());
        }
        log.info("回复已删除: {}", id);
    }

    public Reply acceptBest(String id) {
        Reply reply = replyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("回复不存在: " + id));
        reply.setIsAccepted(true);
        reply.setUpdatedAt(LocalDateTime.now());
        Reply saved = replyRepository.save(reply);

        // 站内信：采纳回复 → 通知回复作者
        Post post = postRepository.findById(reply.getPostId()).orElse(null);
        String postTitle = post != null ? post.getTitle() : "你的回复";
        notificationService.create(
                reply.getAuthorId(),
                "ACCEPT",
                "回复被采纳",
                "你在《" + postTitle + "》下的回复被作者采纳为最佳答案 🎯",
                reply.getPostId());

        return saved;
    }
}
