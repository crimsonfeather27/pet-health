package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.Reminder;
import com.pethealth.service.ReminderService;
import com.pethealth.service.dubbo.ReminderDubboService;
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
     * POST /api/reminders — 创建提醒
     * <p>
     * 本地 ReminderService 包含自动创建延迟消息 + 邮箱补全逻辑，更完整；
     * Dubbo 仅做最简存储。优先走本地，仅在显式 dubbo+no-local 时才走 Dubbo。
     * 这里保留本地实现以维持 Phase 4 的延迟消息链路。
     */
    @PostMapping
    public ApiResponse<Reminder> create(@RequestBody Reminder reminder) {
        // 始终走本地（保持 RabbitMQ 延迟消息链路完整）
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
     * PUT /api/reminders/{id}/cancel — 取消
     */
    @PutMapping("/{id}/cancel")
    public ApiResponse<Reminder> cancel(@PathVariable String id) {
        Reminder r = reminderService.cancel(id);
        if (r == null) return ApiResponse.error(404, "提醒不存在");
        return ApiResponse.success(r);
    }

    /**
     * PUT /api/reminders/{id}/acknowledge — 确认
     */
    @PutMapping("/{id}/acknowledge")
    public ApiResponse<Reminder> acknowledge(@PathVariable String id) {
        if (dubboEnabled) {
            try {
                Reminder r = reminderDubboService.acknowledge(id);
                if (r != null) return ApiResponse.success(r);
            } catch (Exception e) {
                log.warn("Dubbo acknowledge 调用失败，降级本地: {}", e.getMessage());
            }
        }
        Reminder r = reminderService.acknowledge(id);
        if (r == null) return ApiResponse.error(404, "提醒不存在");
        return ApiResponse.success(r);
    }

    /**
     * PUT /api/reminders/{id} — 编辑提醒（仅 PENDING 可编辑）
     */
    @PutMapping("/{id}")
    public ApiResponse<Reminder> update(@PathVariable String id, @RequestBody Reminder patch) {
        Reminder r = reminderService.update(id, patch);
        if (r == null) return ApiResponse.error(404, "提醒不存在或当前状态不可编辑");
        return ApiResponse.success(r);
    }

    /**
     * DELETE /api/reminders/{id} — 删除提醒
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        if (!reminderService.delete(id)) return ApiResponse.error(404, "提醒不存在");
        return ApiResponse.success(null);
    }
}
