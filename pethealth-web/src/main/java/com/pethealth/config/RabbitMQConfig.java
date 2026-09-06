package com.pethealth.config;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 延迟队列配置
 * <p>
 * 延迟消息原理：TTL 队列 + 死信交换机
 * - 消息发到 delay.queue（设置 TTL）
 * - 消息过期后自动转发到 reminder.exchange → reminder.queue
 * - Consumer 监听 reminder.queue 处理到期提醒
 * <p>
 * 优雅降级：app.rabbitmq.enabled=false 时此配置类不生效，
 * ReminderScheduler 定时扫描作为唯一驱动。
 */
@Configuration
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class RabbitMQConfig {

    public static final String DELAY_EXCHANGE  = "pethealth.reminder.delay.exchange";
    public static final String DELAY_QUEUE     = "pethealth.reminder.delay.queue";
    public static final String DELAY_ROUTING  = "reminder.delay";

    public static final String REMINDER_EXCHANGE = "pethealth.reminder.exchange";
    public static final String REMINDER_QUEUE    = "pethealth.reminder.queue";
    public static final String REMINDER_ROUTING  = "reminder.send";

    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", REMINDER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", REMINDER_ROUTING)
                .build();
    }

    @Bean
    public DirectExchange delayExchange() {
        return ExchangeBuilder.directExchange(DELAY_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with(DELAY_ROUTING);
    }

    @Bean
    public Queue reminderQueue() {
        return QueueBuilder.durable(REMINDER_QUEUE).build();
    }

    @Bean
    public DirectExchange reminderExchange() {
        return ExchangeBuilder.directExchange(REMINDER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding reminderBinding() {
        return BindingBuilder.bind(reminderQueue()).to(reminderExchange()).with(REMINDER_ROUTING);
    }
}
