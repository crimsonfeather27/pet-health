package com.pethealth.controller;

import com.pethealth.dto.ApiResponse;
import com.pethealth.entity.HealthRecord;
import com.pethealth.repository.HealthRecordRepository;
import com.pethealth.service.HealthRecordStatsService;
import com.pethealth.service.dubbo.HealthRecordDubboService;
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
    public ApiResponse<List<HealthRecord>> byPet(@PathVariable String petId) {
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
            @RequestParam(defaultValue = "10") int size) {
        // 分页查询保留本地（Dubbo 接口未直接暴露 Page）
        return ApiResponse.success(healthRecordRepository.findAll(PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<HealthRecord> get(@PathVariable String id) {
        return healthRecordRepository.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.error(404, "健康记录不存在"));
    }

    /**
     * GET /api/health-records/pet/{petId}/trends — 宠物健康趋势统计（Redis 缓存）
     * Dubbo 调用 health-record-service 的 getWeeklyStats / getMonthlyStats
     */
    @GetMapping("/pet/{petId}/trends")
    public ApiResponse<Map<String, Object>> trends(
            @PathVariable String petId,
            @RequestParam(defaultValue = "weekly") String period) {
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
    public ApiResponse<HealthRecord> create(@RequestBody HealthRecord record) {
        if (record.getRecordedAt() == null) {
            record.setRecordedAt(LocalDateTime.now());
        }
        record.setCreatedAt(LocalDateTime.now());

        if (dubboEnabled) {
            try {
                HealthRecord saved = healthRecordDubboService.create(record);
                return ApiResponse.success(saved);
            } catch (Exception e) {
                log.warn("Dubbo create 调用失败，降级本地: {}", e.getMessage());
            }
        }
        HealthRecord saved = healthRecordRepository.save(record);
        if (saved.getPetId() != null) statsService.invalidateCache(saved.getPetId());
        return ApiResponse.success(saved);
    }

    @PutMapping("/{id}")
    public ApiResponse<HealthRecord> update(@PathVariable String id, @RequestBody HealthRecord updates) {
        return healthRecordRepository.findById(id).map(existing -> {
            if (updates.getRecordType() != null) existing.setRecordType(updates.getRecordType());
            if (updates.getValue() != null) existing.setValue(updates.getValue());
            if (updates.getNotes() != null) existing.setNotes(updates.getNotes());
            if (updates.getPetId() != null) existing.setPetId(updates.getPetId());
            if (updates.getRecordedAt() != null) existing.setRecordedAt(updates.getRecordedAt());
            HealthRecord saved = healthRecordRepository.save(existing);
            statsService.invalidateCache(existing.getPetId());
            // 同步失效远端缓存
            if (dubboEnabled) {
                try {
                    healthRecordDubboService.invalidateStats(existing.getPetId());
                } catch (Exception e) {
                    log.debug("Dubbo invalidateStats 失败（非关键）: {}", e.getMessage());
                }
            }
            return ApiResponse.success(saved);
        }).orElse(ApiResponse.error(404, "健康记录不存在"));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        return healthRecordRepository.findById(id).map(existing -> {
            healthRecordRepository.deleteById(id);
            statsService.invalidateCache(existing.getPetId());
            if (dubboEnabled) {
                try {
                    healthRecordDubboService.invalidateStats(existing.getPetId());
                } catch (Exception e) {
                    log.debug("Dubbo invalidateStats 失败（非关键）: {}", e.getMessage());
                }
            }
            return ApiResponse.<Void>success("删除成功", null);
        }).orElse(ApiResponse.error(404, "健康记录不存在"));
    }
}
