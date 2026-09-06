package com.pethealth.service;

import com.pethealth.entity.Reminder;
import com.pethealth.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提醒定时调度器（兜底方案）
 * 每分钟扫描 remindAt <= now && status=PENDING 的提醒
 * <p>
 * 当 RabbitMQ 可用时，延迟消息是主驱动；此调度器作为兜底补发。
 * 当 RabbitMQ 不可用时（app.rabbitmq.enabled=false），此调度器是唯一驱动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderScheduler {

    private final ReminderRepository reminderRepository;
    private final EmailNotifyService emailNotifyService;

    @Scheduled(fixedDelayString = "${reminder.scheduler-interval-seconds:60}000")
    public void scanAndSend() {
        LocalDateTime now = LocalDateTime.now();
        List<Reminder> dueList = reminderRepository.findByStatusAndRemindAtBefore("PENDING", now);

        if (dueList.isEmpty()) {
            log.debug("无到期提醒");
            return;
        }

        log.info("扫描到 {} 条到期提醒", dueList.size());

        for (Reminder reminder : dueList) {
            try {
                log.info("处理到期提醒: id={}, title={}, pet={}", reminder.getId(), reminder.getTitle(), reminder.getPetName());
                emailNotifyService.send(reminder);
                reminder.setStatus("SENT");
                reminder.setUpdatedAt(LocalDateTime.now());
                reminderRepository.save(reminder);
            } catch (Exception e) {
                log.error("发送提醒失败，id={}", reminder.getId(), e);
            }
        }
    }
}
