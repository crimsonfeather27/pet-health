package com.pethealth.service;

import com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException;
import com.pethealth.entity.Post;
import com.pethealth.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostRankService postRankService;

    /**
     * 发帖（初始化计数器为 0，状态 published）
     */
    public Post create(Post post) {
        LocalDateTime now = LocalDateTime.now();
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setReplyCount(0);
        post.setStatus("published");
        post.setCreatedAt(now);
        post.setUpdatedAt(now);

        Post saved = postRepository.save(post);
        postRankService.updateScore(saved.getId());
        log.info("新帖创建: {}", saved.getId());
        return saved;
    }

    /**
     * 更新帖子（只有 title/content/category/tags/petSpecies 可变）
     */
    public Post update(String id, Post updates) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在: " + id));

        if (updates.getTitle() != null) post.setTitle(updates.getTitle());
        if (updates.getContent() != null) post.setContent(updates.getContent());
        if (updates.getCategory() != null) post.setCategory(updates.getCategory());
        if (updates.getTags() != null) post.setTags(updates.getTags());
        if (updates.getPetSpecies() != null) post.setPetSpecies(updates.getPetSpecies());
        post.setUpdatedAt(LocalDateTime.now());

        return postRepository.save(post);
    }

    public void delete(String id) {
        if (!postRepository.existsById(id)) {
            throw new ResourceNotFoundException("帖子不存在: " + id);
        }
        postRepository.deleteById(id);
        postRankService.removePost(id);
        log.info("帖子已删除: {}", id);
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
     */
    public Post incrementViewCount(String id) {
        Post post = findById(id);
        post.setViewCount(post.getViewCount() != null ? post.getViewCount() + 1 : 1);
        Post saved = postRepository.save(post);
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
