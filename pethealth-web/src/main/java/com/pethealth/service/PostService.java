package com.pethealth.service;

import com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException;
import com.pethealth.entity.Post;
import com.pethealth.entity.User;
import com.pethealth.exception.AccessDeniedException;
import com.pethealth.repository.PostRepository;
import com.pethealth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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
public class PostService {

    private final PostRepository postRepository;
    private final PostRankService postRankService;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * 发帖（初始化计数器为 0，状态 published；authorName 以服务端登录用户为准）
     */
    public Post create(Post post) {
        LocalDateTime now = LocalDateTime.now();
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setReplyCount(0);
        post.setStatus("published");
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        if (post.getAuthorId() != null) {
            userRepository.findById(post.getAuthorId())
                    .ifPresent(u -> post.setAuthorName(u.getUsername()));
        }

        Post saved = postRepository.save(post);
        postRankService.updateScore(saved.getId());
        log.info("新帖创建: {}", saved.getId());
        return saved;
    }

    /**
     * 更新帖子（只有 title/content/category/tags/petSpecies 可变；仅帖主可操作）
     */
    public Post update(String id, Post updates, String currentUserId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在: " + id));
        checkOwner(currentUserId, post.getAuthorId());

        if (updates.getTitle() != null) post.setTitle(updates.getTitle());
        if (updates.getContent() != null) post.setContent(updates.getContent());
        if (updates.getCategory() != null) post.setCategory(updates.getCategory());
        if (updates.getTags() != null) post.setTags(updates.getTags());
        if (updates.getPetSpecies() != null) post.setPetSpecies(updates.getPetSpecies());
        post.setUpdatedAt(LocalDateTime.now());

        return postRepository.save(post);
    }

    public void delete(String id, String currentUserId) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在: " + id));
        checkOwner(currentUserId, post.getAuthorId());
        postRepository.deleteById(id);
        postRankService.removePost(id);
        log.info("帖子已删除: {}", id);
    }

    /** 属主校验：非帖主操作他人帖子视为水平越权 */
    private void checkOwner(String currentUserId, String authorId) {
        if (currentUserId == null || !currentUserId.equals(authorId)) {
            throw new AccessDeniedException("无权操作他人帖子");
        }
    }

    public Post findById(String id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在: " + id));
    }

    /**
     * 帖子列表（支持 category 筛选，默认按创建时间倒序）
     */
    public Page<Post> list(String category, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (category != null && !category.isBlank()) {
            return postRepository.findByCategoryOrderByCreatedAtDesc(category, pageable);
        }
        return postRepository.findByStatusOrderByCreatedAtDesc("published", pageable);
    }

    public List<Post> findByAuthor(String authorId) {
        return postRepository.findByAuthorId(authorId);
    }

    /**
     * 浏览 +1（读取详情时调用）
     * #36：Mongo $inc 原子自增，替代"读-改-写"，并发浏览不丢计数
     */
    public Post incrementViewCount(String id) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().inc("viewCount", 1);
        Post saved = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Post.class);
        if (saved == null) {
            throw new ResourceNotFoundException("帖子不存在: " + id);
        }
        postRankService.updateScore(id);
        return saved;
    }

    public List<Post> getHotPosts(int limit) {
        return postRankService.getTopPosts(limit);
    }

    public long count() {
        return postRepository.count();
    }
}
