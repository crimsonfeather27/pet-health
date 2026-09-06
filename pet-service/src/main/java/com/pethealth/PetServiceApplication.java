package com.pethealth;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Pet Service · 宠物档案微服务
 * <p>
 * HTTP 端口 8081 · Dubbo 端口 20881 · MongoDB: pethealth
 */
@SpringBootApplication
@EnableDubbo
public class PetServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetServiceApplication.class, args);
    }
}
