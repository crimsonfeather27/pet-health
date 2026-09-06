package com.pethealth.repository;

import com.pethealth.entity.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {

    List<Notification> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

    long countByOwnerIdAndIsReadFalse(String ownerId);
}
