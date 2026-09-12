package com.pethealth.service.dubbo;

import com.pethealth.entity.HealthRecord;

import java.util.List;
import java.util.Map;

/**
 * 健康记录 Dubbo 服务接口（共享契约）
 * <p>
 * 由 health-record-service 提供实现，pethealth-web 通过 Dubbo 调用。设计文档 7.2 节。
 */
public interface HealthRecordDubboService {

    HealthRecord create(HealthRecord record);

    List<HealthRecord> findByPetId(String petId, int page, int size);

    /** 走 Redis 缓存的周统计 */
    Map<String, Object> getWeeklyStats(String petId);

    /** 走 Redis 缓存的月统计 */
    Map<String, Object> getMonthlyStats(String petId);

    /** 趋势查询（指定记录类型 + 天数范围） */
    List<Map<String, Object>> getTrend(String petId, String recordType, int days);

    /** 失效某宠物的统计缓存（CRUD 时由 Consumer 调用） */
    void invalidateStats(String petId);
}
