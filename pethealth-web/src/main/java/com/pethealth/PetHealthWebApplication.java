package com.pethealth;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PetHealth 主入口应用
 * <p>
 * 端口 8080 · 单体 Web · Phase 5 已通过 Dubbo 调用 4 个微服务
 * （pet-service / health-record-service / vet-service / reminder-service）
 */
@SpringBootApplication
@EnableScheduling
@EnableDubbo
public class PetHealthWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetHealthWebApplication.class, args);
    }
}
