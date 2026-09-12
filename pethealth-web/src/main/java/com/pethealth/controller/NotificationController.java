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
     * GET /api/notifications?ownerId=xxx — 站内信列表（按时间倒序）
     */
    @GetMapping
    public ApiResponse<List<Notification>> list(@RequestParam String ownerId) {
        return ApiResponse.success(notificationService.list(ownerId));
    }

    /**
     * GET /api/notifications/unread-count?ownerId=xxx — 未读数
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@RequestParam String ownerId) {
        return ApiResponse.success(notificationService.unreadCount(ownerId));
    }

    /**
     * PUT /api/notifications/{id}/read — 标记单条已读（仅接收者本人）
     */
    @PutMapping("/{id}/read")
    public ApiResponse<Notification> markRead(@PathVariable String id, HttpServletRequest request) {
        return ApiResponse.success(notificationService.markRead(id, AuthContext.requireUserId(request)));
    }

    /**
     * PUT /api/notifications/read-all?ownerId=xxx — 全部标记已读（仅本人）
     */
    @PutMapping("/read-all")
    public ApiResponse<Long> markAllRead(@RequestParam String ownerId, HttpServletRequest request) {
        return ApiResponse.success("已全部标记为已读",
                notificationService.markAllRead(ownerId, AuthContext.requireUserId(request)));
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
