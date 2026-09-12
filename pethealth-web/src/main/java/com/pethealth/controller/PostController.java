package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.Post;
import com.pethealth.interceptor.AuthContext;
import com.pethealth.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /**
     * POST /api/posts — 发帖（authorId/authorName 由服务端登录态注入，不信任客户端）
     */
    @PostMapping
    public ApiResponse<Post> create(@Valid @RequestBody Post post, HttpServletRequest request) {
        post.setAuthorId(AuthContext.requireUserId(request));
        Post saved = postService.create(post);
        return ApiResponse.success(saved);
    }

    /**
     * GET /api/posts — 帖子列表（支持 category / page 筛选）
     */
    @GetMapping
    public ApiResponse<Page<Post>> list(
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(postService.list(category, page, size));
    }

    /**
     * GET /api/posts/hot — 热门帖子 Top N
     */
    @GetMapping("/hot")
    public ApiResponse<List<Post>> hot(@RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.success(postService.getHotPosts(limit));
    }

    /**
     * GET /api/posts/author/{authorId} — 某用户的帖子
     */
    @GetMapping("/author/{authorId}")
    public ApiResponse<List<Post>> byAuthor(@PathVariable String authorId) {
        return ApiResponse.success(postService.findByAuthor(authorId));
    }

    /**
     * GET /api/posts/{id} — 帖子详情（浏览量 +1）
     */
    @GetMapping("/{id}")
    public ApiResponse<Post> get(@PathVariable String id) {
        return ApiResponse.success(postService.incrementViewCount(id));
    }

    /**
     * PUT /api/posts/{id} — 编辑帖子（仅帖主）
     */
    @PutMapping("/{id}")
    public ApiResponse<Post> update(@PathVariable String id, @Valid @RequestBody Post updates,
                                    HttpServletRequest request) {
        return ApiResponse.success(postService.update(id, updates, AuthContext.requireUserId(request)));
    }

    /**
     * DELETE /api/posts/{id} — 删除帖子（仅帖主）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest request) {
        postService.delete(id, AuthContext.requireUserId(request));
        return ApiResponse.success("删除成功", null);
    }
}
