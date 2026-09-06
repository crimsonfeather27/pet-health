package com.pethealth.service;

import com.pethealth.config.GlobalExceptionHandler.ResourceNotFoundException;
import com.pethealth.entity.Notification;
import com.pethealth.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 创建站内信（ownerId 为空时静默跳过，不产生通知）
     */
    public Notification create(String ownerId, String type, String title, String content, String relatedId) {
        if (ownerId == null || ownerId.isBlank()) {
            return null;
        }
        Notification n = Notification.builder()
                .ownerId(ownerId)
                .type(type == null ? "SYSTEM" : type)
                .title(title)
                .content(content)
                .relatedId(relatedId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        Notification saved = notificationRepository.save(n);
        log.info("站内信已创建: ownerId={}, type={}, title={}", ownerId, saved.getType(), saved.getTitle());
        return saved;
    }

    public List<Notification> list(String ownerId) {
        return notificationRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public long unreadCount(String ownerId) {
        return notificationRepository.countByOwnerIdAndIsReadFalse(ownerId);
    }

    public Notification markRead(String id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("通知不存在: " + id));
        n.setIsRead(true);
        return notificationRepository.save(n);
    }

    /**
     * 全部标记已读，返回被标记的数量
     */
    public long markAllRead(String ownerId) {
        List<Notification> list = notificationRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        list.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(list);
        return list.size();
    }

    public void delete(String id) {
        notificationRepository.deleteById(id);
    }
}
