package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.Like;
import com.pethealth.interceptor.AuthInterceptor;
import com.pethealth.service.LikeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;

    /**
     * POST /api/likes — 点赞（幂等）
     * 登录态从 AuthInterceptor 注入的 request attribute 获取（Redis Token 机制）
     */
    @PostMapping
    public ApiResponse<Like> like(@RequestBody LikeRequest req, HttpServletRequest request) {
        String userId = (String) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
        if (userId == null) return ApiResponse.error(401, "请先登录");
        Optional<Like> liked = likeService.like(userId, req.getTargetType(), req.getTargetId());
        return liked.map(ApiResponse::success)
                .orElse(ApiResponse.error(500, "点赞失败"));
    }

    /**
     * DELETE /api/likes/{targetType}/{targetId} — 取消点赞（幂等）
     */
    @DeleteMapping("/{targetType}/{targetId}")
    public ApiResponse<Void> unlike(@PathVariable String targetType,
                                    @PathVariable String targetId,
                                    HttpServletRequest request) {
        String userId = (String) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
        if (userId == null) return ApiResponse.error(401, "请先登录");
        likeService.unlike(userId, targetType, targetId);
        return ApiResponse.success("取消成功", null);
    }

    /**
     * GET /api/likes/check?targetType=POST&targetId=xxx — 检查是否已点赞
     */
    @GetMapping("/check")
    public ApiResponse<Boolean> check(@RequestParam String targetType,
                                       @RequestParam String targetId,
                                       HttpServletRequest request) {
        String userId = (String) request.getAttribute(AuthInterceptor.CURRENT_USER_ID);
        if (userId == null) return ApiResponse.success(false);
        return ApiResponse.success(likeService.isLiked(userId, targetType, targetId));
    }

    @Data
    public static class LikeRequest {
        private String targetType;
        private String targetId;
    }
}
