package com.pethealth.service.dubbo;

import com.pethealth.entity.Reminder;
import com.pethealth.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * ReminderDubboService Provider 端实现。
 * <p>
 * 暴露在 Dubbo 端口 20884，由 @EnableDubbo 自动扫描注册。
 */
@Slf4j
@RequiredArgsConstructor
@DubboService
public class ReminderDubboServiceImpl implements ReminderDubboService {

    private final ReminderRepository reminderRepository;

    @Override
    public Reminder create(Reminder reminder) {
        if (reminder.getStatus() == null || reminder.getStatus().isEmpty()) {
            reminder.setStatus("PENDING");
        }
        LocalDateTime now = LocalDateTime.now();
        reminder.setCreatedAt(now);
        reminder.setUpdatedAt(now);
        Reminder saved = reminderRepository.save(reminder);
        log.info("Created reminder id={}, ownerId={}, petId={}, remindAt={}",
                saved.getId(), saved.getOwnerId(), saved.getPetId(), saved.getRemindAt());
        return saved;
    }

    @Override
    public List<Reminder> findByOwnerId(String ownerId) {
        log.debug("findByOwnerId: {}", ownerId);
        return reminderRepository.findByOwnerId(ownerId);
    }

    @Override
    public List<Reminder> findPendingWithin(int hours) {
        LocalDateTime end = LocalDateTime.now().plusHours(hours);
        log.debug("findPendingWithin: hours={}, end={}", hours, end);
        return reminderRepository.findByStatusAndRemindAtBefore("PENDING", end);
    }

    @Override
    public Reminder markSent(String reminderId) {
        Optional<Reminder> optional = reminderRepository.findById(reminderId);
        if (optional.isEmpty()) {
            log.warn("markSent: reminder not found, id={}", reminderId);
            return null;
        }
        Reminder reminder = optional.get();
        reminder.setStatus("SENT");
        reminder.setUpdatedAt(LocalDateTime.now());
        Reminder saved = reminderRepository.save(reminder);
        log.info("markSent: id={}", reminderId);
        return saved;
    }

    @Override
    public Reminder acknowledge(String reminderId) {
        Optional<Reminder> optional = reminderRepository.findById(reminderId);
        if (optional.isEmpty()) {
            log.warn("acknowledge: reminder not found, id={}", reminderId);
            return null;
        }
        Reminder reminder = optional.get();
        reminder.setStatus("ACKNOWLEDGED");
        reminder.setUpdatedAt(LocalDateTime.now());
        Reminder saved = reminderRepository.save(reminder);
        log.info("acknowledge: id={}", reminderId);
        return saved;
    }

    @Override
    public void scheduleFromPet(String petId) {
        log.warn("scheduleFromPet: petId={} 需要 PetDubboService 远程调用获取宠物信息，"
                + "目前仅在 reminder-service 端记录日志。实际生产应由 pet-service 触发调用此方法时传入完整 reminder。", petId);
    }
}
