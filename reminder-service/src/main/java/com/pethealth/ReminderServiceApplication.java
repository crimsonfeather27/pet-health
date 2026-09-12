package com.pethealth;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Reminder Service · 提醒调度 + 状态流转
 * <p>
 * HTTP 端口 8084 · Dubbo 端口 20884 · MongoDB: pethealth_reminder
 */
@SpringBootApplication
@EnableDubbo
@EnableScheduling
public class ReminderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReminderServiceApplication.class, args);
    }
}
