package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.HealthRecord;
import com.pethealth.exception.AccessDeniedException;
import com.pethealth.interceptor.AuthContext;
import com.pethealth.repository.HealthRecordRepository;
import com.pethealth.service.HealthRecordStatsService;
import com.pethealth.service.OwnershipGuard;
import com.pethealth.service.dubbo.HealthRecordDubboService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health-records")
@RequiredArgsConstructor
public class HealthRecordController {

    private final HealthRecordRepository healthRecordRepository;
    private final HealthRecordStatsService statsService;
    private final OwnershipGuard ownershipGuard;

    /**
     * Dubbo Consumer —— 直连 health-record-service（端口 20885）
     */
    @DubboReference(
            url = "dubbo://localhost:20885/com.pethealth.service.dubbo.HealthRecordDubboService",
            check = false,
            timeout = 5000
    )
    private HealthRecordDubboService healthRecordDubboService;

    @Value("${app.dubbo.enabled:true}")
    private boolean dubboEnabled;

    @GetMapping("/pet/{petId}")
    public ApiResponse<List<HealthRecord>> byPet(@PathVariable String petId, HttpServletRequest request) {
        // 先校验这只宠物属于当前登录用户，防止枚举 petId 拖取他人病历
        ownershipGuard.requireOwnedPet(request, petId);
        if (dubboEnabled) {
            try {
                // Dubbo 接口默认分页，传一个较大的 size 拿全部
                List<HealthRecord> records = healthRecordDubboService.findByPetId(petId, 0, 1000);
                return ApiResponse.success(records);
            } catch (Exception e) {
                log.warn("Dubbo findByPetId 调用失败，降级本地: {}", e.getMessage());
            }
        }
        return ApiResponse.success(healthRecordRepository.findByPetIdOrderByRecordedAtDesc(petId));
    }

    @GetMapping
    public ApiResponse<Page<HealthRecord>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        // 私有数据：只返回当前登录用户自己的健康记录
        String userId = AuthContext.requireUserId(request);
        return ApiResponse.success(
                healthRecordRepository.findByOwnerId(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<HealthRecord> get(@PathVariable String id, HttpServletRequest request) {
        HealthRecord record = healthRecordRepository.findById(id).orElse(null);
        if (record == null) {
            return ApiResponse.error(404, "健康记录不存在");
        }
        String userId = AuthContext.requireUserId(request);
        if (!userId.equals(record.getOwnerId())) {
            throw new AccessDeniedException("无权访问他人健康记录");
        }
        return ApiResponse.success(record);
    }

    /**
     * GET /api/health-records/pet/{petId}/trends — 宠物健康趋势统计（Redis 缓存）
     * Dubbo 调用 health-record-service 的 getWeeklyStats / getMonthlyStats
     */
    @GetMapping("/pet/{petId}/trends")
    public ApiResponse<Map<String, Object>> trends(
            @PathVariable String petId,
            @RequestParam(defaultValue = "weekly") String period,
            HttpServletRequest request) {
        // 属主校验先行，避免他人 petId 的统计数据泄露
        ownershipGuard.requireOwnedPet(request, petId);
        if (dubboEnabled) {
            try {
                Map<String, Object> stats = "monthly".equalsIgnoreCase(period)
                        ? healthRecordDubboService.getMonthlyStats(petId)
                        : healthRecordDubboService.getWeeklyStats(petId);
                return ApiResponse.success(stats);
            } catch (Exception e) {
                log.warn("Dubbo getStats 调用失败，降级本地: {}", e.getMessage());
            }
        }
        return ApiResponse.success(statsService.getStats(petId, period));
    }

    @PostMapping
    public ApiResponse<HealthRecord> create(@RequestBody HealthRecord record, HttpServletRequest request) {
        // ownerId 由服务端登录态注入，不信任客户端
        String userId = AuthContext.requireUserId(request);
        record.setOwnerId(userId);
        // 记录必须挂在本人宠物下，防止给他人宠物伪造病历
        if (record.getPetId() != null && !record.getPetId().isBlank()) {
            ownershipGuard.requireOwnedPet(request, record.getPetId());
        }
        if (record.getRecordedAt() == null) {
            record.setRecordedAt(LocalDateTime.now());
        }
        record.setCreatedAt(LocalDateTime.now());

        if (dubboEnabled) {
            try {
                HealthRecord saved = healthRecordDubboService.create(record);
                invalidateRecordStats(saved.getPetId());
                return ApiResponse.success(saved);
            } catch (Exception e) {
                log.warn("Dubbo create 调用失败，降级本地: {}", e.getMessage());
            }
        }
        HealthRecord saved = healthRecordRepository.save(record);
        invalidateRecordStats(saved.getPetId());
        return ApiResponse.success(saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<HealthRecord> update(@PathVariable String id, @RequestBody HealthRecord updates,
                                            HttpServletRequest request) {
        checkRecordOwner(id, request);
        return healthRecordRepository.findById(id).map(existing -> {
            // #12：记录可能被改挂到另一只宠物，旧/新 petId 的缓存都要失效
            String oldPetId = existing.getPetId();
            if (updates.getRecordType() != null) existing.setRecordType(updates.getRecordType());
            if (updates.getValue() != null) existing.setValue(updates.getValue());
            if (updates.getNotes() != null) existing.setNotes(updates.getNotes());
            // 改挂目标宠物也必须是本人的
            if (updates.getPetId() != null && !updates.getPetId().equals(oldPetId)) {
                ownershipGuard.requireOwnedPet(request, updates.getPetId());
            }
            if (updates.getPetId() != null) existing.setPetId(updates.getPetId());
            if (updates.getRecordedAt() != null) existing.setRecordedAt(updates.getRecordedAt());
            HealthRecord saved = healthRecordRepository.save(existing);
            invalidateRecordStats(oldPetId, saved.getPetId());
            return ApiResponse.success(saved);
        }).orElse(ApiResponse.error(404, "健康记录不存在"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, HttpServletRequest request) {
        checkRecordOwner(id, request);
        return healthRecordRepository.findById(id).map(existing -> {
            healthRecordRepository.deleteById(id);
            invalidateRecordStats(existing.getPetId());
            return ApiResponse.<Void>success("删除成功", null);
        }).orElse(ApiResponse.error(404, "健康记录不存在"));
    }

    /**
     * #11 失效对称：本地 + Dubbo 双侧失效；#12 宠物换绑时传入新旧 petId 全部清掉
     */
    private void invalidateRecordStats(String... petIds) {
        for (String petId : petIds) {
            if (petId == null || petId.isBlank()) continue;
            try {
                statsService.invalidateCache(petId);
            } catch (Exception e) {
                log.debug("本地统计缓存失效失败（非关键）: petId={}, {}", petId, e.getMessage());
            }
            if (dubboEnabled) {
                try {
                    healthRecordDubboService.invalidateStats(petId);
                } catch (Exception e) {
                    log.debug("Dubbo invalidateStats 失败（非关键）: petId={}, {}", petId, e.getMessage());
                }
            }
        }
    }

    /** 属主校验：仅记录归属人可编辑/删除（Dubbo 调用前先校验，避免越权穿透） */
    private void checkRecordOwner(String recordId, HttpServletRequest request) {
        String userId = AuthContext.requireUserId(request);
        HealthRecord record = healthRecordRepository.findById(recordId).orElse(null);
        if (record == null) {
            throw new com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException("健康记录不存在: " + recordId);
        }
        if (!userId.equals(record.getOwnerId())) {
            throw new AccessDeniedException("无权操作他人健康记录");
        }
    }
}
