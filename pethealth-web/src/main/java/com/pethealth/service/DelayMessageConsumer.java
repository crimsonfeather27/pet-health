package com.pethealth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pethealth.config.RabbitMQConfig;
import com.pethealth.entity.Reminder;
import com.pethealth.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 延迟消息消费者
 * 监听 reminder.queue（死信队列），收到消息后发送邮件 + 更新状态
 * 仅当 app.rabbitmq.enabled=true 时生效
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class DelayMessageConsumer {

    private final ObjectMapper objectMapper;
    private final ReminderRepository reminderRepository;
    private final EmailNotifyService emailNotifyService;

    @RabbitListener(queues = RabbitMQConfig.REMINDER_QUEUE)
    public void onReminder(String payload) {
        try {
            Reminder reminder = objectMapper.readValue(payload, Reminder.class);
            log.info("收到到期提醒，id={}, title={}", reminder.getId(), reminder.getTitle());

            emailNotifyService.send(reminder);

            reminder.setStatus("SENT");
            reminder.setUpdatedAt(LocalDateTime.now());
            reminderRepository.save(reminder);
        } catch (Exception e) {
            log.error("处理提醒消息失败", e);
        }
    }
}
