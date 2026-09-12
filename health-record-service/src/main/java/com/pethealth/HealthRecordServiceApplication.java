package com.pethealth;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Health Record Service · 健康记录 + AI 诊断微服务
 * <p>
 * HTTP 端口 8086 · Dubbo 端口 20885 · MongoDB: pethealth_record
 */
@SpringBootApplication
@EnableDubbo
public class HealthRecordServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthRecordServiceApplication.class, args);
    }
}
