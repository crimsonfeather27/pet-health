package com.pethealth.service.dubbo;

import com.pethealth.entity.HealthRecord;
import com.pethealth.repository.HealthRecordRepository;
import com.pethealth.service.HealthRecordStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康记录 Dubbo 服务实现（Provider 端）
 * <p>
 * 暴露给 pethealth-web 通过 Dubbo 调用。Dubbo 协议端口 20885。
 */
@Slf4j
@DubboService
@RequiredArgsConstructor
public class HealthRecordDubboServiceImpl implements HealthRecordDubboService {

    private final HealthRecordRepository healthRecordRepository;
    private final HealthRecordStatsService healthRecordStatsService;

    @Override
    public HealthRecord create(HealthRecord record) {
        log.info("Dubbo create health record: petId={}, recordType={}", record.getPetId(), record.getRecordType());
        if (record.getRecordedAt() == null) {
            record.setRecordedAt(LocalDateTime.now());
        }
        record.setCreatedAt(LocalDateTime.now());
        HealthRecord saved = healthRecordRepository.save(record);
        // 失效该宠物的统计缓存
        healthRecordStatsService.invalidateCache(record.getPetId());
        return saved;
    }

    @Override
    public List<HealthRecord> findByPetId(String petId, int page, int size) {
        log.debug("Dubbo findByPetId: petId={}, page={}, size={}", petId, page, size);
        return healthRecordRepository
                .findByPetIdOrderByRecordedAtDesc(petId, PageRequest.of(page, size))
                .getContent();
    }

    @Override
    public Map<String, Object> getWeeklyStats(String petId) {
        log.debug("Dubbo getWeeklyStats: petId={}", petId);
        return healthRecordStatsService.getStats(petId, "weekly");
    }

    @Override
    public Map<String, Object> getMonthlyStats(String petId) {
        log.debug("Dubbo getMonthlyStats: petId={}", petId);
        return healthRecordStatsService.getStats(petId, "monthly");
    }

    @Override
    public List<Map<String, Object>> getTrend(String petId, String recordType, int days) {
        log.debug("Dubbo getTrend: petId={}, recordType={}, days={}", petId, recordType, days);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(days);
        List<HealthRecord> records = healthRecordRepository
                .findByPetIdAndRecordedAtBetween(petId, start, end);

        List<Map<String, Object>> trend = new ArrayList<>();
        for (HealthRecord r : records) {
            // 按 recordType 过滤（空则全部返回）
            if (recordType != null && !recordType.isBlank()
                    && !recordType.equalsIgnoreCase(r.getRecordType())) {
                continue;
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", r.getRecordedAt() != null ? r.getRecordedAt().toString() : null);
            point.put("recordType", r.getRecordType());
            point.put("value", r.getValue());
            point.put("notes", r.getNotes());
            trend.add(point);
        }
        return trend;
    }

    @Override
    public void invalidateStats(String petId) {
        log.debug("Dubbo invalidateStats: petId={}", petId);
        healthRecordStatsService.invalidateCache(petId);
    }
}
