package com.pethealth.service;

import com.pethealth.config.RabbitMQConfig;
import com.pethealth.entity.Reminder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 延迟消息生产者
 * 仅当 app.rabbitmq.enabled=true 时生效
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class DelayMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public void sendDelayedReminder(Reminder reminder) {
        long rawDelayMs = ChronoUnit.MILLIS.between(LocalDateTime.now(), reminder.getRemindAt());
        long delayMs = Math.max(60_000L, rawDelayMs);

        try {
            String json = objectMapper.writeValueAsString(reminder);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DELAY_EXCHANGE,
                    RabbitMQConfig.DELAY_ROUTING,
                    json,
                    msg -> {
                        msg.getMessageProperties().setDelay((int) delayMs);
                        return msg;
                    }
            );
            log.info("发送延迟提醒，id={}, 延迟 {} 分钟后触发", reminder.getId(), delayMs / 60000);
        } catch (Exception e) {
            log.error("延迟消息发送失败（ReminderScheduler 会兜底）: {}", e.getMessage());
        }
    }
}
