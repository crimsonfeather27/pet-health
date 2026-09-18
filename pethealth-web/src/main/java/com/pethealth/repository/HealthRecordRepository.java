package com.pethealth.repository;

import com.pethealth.entity.HealthRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface HealthRecordRepository extends MongoRepository<HealthRecord, String> {
    List<HealthRecord> findByPetIdOrderByRecordedAtDesc(String petId);
    Page<HealthRecord> findByPetIdOrderByRecordedAtDesc(String petId, Pageable pageable);
    List<HealthRecord> findByPetIdAndRecordedAtBetween(String petId, LocalDateTime start, LocalDateTime end);
    List<HealthRecord> findByOwnerId(String ownerId);
    Page<HealthRecord> findByOwnerId(String ownerId, Pageable pageable);
}
