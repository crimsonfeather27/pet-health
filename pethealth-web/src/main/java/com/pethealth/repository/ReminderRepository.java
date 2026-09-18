package com.pethealth.repository;

import com.pethealth.entity.Reminder;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderRepository extends MongoRepository<Reminder, String> {
    List<Reminder> findByStatusAndRemindAtBefore(String status, LocalDateTime time);
    List<Reminder> findByOwnerIdAndStatusAndRemindAtBefore(String ownerId, String status, LocalDateTime time);
    List<Reminder> findByOwnerId(String ownerId);
    List<Reminder> findByPetId(String petId);
    List<Reminder> findByOwnerIdAndStatus(String ownerId, String status);
}
