package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.Reminder;
import com.pethealth.exception.AccessDeniedException;
import com.pethealth.interceptor.AuthContext;
import com.pethealth.service.ReminderService;
import com.pethealth.service.dubbo.ReminderDubboService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    /**
     * Dubbo Consumer —— 直连 reminder-service（端口 20884）
     */
    @DubboReference(
            url = "dubbo://localhost:20884/com.pethealth.service.dubbo.ReminderDubboService",
            check = false,
            timeout = 5000
    )
    private ReminderDubboService reminderDubboService;

    @Value("${app.dubbo.enabled:true}")
    private boolean dubboEnabled;

    /**
     * GET /api/reminders?ownerId=xxx — 查询某用户的提醒
     * GET /api/reminders?petId=xxx — 查询某宠物的提醒（仅本地服务支持）
     */
    @GetMapping
    public ApiResponse<List<Reminder>> list(@RequestParam(required = false) String ownerId,
                                            @RequestParam(required = false) String petId) {
        if (petId != null) {
            // Dubbo 接口未提供 findByPet，仅本地支持
            return ApiResponse.success(reminderService.findByPet(petId));
        }
        if (ownerId != null) {
            if (dubboEnabled) {
                try {
                    return ApiResponse.success(reminderDubboService.findByOwnerId(ownerId));
                } catch (Exception e) {
                    log.warn("Dubbo findByOwnerId 调用失败，降级本地: {}", e.getMessage());
                }
            }
            return ApiResponse.success(reminderService.findByOwner(ownerId));
        }
        return ApiResponse.success(List.of());
    }

    /**
     * GET /api/reminders/due?days=7 — 即将到期（未来 N 天内）
     */
    @GetMapping("/due")
    public ApiResponse<List<Reminder>> due(@RequestParam(defaultValue = "7") int days) {
        // Dubbo 接口按小时查询，days*24 转换
        if (dubboEnabled) {
            try {
                return ApiResponse.success(reminderDubboService.findPendingWithin(days * 24));
            } catch (Exception e) {
                log.warn("Dubbo findPendingWithin 调用失败，降级本地: {}", e.getMessage());
            }
        }
        return ApiResponse.success(reminderService.findDueWithin(days));
    }

    /**
     * POST /api/reminders — 创建提醒（ownerId 由服务端登录态注入）
     * <p>
     * 本地 ReminderService 含宠物名/属主补全逻辑；Dubbo 路径仅做最简存储，
     * 两者共享同一 MongoDB 库，数据最终一致。
     */
    @PostMapping
    public ApiResponse<Reminder> create(@RequestBody Reminder reminder, HttpServletRequest request) {
        // ownerId 由服务端登录态注入，不信任客户端传入的 ownerId/email
        reminder.setOwnerId(AuthContext.requireUserId(request));
        Reminder saved = reminderService.create(reminder);
        // Dubbo 同步一份到 reminder-service（best-effort，失败不影响主流程）
        if (dubboEnabled) {
            try {
                reminderDubboService.create(reminder);
            } catch (Exception e) {
                log.debug("Dubbo 同步创建提醒失败（非关键）: {}", e.getMessage());
            }
        }
        return ApiResponse.success(saved);
    }

    /**
     * PUT /api/reminders/{id}/cancel — 取消（仅提醒属主）
     */
    @PutMapping("/{id}/cancel")
    public ApiResponse<Reminder> cancel(@PathVariable String id, HttpServletRequest request) {
        Reminder r = reminderService.cancel(id, AuthContext.requireUserId(request));
        if (r == null) return ApiResponse.error(404, "提醒不存在");
        return ApiResponse.success(r);
    }

    /**
     * PUT /api/reminders/{id}/acknowledge — 确认（仅提醒属主）
     */
    @PutMapping("/{id}/acknowledge")
    public ApiResponse<Reminder> acknowledge(@PathVariable String id, HttpServletRequest request) {
        String userId = AuthContext.requireUserId(request);
        if (dubboEnabled) {
            try {
                Reminder r = reminderDubboService.acknowledge(id);
                if (r != null) {
                    checkReminderOwner(r, userId);
                    return ApiResponse.success(r);
                }
            } catch (AccessDeniedException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Dubbo acknowledge 调用失败，降级本地: {}", e.getMessage());
            }
        }
        Reminder r = reminderService.acknowledge(id, userId);
        if (r == null) return ApiResponse.error(404, "提醒不存在");
        return ApiResponse.success(r);
    }

    /**
     * PUT /api/reminders/{id} — 编辑提醒（仅 PENDING 可编辑，且仅提醒属主）
     */
    @PutMapping("/{id}")
    public ApiResponse<Reminder> update(@PathVariable String id, @RequestBody Reminder patch,
                                        HttpServletRequest request) {
        Reminder r = reminderService.update(id, patch, AuthContext.requireUserId(request));
        if (r == null) return ApiResponse.error(404, "提醒不存在或当前状态不可编辑");
        return ApiResponse.success(r);
    }

    /**
     * DELETE /api/reminders/{id} — 删除提醒（仅提醒属主）
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest request) {
        if (!reminderService.delete(id, AuthContext.requireUserId(request))) {
            return ApiResponse.error(404, "提醒不存在");
        }
        return ApiResponse.success(null);
    }

    /** Dubbo 路径返回的提醒也需校验属主（防止 Dubbo 路径绕过本地校验） */
    private void checkReminderOwner(Reminder reminder, String userId) {
        if (userId == null || !userId.equals(reminder.getOwnerId())) {
            throw new AccessDeniedException("无权操作他人提醒");
        }
    }
}
