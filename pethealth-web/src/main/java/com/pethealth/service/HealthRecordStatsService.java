package com.pethealth.service;

import com.pethealth.entity.HealthRecord;
import com.pethealth.repository.HealthRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 宠物健康记录周/月聚合统计服务
 * <p>
 * 缓存键设计（设计文档 4.12 节）:
 * - pethealth:pet:{petId}:weekly-stats  → Hash，本周聚合，TTL 7 天
 * - pethealth:pet:{petId}:monthly-stats → Hash，本月聚合，TTL 30 天
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthRecordStatsService {

    private final HealthRecordRepository healthRecordRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String WEEKLY_KEY_PREFIX  = "pethealth:pet:%s:weekly-stats";
    private static final String MONTHLY_KEY_PREFIX = "pethealth:pet:%s:monthly-stats";
    private static final long   WEEKLY_TTL_DAYS    = 7;
    private static final long   MONTHLY_TTL_DAYS   = 30;

    /**
     * 获取某宠物指定周期的健康统计（带 Redis Hash 缓存）
     *
     * @param petId  宠物 ID
     * @param period weekly / monthly
     * @return 聚合统计 Map
     */
    public Map<String, Object> getStats(String petId, String period) {
        boolean isMonthly = "monthly".equalsIgnoreCase(period);
        String cacheKey = isMonthly
                ? String.format(MONTHLY_KEY_PREFIX, petId)
                : String.format(WEEKLY_KEY_PREFIX, petId);

        // 1. 尝试 Redis Hash 缓存
        try {
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                log.debug("命中健康统计缓存: {}", cacheKey);
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<Object, Object> e : cached.entrySet()) {
                    result.put(String.valueOf(e.getKey()), e.getValue());
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Redis 读取失败，降级查 MongoDB: {}", e.getMessage());
        }

        // 2. 缓存 miss → 查 MongoDB 实时聚合
        LocalDateTime[] range = computeRange(isMonthly);
        Map<String, Object> stats = computeStats(petId, range[0], range[1]);

        // 3. 写入缓存
        try {
            redisTemplate.opsForHash().putAll(cacheKey, stats);
            long ttlDays = isMonthly ? MONTHLY_TTL_DAYS : WEEKLY_TTL_DAYS;
            redisTemplate.expire(cacheKey, ttlDays, TimeUnit.DAYS);
            log.debug("健康统计已缓存: {}", cacheKey);
        } catch (Exception e) {
            log.warn("Redis 写入失败（非关键），跳过缓存: {}", e.getMessage());
        }

        return stats;
    }

    /**
     * 新增/更新/删除健康记录时，失效该宠物的所有统计缓存
     */
    public void invalidateCache(String petId) {
        try {
            redisTemplate.delete(Arrays.asList(
                    String.format(WEEKLY_KEY_PREFIX, petId),
                    String.format(MONTHLY_KEY_PREFIX, petId)
            ));
            log.debug("已失效健康统计缓存: petId={}", petId);
        } catch (Exception e) {
            log.warn("Redis 失效失败（非关键）: {}", e.getMessage());
        }
    }

    /**
     * 计算时间范围：本周一 00:00:00 ~ 今天 23:59:59（或本月 1 号 ~ 月末）
     */
    private LocalDateTime[] computeRange(boolean monthly) {
        LocalDate today = LocalDate.now();
        LocalDate startDate;
        if (monthly) {
            startDate = today.withDayOfMonth(1);
        } else {
            startDate = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end   = today.plusDays(1).atStartOfDay().minusSeconds(1);
        return new LocalDateTime[]{start, end};
    }

    /**
     * 从 MongoDB 实时聚合计算统计数据
     */
    private Map<String, Object> computeStats(String petId, LocalDateTime start, LocalDateTime end) {
        List<HealthRecord> records = healthRecordRepository
                .findByPetIdAndRecordedAtBetween(petId, start, end);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("petId", petId);
        stats.put("period", records.isEmpty() ? "empty" : "computed");
        stats.put("recordCount", records.size());
        stats.put("periodStart", start.toString());
        stats.put("periodEnd", end.toString());

        if (records.isEmpty()) {
            stats.put("message", "该周期内暂无健康记录");
            return stats;
        }

        // 按 recordType 分组
        Map<String, List<HealthRecord>> byType = new HashMap<>();
        for (HealthRecord r : records) {
            byType.computeIfAbsent(r.getRecordType(), k -> new ArrayList<>()).add(r);
        }

        stats.put("recordTypes", new ArrayList<>(byType.keySet()));

        // 对"体重"记录：计算平均值 + 范围
        List<HealthRecord> weights = byType.getOrDefault("体重", List.of());
        if (!weights.isEmpty()) {
            List<Double> values = extractNumericValues(weights);
            if (!values.isEmpty()) {
                stats.put("weightAvg", Math.round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100) / 100.0);
                stats.put("weightMin", Collections.min(values));
                stats.put("weightMax", Collections.max(values));
                stats.put("weightTrend", computeTrend(values));
            }
        }

        // 对"体温"记录
        List<HealthRecord> temps = byType.getOrDefault("体温", List.of());
        if (!temps.isEmpty()) {
            List<Double> values = extractNumericValues(temps);
            if (!values.isEmpty()) {
                stats.put("tempAvg", Math.round(values.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100) / 100.0);
                stats.put("tempMin", Collections.min(values));
                stats.put("tempMax", Collections.max(values));
            }
        }

        // 记录按日期分布（给 ECharts 画趋势图用）
        Map<String, Integer> dailyDist = new LinkedHashMap<>();
        for (HealthRecord r : records) {
            String day = r.getRecordedAt().toLocalDate().toString();
            dailyDist.merge(day, 1, Integer::sum);
        }
        stats.put("dailyDistribution", dailyDist);

        return stats;
    }

    private List<Double> extractNumericValues(List<HealthRecord> records) {
        List<Double> result = new ArrayList<>();
        for (HealthRecord r : records) {
            if (r.getValue() == null) continue;
            Object v = r.getValue().get("value");
            if (v instanceof Number n) {
                result.add(n.doubleValue());
            } else if (v instanceof String s) {
                try { result.add(Double.parseDouble(s)); } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }

    /**
     * 简单趋势判断：最近值 vs 之前值
     */
    private String computeTrend(List<Double> values) {
        if (values.size() < 2) return "stable";
        Double last = values.get(values.size() - 1);
        Double first = values.get(0);
        double diff = last - first;
        if (Math.abs(diff) < 0.5) return "stable";
        return diff > 0 ? "up" : "down";
    }
}
