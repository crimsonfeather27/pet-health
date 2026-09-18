package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.Notification;
import com.pethealth.interceptor.AuthContext;
import com.pethealth.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/notifications — 当前登录用户的站内信列表（按时间倒序）。
     * ownerId 一律取登录态，忽略客户端参数。
     */
    @GetMapping
    public ApiResponse<List<Notification>> list(HttpServletRequest request) {
        return ApiResponse.success(notificationService.list(AuthContext.requireUserId(request)));
    }

    /**
     * GET /api/notifications/unread-count — 当前用户未读数
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(HttpServletRequest request) {
        return ApiResponse.success(notificationService.unreadCount(AuthContext.requireUserId(request)));
    }

    /**
     * PUT /api/notifications/{id}/read — 标记单条已读（仅接收者本人）
     */
    @PutMapping("/{id}/read")
    public ApiResponse<Notification> markRead(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.success(notificationService.markRead(id, AuthContext.requireUserId(request)));
    }

    /**
     * PUT /api/notifications/read-all — 当前用户全部标记已读
     */
    @PutMapping("/read-all")
    public ApiResponse<Long> markAllRead(HttpServletRequest request) {
        String userId = AuthContext.requireUserId(request);
        return ApiResponse.success("已全部标记为已读",
                notificationService.markAllRead(userId, userId));
    }

    /**
     * DELETE /api/notifications/{id} — 删除站内信（仅接收者本人）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest request) {
        notificationService.delete(id, AuthContext.requireUserId(request));
        return ApiResponse.success("删除成功", null);
    }
}
