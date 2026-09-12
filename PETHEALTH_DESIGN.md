# PetHealth · 宠物健康管家 — 完整项目设计文档

> **一句话定位：** 一站式宠物健康管理平台，帮铲屎官记录宠物健康数据、提醒就医、AI 辅助诊断。

---

## 目录

- [0. 项目概述](#0-项目概述)
- [1. 技术栈总览](#1-技术栈总览)
- [2. 系统架构设计](#2-系统架构设计)
- [3. 项目目录结构](#3-项目目录结构)
- [4. 数据库设计（MongoDB + Redis）](#4-数据库设计mongodb--redis)
- [5. 核心实体与 DTO 定义](#5-核心实体与-dto-定义)
- [6. REST API 接口文档](#6-rest-api-接口文档)
- [7. Dubbo 微服务接口设计](#7-dubbo-微服务接口设计)
- [8. ~~RabbitMQ 消息队列设计~~（已下线）](#8-rabbitmq-消息队列设计已下线)
- [9. AI 功能设计](#9-ai-功能设计)
- [10. 前端页面设计](#10-前端页面设计)
- [11. 配置文件模板](#11-配置文件模板)
- [12. 分布式追踪与监控](#12-分布式追踪与监控)
- [13. 部署指南](#13-部署指南)
- [14. 开发路线图](#14-开发路线图)

---

## 0. 项目概述

### 0.1 为什么做这个？

| 优势 | 说明 |
|------|------|
| 宠物经济火热 | 2025 年中国宠物市场规模超 4000 亿，面试官天然有共鸣 |
| 业务场景天然复杂 | 档案管理 / 健康追踪 / 社区 / AI 诊断 / 营养计算，能充分展示架构能力 |
| 数据维度丰富 | 宠物档案包含疫苗、驱虫、体检、饮食、运动等多维度，MongoDB 灵活 Schema 优势尽显 |
| AI 结合自然 | "症状描述 → AI 诊断建议" 是大模型落地的天然场景 |

### 0.2 核心模块一览

| 模块 | 功能 | 技术亮点 |
|------|------|----------|
| 宠物档案 | 多宠物管理、基本信息、疫苗记录、驱虫记录 | MongoDB 嵌套文档 + 动态字段扩展 |
| 健康记录 | 体重 / 体温 / 饮食 / 运动量追踪 + 趋势图表 | Redis 缓存周 / 月聚合数据，ECharts 前端可视化 |
| 提醒系统 | 疫苗到期、驱虫提醒、体检提醒 | 定时调度 `findAndModify` 原子抢占 + 应用内提醒 |
| AI 健康助手 | 症状描述 → AI 初步诊断建议 + 就医指引 | DeepSeek AI + Prompt 工程 + 安全兜底 |
| 宠物社区 | 养宠经验分享、问答互动 | 嵌套评论 + 点赞 + Redis 热门榜 (Sorted Set) |
| 营养助手 | 按 NRC/WSAVA 公式计算每日能量与喂食量，支持 BCS 体重管理 | 档案 + 健康记录数据聚合 + 规则表引擎 + ECharts 体重趋势 |

### 0.3 模块设计映射关系

> 本项目整体架构、代码风格、技术选型经过实际验证可用，此处列出各模块在架构中的角色对应关系。

| 原设计（学习类平台） | PetHealth（宠物健康管家） | 说明 |
|------------------------|--------------------------|------|
| User | PetOwner | 宠物主人账号 |
| KnowledgePoint | HealthCategory | 健康分类 / 标签 |
| Question | Post | 社区帖子 |
| Answer | Reply | 帖子下的回复 |
| Comment | Comment | 嵌套评论 |
| Like | Like | 点赞 |
| LearningProgress | PetProfile | 宠物档案 |
| StudyRecord | HealthRecord | 健康记录 |
| AI 学习建议 | AI 健康诊断 | LLM 调用 + Prompt 改造 |
| 知识点 → 问答 | 健康分类 → 社区帖子 | 关联模型复用 |
| 学习时长 | 健康指标变化 | 统计逻辑复用 |

---

## 1. 技术栈总览

### 1.1 技术栈选型

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 17 | JDK 21 兼容 |
| 框架 | Spring Boot | 3.2.0 | 主框架 |
| 微服务 | Apache Dubbo | 3.2.4 | RPC 通信 |
| 注册中心 | Nacos（可选） | 2.3.0 | 生产可用；本地演示采用 Dubbo 直连（registry: N/A，见 2.4） |
| Spring Cloud | Spring Cloud | 2023.0.0 | 微服务基础 |
| 数据库 | MongoDB | 8.2.0 | 主存储（文档型，灵活 Schema） |
| 缓存 | Redis | 7.x | 排行榜、会话 Token、统计缓存 |
| ~~消息队列~~ | ~~RabbitMQ~~ | — | **已下线**：提醒改为定时调度 + 应用内触达，无 MQ 依赖 |
| ~~分布式追踪~~ | ~~Zipkin + Micrometer~~ | — | **未启用**：本项目未接入链路追踪 |
| AI 调用 | OkHttp + DeepSeek API | 4.12.0 | AI 健康诊断 |
| JSON | FastJSON2 | 2.0.41 | JSON 处理 |
| Lombok | Lombok | - | 简化 POJO |
| 前端 | 原生 HTML + CSS + JS | - | 单页应用 |
| 图表 | ECharts | 5.4.3 | 健康趋势图 |
| 图标 | Lucide Icons | - | 前端图标 |
| 安全 | Spring Security | 6.x | BCrypt 密码加密 + 认证 |
| 构建 | Maven | 3.9+ | 多模块构建 |

### 1.2 依赖版本锁（pom.xml 片段）

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<properties>
    <java.version>17</java.version>
    <dubbo.version>3.2.4</dubbo.version>
    <nacos.version>2.3.0</nacos.version>
    <spring-cloud.version>2023.0.0</spring-cloud.version>
</properties>
```

---

## 2. 系统架构设计

### 2.1 整体架构图

```
                       ┌──────────────────────────────┐
                       │         前端 (浏览器)          │
                       │  index.html + script.js + CSS │
                       │  ECharts 图表  Lucide 图标     │
                       └──────────────┬───────────────┘
                                      │ HTTP/JSON
                                      ▼
┌────────────────────────────────────────────────────────────┐
│                   pethealth-web (单体)                     │
│                   Spring Boot · :8080                       │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────────┐  │
│  │UserCtrl │ │PetCtrl  │ │PostCtrl │ │HealthRecordCtrl │  │
│  │用户/帖子 │ │宠物档案 │ │社区帖子 │ │健康记录/AI诊断/  │  │
│  │/评论点赞 │ │         │ │(本地)   │ │营养助手(本地聚合)│  │
│  └────┬────┘ └────┬────┘ └─────────┘ └────────┬────────┘  │
│       │           │                           │           │
└───────┼───────────┼───────────────────────────┼───────────┘
        │           │                           │
        ▼           ▼                           ▼
┌──────────┐  ┌─────────────┐  ┌─────────────┐
│  pet-    │  │ health-     │  │ reminder-   │
│  service │  │ record-     │  │ service     │
│  :8081   │  │ service     │  │ :8084       │
│ Dubbo P  │  │ :8086       │  │ Dubbo P +   │
│ MongoDB  │  │ Dubbo P     │  │ 定时调度     │
│ 宠物档案 │  │ Redis 缓存  │  │ 原子抢占     │
└────┬─────┘  │ LLM 调用    │  └──────┬──────┘
     │        └─────┬───────┘         │
     ▼              ▼                 ▼
  MongoDB       Redis 缓存        MongoDB
  主存储         会话/统计聚合      提醒集合（共享 pethealth_web 库）
```

> **说明**：提醒系统**不再使用 RabbitMQ 延迟队列**，改为 `ReminderScheduler` 定时扫描 + `findAndModify` 原子抢占（PENDING→SENT），到期后状态流转并在提醒中心页面展示（应用内触达）。

### 2.2 微服务模块与端口规划

| 模块 | 职责 | 端口 | Dubbo 端口 | 说明 |
|------|------|------|------------|------|
| `pethealth-web` | 主入口 / 前端静态资源 / 社区 + 用户 API | 8080 | — | 单体 Web，启动最方便 |
| `pet-service` | 宠物档案 / 疫苗 / 驱虫记录 | 8081 | 20881 | Dubbo Provider |
| `health-record-service` | 健康记录 / 趋势统计 / AI 诊断 | 8086 | 20885 | Dubbo Provider + LLM |
| `reminder-service` | 提醒调度 / 应用内提醒 | 8084 | 20884 | Dubbo Provider + 定时调度（无 MQ） |

### 2.3 服务间调用关系

```
pethealth-web (8080)
 ├── Dubbo ──► pet-service (20881)         [查询/新增宠物档案]
 ├── Dubbo ──► health-record-service (20885) [健康记录 + AI 诊断/健康报告]
 ├── Dubbo ──► reminder-service (20884)    [设置提醒]
 └── 本地聚合 pet_profiles + health_records（营养助手：RER/MER 计算，无新增集合）

health-record-service (8086)
 └── HTTP ──► DeepSeek API (AI 诊断)

reminder-service (8084)
 ├── 定时扫描 remindAt 到期且 status=PENDING 的提醒 → findAndModify 原子翻转为 SENT（应用内触达）
 └── Dubbo ──► pet-service (查询疫苗到期日)
```

> RabbitMQ 延迟队列 / 邮件推送**已下线**，提醒统一走应用内状态流转。

### 2.4 Dubbo 直连（无注册中心）

本项目为便于本地演示，Dubbo 采用**直连模式**（`registry: N/A`），Consumer 在注解/配置中显式指定 Provider 地址：

- pet-service → `dubbo://localhost:20881/com.pethealth.service.dubbo.PetDubboService`
- health-record-service → `dubbo://localhost:20885/com.pethealth.service.dubbo.HealthRecordDubboService` / `AIDiagnosisDubboService`
- reminder-service → `dubbo://localhost:20884/com.pethealth.service.dubbo.ReminderDubboService`

任何服务都可独立 `mvn spring-boot:run` 启动，无需先启动注册中心。

---

## 3. 项目目录结构

### 3.1 Maven 多模块结构

```
pethealth/                                          ← 项目根目录
├── pom.xml                                         ← 父 POM（Spring Boot Parent 3.2.0）
├── .gitignore
│
├── microservices/                                  ← 微服务父 POM
│   └── pom.xml                                     ← 管理 Dubbo/Nacos/Spring Cloud 版本
│
├── pethealth-web/                                  ← 主 Web 应用（端口 8080）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/pethealth/
│       │   ├── PetHealthWebApplication.java
│       │   ├── config/
│       │   │   ├── SecurityConfig.java
│       │   │   ├── RedisConfig.java
│       │   │   ├── GlobalExceptionHandler.java
│       │   │   └── DataInitializer.java
│       │   ├── controller/
│       │   │   ├── UserController.java
│       │   │   ├── PostController.java
│       │   │   ├── ReplyController.java
│       │   │   ├── LikeController.java
│       │   │   ├── NotificationController.java      ← 站内通知
│       │   │   ├── PetController.java              ← 转发到 pet-service
│       │   │   ├── HealthRecordController.java     ← 转发到 health-record-service
│       │   │   ├── ReminderController.java          ← 提醒（本地 + reminder-service）
│       │   │   ├── NutritionController.java          ← 营养助手（本地聚合档案+体重）
│       │   │   ├── AIDiagnosisController.java       ← 转发到 health-record-service
│       │   │   └── StatisticsController.java
│       │   ├── dto/
│       │   │   └── NutritionReport.java
│       │   ├── entity/
│       │   │   ├── User.java
│       │   │   ├── Post.java
│       │   │   ├── Reply.java
│       │   │   ├── Like.java
│       │   │   └── Notification.java
│       │   ├── exception/
│       │   │   ├── AccessDeniedException.java
│       │   │   ├── ServiceUnavailableException.java
│       │   │   └── UnauthorizedException.java
│       │   ├── interceptor/
│       │   │   ├── AuthInterceptor.java             ← Token 解析 + 写接口登录门槛
│       │   │   └── AuthContext.java
│       │   ├── repository/
│       │   │   ├── UserRepository.java
│       │   │   ├── PostRepository.java
│       │   │   ├── ReplyRepository.java
│       │   │   ├── LikeRepository.java
│       │   │   ├── NotificationRepository.java
│       │   │   ├── PetProfileRepository.java
│       │   │   ├── HealthRecordRepository.java
│       │   │   └── ReminderRepository.java
│       │   ├── service/
│       │   │   ├── UserService.java
│       │   │   ├── AuthService.java                 ← Redis Token 会话（写失败抛 503）
│       │   │   ├── PostService.java
│       │   │   ├── ReplyService.java
│       │   │   ├── LikeService.java                 ← $inc 原子计数 + DuplicateKey 幂等
│       │   │   ├── PostRankService.java             ← Redis Sorted Set 热门榜（Redis 挂降级 DB）
│       │   │   ├── HealthRecordStatsService.java    ← 周/月聚合（键带周期起点，SCAN 失效）
│       │   │   ├── NotificationService.java
│       │   │   ├── NutritionCalculator.java
│       │   │   ├── NutritionService.java
│       │   │   ├── ReminderScheduler.java           ← findAndModify 原子抢占
│       │   │   ├── ReminderService.java
│       │   │   ├── OwnershipGuard.java
│       │   │   └── RateLimitService.java
│       │   └── PetHealthWebApplication.java
│       └── resources/
│           ├── application.yml
│           └── static/
│               ├── index.html
│               ├── script.js
│               └── styles.css
│
├── pethealth-api/                                      ← 共享 API 模块（Dubbo 接口 + 公共实体）
│   ├── pom.xml
│   └── src/main/java/com/pethealth/
│       ├── dto/ApiResponse.java
│       ├── entity/
│       │   ├── PetProfile.java
│       │   ├── HealthRecord.java
│       │   └── Reminder.java
│       └── service/dubbo/                             ← Dubbo 接口（Consumer/Provider 共享同一份）
│           ├── PetDubboService.java
│           ├── HealthRecordDubboService.java
│           ├── AIDiagnosisDubboService.java
│           └── ReminderDubboService.java
│
├── pet-service/                                    ← 宠物档案微服务（端口 8081）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/pethealth/
│       │   ├── PetServiceApplication.java
│       │   ├── entity/
│       │   │   └── PetProfile.java
│       │   ├── repository/
│       │   │   └── PetProfileRepository.java
│       │   ├── dto/
│       │   │   ├── PetProfileDTO.java
│       │   │   ├── VaccineDTO.java
│       │   │   └── DewormingDTO.java
│       │   ├── service/
│       │   │   ├── PetService.java
│       │   │   └── PetDubboServiceImpl.java
│       │   └── config/
│       │       └── PetServiceConfig.java
│       └── resources/
│           └── application.yml
│
├── health-record-service/                          ← 健康记录 + AI 诊断（端口 8086）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/pethealth/
│       │   ├── HealthRecordServiceApplication.java
│       │   ├── config/
│       │   │   ├── RedisConfig.java
│       │   │   ├── CorsConfig.java
│       │   │   └── GlobalExceptionHandler.java
│       │   ├── entity/
│       │   │   └── HealthRecord.java                 ← 实际在 pethealth-api，此处共享
│       │   ├── repository/
│       │   │   └── HealthRecordRepository.java
│       │   ├── service/
│       │   │   ├── HealthRecordService.java
│       │   │   ├── HealthRecordStatsService.java    ← 周/月聚合缓存（键带周期起点）
│       │   │   ├── LLMClient.java                    ← AI 诊断核心
│       │   │   ├── AIDiagnosisService.java
│       │   │   └── HealthRecordDubboServiceImpl.java
│       │   └── controller/
│       │       ├── HealthRecordController.java
│       │       └── AIDiagnosisController.java
│       └── resources/
│           └── application.yml
│
└── reminder-service/                               ← 提醒服务（端口 8084）
    ├── pom.xml
    └── src/main/
        ├── java/com/pethealth/
        │   ├── ReminderServiceApplication.java
        │   ├── config/
        │   │   └── ReminderConfig.java
        │   ├── entity/
        │   │   └── Reminder.java                    ← 实际在 pethealth-api，此处共享
        │   ├── repository/
        │   │   └── ReminderRepository.java
        │   ├── service/
        │   │   ├── ReminderService.java
        │   │   ├── ReminderScheduler.java          ← findAndModify 原子抢占（无 MQ）
        │   │   └── ReminderDubboServiceImpl.java
        │   └── controller/
        │       └── ReminderController.java
        └── resources/
            └── application.yml
```

### 3.2 包命名约定

- groupId：`com.pethealth`
- 主启动类：`XxxServiceApplication.java`，放在包根目录
- Controller 层放在 `controller/`
- Service 层放在 `service/`
- Dubbo 服务接口（Consumer 侧）放在 `service/dubbo/` 或独立 `service/` 下
- Entity 放在 `entity/`
- Repository 放在 `repository/`
- DTO 放在 `dto/`

---

## 4. 数据库设计（MongoDB + Redis）

### 4.1 MongoDB 数据库划分

> 所有服务统一使用 `pethealth_web` 库：各服务通过不同 collection 隔离数据，
> 微服务的 Dubbo 路径与 pethealth-web 的降级本地 Repository 读写同一集合，保证数据一致。

| 数据库 | 所属服务 | Collections |
|--------|----------|-------------|
| `pethealth_web` | 全部服务（pethealth-web、pet-service、health-record-service、reminder-service） | users, posts, replies, likes, notifications, pet_profiles, health_records, reminders |

> 说明：营养助手**不新增集合**，直接聚合读取 `pet_profiles`（绝育等档案字段）与 `health_records`（体重序列）计算。

### 4.2 pet_profiles — 宠物档案（核心！体现 MongoDB 灵活 Schema）

```json
{
  "_id": "ObjectId(...)",
  "ownerId": "用户ID",
  "ownerName": "用户昵称",
  "name": "豆豆",
  "species": "DOG",                   // DOG / CAT / RABBIT / HAMSTER / BIRD / OTHER
  "breed": "柯基",
  "gender": "MALE",                    // MALE / FEMALE / UNKNOWN
  "neutered": false,                   // 是否绝育：true / false / null（营养助手系数计算用）
  "birthday": "2022-03-15",
  "adoptedAt": "2022-05-01",
  "avatar": "/pets/avatar/doudou.jpg",
  "description": "活泼好动的小柯基",

  // === 疫苗记录（嵌套数组） ===
  "vaccines": [
    {
      "_id": "ObjectId(...)",
      "name": "狂犬疫苗",
      "vaccinatedAt": "2025-05-01",
      "nextDueAt": "2026-05-01",
      "vetClinic": "爱宠宠物医院",
      "notes": "无不良反应"
    }
  ],

  // === 驱虫记录（嵌套数组） ===
  "dewormings": [
    {
      "_id": "ObjectId(...)",
      "type": "INTERNAL",              // INTERNAL 体内 / EXTERNAL 体外
      "medicine": "拜宠清",
      "dewormedAt": "2026-04-01",
      "nextDueAt": "2026-07-01",
      "notes": null
    }
  ],

  // === 体检记录（嵌套数组） ===
  "checkups": [
    {
      "_id": "ObjectId(...)",
      "checkedAt": "2026-03-15",
      "vetId": "ObjectId(...)",
      "vetName": "李医生",
      "clinic": "爱宠宠物医院",
      "result": "各项指标正常",
      "abnormalItems": null
    }
  ],

  // === 就医记录 ===
  "medicalVisits": [
    {
      "_id": "ObjectId(...)",
      "visitedAt": "2025-11-20",
      "reason": "呕吐、食欲不振",
      "diagnosis": "肠胃炎",
      "treatment": "输液 + 消炎药",
      "vetId": "ObjectId(...)"
    }
  ],

  // === 可扩展动态字段（关键体现 MongoDB 灵活性） ===
  "additionalInfo": {
    "microchipId": "900115004523345",
    "insuranceCompany": "平安",
    "insuranceExpire": "2026-12-31",
    "allergies": ["鸡肉", "牛肉"]
  },

  "createdAt": "2025-05-01T10:00:00Z",
  "updatedAt": "2026-04-01T14:30:00Z"
}
```

**索引设计：**
- `{ ownerId: 1 }` — 查询某用户的所有宠物（unique 不强制，一对多）
- `{ ownerId: 1, species: 1 }` — 按物种筛选
- `{ vaccines.nextDueAt: 1 }` — TTL 扫描疫苗到期提醒
- `{ dewormings.nextDueAt: 1 }` — 驱虫到期扫描

### 4.3 health_records — 健康追踪记录

```json
{
  "_id": "ObjectId(...)",
  "petId": "ObjectId(...)",
  "ownerId": "用户ID",
  "recordType": "WEIGHT",              // WEIGHT / TEMPERATURE / DIET / EXERCISE / SYMPTOM / MEDICATION
  "value": {
    "weight": 11.5,                    // kg
    "temperature": 38.8,               // ℃  (猫正常 38-39.2)
    "foodType": "皇家成犬粮",
    "foodAmount": 250,                 // g
    "exerciseMinutes": 45,
    "symptom": "打喷嚏，有清鼻涕",
    "medicine": "阿莫西林 0.5片/次"
  },
  "recordedAt": "2026-05-27T08:00:00Z",
  "notes": null,
  "createdAt": "2026-05-27T08:00:00Z"
}
```

**索引：**
- `{ petId: 1, recordedAt: -1 }` — 查询趋势
- `{ ownerId: 1, recordType: 1, recordedAt: -1 }` — 按类型查询

### 4.4 users — 用户表

```json
{
  "_id": "ObjectId(...)",
  "username": "xiaoming",
  "password": "$2a$10$...",           // BCrypt 加密
  "email": "xiaoming@example.com",
  "avatar": "/avatars/xiaoming.jpg",
  "phone": "138****1234",
  "preferences": {
    "reminderEnabled": true,
    "emailNotify": true,
    "pushNotify": false
  },
  "createdAt": "2025-05-01T10:00:00Z",
  "updatedAt": "2025-05-01T10:00:00Z"
}
```

### 4.5 posts — 社区帖子

```json
{
  "_id": "ObjectId(...)",
  "title": "我家猫突然不吃东西了怎么办？",
  "content": "昨天还好好的，今天就不吃猫粮了...",
  "authorId": "用户ID",
  "authorName": "小明",
  "category": "HEALTH",                // HEALTH / FEEDING / TRAINING / DAILY / EMERGENCY
  "tags": ["不吃东西", "精神萎靡", "6个月"],
  "petSpecies": "CAT",                 // 关联宠物类型
  "viewCount": 128,
  "likeCount": 12,
  "replyCount": 8,
  "status": "published",              // published（已发布）；删除为物理删除
  "createdAt": "2026-05-27T09:30:00Z",
  "updatedAt": "2026-05-27T09:30:00Z"
}
```

**索引：**
- `{ authorId: 1 }`
- `{ category: 1, createdAt: -1 }`
- `{ status: 1 }`（值为 `published`）
- `{ tags: 1 }`（多键索引）

### 4.6 replies — 帖子回复（扁平结构，简单版本）

```json
{
  "_id": "ObjectId(...)",
  "postId": "帖子ID",
  "content": "建议先观察精神状态，测一下体温...",
  "authorId": "用户ID",
  "authorName": "老铲屎官",
  "likeCount": 5,
  "isAccepted": false,
  "createdAt": "2026-05-27T10:00:00Z",
  "updatedAt": "2026-05-27T10:00:00Z"
}
```

### 4.7 comments — 嵌套评论（对帖子/回复的评论）

```json
{
  "_id": "ObjectId(...)",
  "targetType": "post",                // post / reply
  "targetId": "目标ID",
  "parentId": "父评论ID（null 为顶级）",
  "path": "0/5/12",                    // 路径字符串，支持树形遍历
  "content": "说得有道理！",
  "authorId": "用户ID",
  "authorName": "路人甲",
  "likeCount": 0,
  "createdAt": "2026-05-27T10:30:00Z",
  "updatedAt": "2026-05-27T10:30:00Z"
}
```

### 4.8 likes — 点赞记录（防重复）

```json
{
  "_id": "ObjectId(...)",
  "userId": "用户ID",
  "targetType": "post",                // post / reply / comment
  "targetId": "目标ID",
  "createdAt": "2026-05-27T11:00:00Z"
}
```

**索引：**
- `{ userId: 1, targetType: 1, targetId: 1 }` — unique 防重复

### 4.9 reminders — 提醒表

```json
{
  "_id": "ObjectId(...)",
  "ownerId": "用户ID",
  "petId": "宠物ID",
  "type": "VACCINE",                   // VACCINE / DEWORMING / CHECKUP / MEDICATION / CUSTOM
  "title": "狂犬疫苗到期",
  "description": "豆豆的狂犬疫苗将于明天到期，请尽快续种",
  "remindAt": "2026-05-28T09:00:00Z",
  "advanceDays": 7,                    // 提前几天提醒
  "status": "PENDING",                 // PENDING / SENT / ACKNOWLEDGED / CANCELLED
  "notifyMethod": ["INAPP"],           // 应用内提醒（项目内无邮件/短信通道）
  "sourceRef": {                       // 关联来源（如疫苗记录）
    "collection": "pet_profiles",
    "vaccineId": "ObjectId(...)"
  },
  "createdAt": "2026-05-01T10:00:00Z",
  "updatedAt": "2026-05-28T09:00:00Z"
}
```

### 4.10 营养助手计算（无新增集合）

营养助手不存储业务数据，运行时由 pethealth-web **本地聚合**读取 `pet_profiles`（物种 / 生日 / 绝育）与 `health_records`（最新体重 + 体重序列），按通用营养标准实时计算（参考 NRC 2006 / WSAVA）：

| 项 | 公式 / 规则 |
|----|------------|
| RER 静息能量需求 | RER = 70 × 体重(kg)^0.75（kcal/天） |
| MER 每日能量需求 | MER = RER × 生命阶段系数（物种 × 年龄 × 绝育；示例：成年犬已绝育 1.4 / 未绝育 1.6，成年猫已绝育 1.2 / 未绝育 1.4，幼犬 <4 月 3.0 / 4-12 月 2.0，幼猫 <6 月 2.5 / 6-12 月 1.4） |
| 每日喂食克数 | 喂食量(g) = MER ÷ 主粮热量密度（kcal/100g，前端可选 340-420） |
| 体重管理 | BCS（1-9）≥7 时目标热量 = MER × 0.8（减重）；≤4 时 = MER × 1.15（增重） |

- 仅支持猫 / 犬的精确估算；其他物种返回"请以兽医建议为准"
- 计算逻辑封装为纯静态类 `NutritionCalculator`，并配 JUnit 单元测试（公式、系数表、边界）
- 页面：宠物选择 → RER / MER / 每日喂食克数 + 计算过程说明 → BCS 体重管理 → 体重趋势图（ECharts）

### 4.11 Redis 键设计

| Key | 类型 | 用途 | TTL |
|-----|------|------|-----|
| `pethealth:auth:token:{token}` | Hash | 登录会话（userId/username/loginAt），写失败抛 503 | 7 天 |
| `pethealth:post:rank` | Sorted Set | 社区热门帖子榜，score = like×3 + reply×5 + view×1 | 永久，实时更新 |
| `pethealth:post:rank:cache` | String (JSON) | 热门榜查询结果缓存（Redis 不可用时降级查 DB） | 1 分钟 |
| `pethealth:statistics:home` | String (JSON) | 首页聚合统计缓存 | 短 TTL |
| `pethealth:pet:{petId}:weekly-stats:{本周一日期}` | Hash | 某宠物本周健康数据聚合（键带周期起点防串期） | 7 天 |
| `pethealth:pet:{petId}:monthly-stats:{yyyy-MM}` | Hash | 某月聚合（键带月份起点） | 30 天 |

> **周期起点防串期**：周/月统计键末尾带周期起点（本周一日期 / `yyyy-MM`），跨周/跨月后自然读写新键，旧周期靠 TTL 自然过期。失效时按 `petId` 前缀 SCAN 渐进式删除该宠物所有周期的键，宠物换绑时旧/新 petId 双失效。

**热门榜分数算法（算法设计简单直接，兼顾性能与可维护性）：**
```
hotScore = likeCount × 3.0 + replyCount × 5.0 + viewCount × 1.0
```

---

## 5. 核心实体与 DTO 定义

### 5.1 PetProfile 实体（体现 MongoDB 灵活 Schema 的关键）

```java
package com.pethealth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "pet_profiles")
public class PetProfile {

    @Id
    private String id;

    @Indexed
    private String ownerId;

    private String ownerName;
    private String name;
    private String species;         // DOG / CAT / RABBIT / HAMSTER / BIRD / OTHER
    private String breed;
    private String gender;          // MALE / FEMALE / UNKNOWN
    private Boolean neutered;       // 是否绝育：true / false / null（营养助手系数计算用）
    private LocalDate birthday;
    private LocalDate adoptedAt;
    private String avatar;
    private String description;

    // === 嵌套文档：疫苗记录 ===
    private List<Vaccine> vaccines;

    // === 嵌套文档：驱虫记录 ===
    private List<Deworming> dewormings;

    // === 嵌套文档：体检记录 ===
    private List<Checkup> checkups;

    // === 嵌套文档：就医记录 ===
    private List<MedicalVisit> medicalVisits;

    // === 动态扩展字段：Map 形式，适配各种个性化信息 ===
    private Map<String, Object> additionalInfo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- 内嵌类 ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vaccine {
        private String id;
        private String name;              // 狂犬疫苗 / 猫三联 / 狗四联 ...
        private LocalDate vaccinatedAt;
        @Indexed
        private LocalDate nextDueAt;
        private String vetClinic;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Deworming {
        private String id;
        private String type;              // INTERNAL / EXTERNAL
        private String medicine;
        private LocalDate dewormedAt;
        @Indexed
        private LocalDate nextDueAt;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Checkup {
        private String id;
        private LocalDate checkedAt;
        private String vetId;
        private String vetName;
        private String clinic;
        private String result;
        private List<String> abnormalItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MedicalVisit {
        private String id;
        private LocalDate visitedAt;
        private String reason;
        private String diagnosis;
        private String treatment;
        private String vetId;
    }
}
```

### 5.2 HealthRecord 实体

```java
package com.pethealth.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@Document(collection = "health_records")
public class HealthRecord {

    @Id
    private String id;

    private String petId;
    private String ownerId;
    private String recordType;         // WEIGHT / TEMPERATURE / DIET / EXERCISE / SYMPTOM / MEDICATION

    // 使用 Map 存储灵活的健康指标（体重 kg / 体温 ℃ / 食量 g / 运动分钟 ...）
    private java.util.Map<String, Object> value;

    private LocalDateTime recordedAt;
    private String notes;
    private LocalDateTime createdAt;
}
```

### 5.3 Post / Reply / Like 实体

以下三个实体结构简洁，与社区常见设计保持一致。collection 名分别为 `posts` / `replies` / `likes`。

> **嵌套评论（Comment）当前未实现**：项目暂未提供 Comment 实体、Controller 与 Repository，回复为扁平结构（`Reply`），不含对回复的二级评论。

```java
// Post（对应原 Question）
@Document(collection = "posts")
public class Post {
    @Id private String id;
    private String title;
    private String content;
    @Indexed private String authorId;
    private String authorName;
    private String category;            // HEALTH / FEEDING / TRAINING / DAILY / EMERGENCY
    private List<String> tags;
    private String petSpecies;
    private Integer viewCount;
    private Integer likeCount;
    private Integer replyCount;
    @Indexed private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// Reply（对应原 Answer）
@Document(collection = "replies")
public class Reply {
    @Id private String id;
    @Indexed private String postId;
    private String content;
    private String authorId;
    private String authorName;
    private Integer likeCount;
    private Boolean isAccepted;
    private LocalDateTime createdAt;
}

// Like — userId + targetType + targetId 唯一索引防重复点赞（DuplicateKey 幂等）
```

### 5.4 通用 ApiResponse

```java
package com.pethealth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200).message("success").data(data).timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .code(200).message(message).data(data).timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .code(400).message(message).data(null).timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code).message(message).data(null).timestamp(System.currentTimeMillis())
                .build();
    }
}
```

---

## 5.5 辅助服务与配置类完整代码（可直接复制粘贴）

> 本节补全所有零散提到但没有给出完整代码的类。每个类都标注了**文件位置**和**所属模块**。

### 5.5.1 SecurityConfig（Spring Security — BCrypt + 放开所有接口）

**文件位置**: `pethealth-web/src/main/java/com/pethealth/config/SecurityConfig.java`

> **实际认证机制**：Spring Security 仅提供 `BCryptPasswordEncoder` 并放开所有接口（`permitAll`），**真正的登录态校验由 `AuthInterceptor` 完成**——解析 HttpOnly Cookie / `Authorization: Bearer` 中的 Token，查 Redis 取 userId，写接口（POST/PUT/DELETE）无有效 Token 返回 401。Token 写 Redis 失败时抛 `ServiceUnavailableException` → 503。

```java
package com.pethealth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置
 * 当前阶段为了简化开发，所有接口放开访问（仅用 BCrypt 加密密码存储）
 * 生产环境可以在此处加上 JWT / Session 认证
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())                       // 禁用 CSRF（前后端分离）
            .cors(cors -> cors.configure(http))                // 允许跨域
            .authorizeRequests(authorize -> authorize
                // 静态资源
                .requestMatchers("/", "/index.html", "/styles.css", "/script.js").permitAll()
                // 所有 API 暂时放开
                .requestMatchers("/api/**").permitAll()
                // Actuator
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().permitAll()
            )
            .formLogin(form -> form.disable())                  // 禁用表单登录
            .httpBasic(basic -> basic.disable())                // 禁用 HTTP Basic
            .logout(logout -> logout.disable());                // 禁用默认登出

        return http.build();
    }
}
```

### 5.5.2 RedisConfig（RedisTemplate 序列化配置）

**文件位置**: `pethealth-web/src/main/java/com/pethealth/config/RedisConfig.java`
`health-record-service` 和 `pethealth-web` 都需要这个类，放在各自的 config 包下即可。

```java
package com.pethealth.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 * 让 RedisTemplate 正确地序列化/反序列化 POJO
 * Key 用 String 序列化，Value 用 JSON 序列化
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 使用 String 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 使用 JSON 序列化
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(om);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
```

### 5.5.3 GlobalExceptionHandler（统一异常处理）

**文件位置**: `pethealth-web/src/main/java/com/pethealth/config/GlobalExceptionHandler.java`

> **实际实现的异常映射**（比下文代码更全）：`MethodArgumentNotValidException`→400、`ConstraintViolationException`→400、`IllegalArgumentException`→400、`ResourceNotFoundException`→404、`UnauthorizedException`→401、`AccessDeniedException`→403、`ServiceUnavailableException`→503（Redis 写 Token 失败等）、兜底 `Exception`→500（不透出内部信息）。

```java
package com.pethealth.config;

import com.pethealth.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 把各种异常统一包装成 ApiResponse 返回前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.badRequest().body(ApiResponse.error(400, "参数错误: " + msg));
    }

    /** 非法参数 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
    }

    /** AI 服务异常 */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntime(RuntimeException e) {
        log.error("运行时异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, e.getMessage()));
    }

    /** 兜底 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleOther(Exception e) {
        log.error("未知异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, "服务器内部错误"));
    }
}
```

### 5.5.4 DataInitializer（启动时自动插入 Demo 数据）

**文件位置**: `pethealth-web/src/main/java/com/pethealth/config/DataInitializer.java`

> **实际初始化内容**（dev 环境生效）：`admin/admin123` + `demo/123456` 两个用户，以及 2 只宠物（猫/狗）、若干社区帖子与回复、点赞、健康记录、提醒，便于直接登录查看全量功能。下文代码为简化版（仅 demo + 帖子）。

```java
package com.pethealth.config;

import com.pethealth.entity.*;
import com.pethealth.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用启动时自动检查数据库，如果是空的就插入 Demo 数据
 * 方便开发调试 —— 打开浏览器就能看到内容
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("数据库已有数据，跳过初始化");
            return;
        }

        log.info("开始初始化 Demo 数据...");

        // 1. 创建用户
        User u1 = User.builder()
                .username("demo")
                .password(passwordEncoder.encode("123456"))
                .email("demo@pethealth.com")
                .avatar("")
                .createdAt(System.currentTimeMillis())
                .updatedAt(System.currentTimeMillis())
                .build();
        u1 = userRepository.save(u1);
        log.info("创建用户 demo/123456");

        // 2. 创建几个社区帖子
        Post p1 = Post.builder()
                .title("我家猫突然不吃东西了怎么办？")
                .content("昨天还好好的，今天就不吃猫粮了，精神也不太好，偶尔还打喷嚏。有没有铲屎官遇到过类似情况？")
                .authorId(u1.getId())
                .authorName(u1.getUsername())
                .category("HEALTH")
                .tags(List.of("不吃东西", "精神萎靡", "不吃猫粮"))
                .petSpecies("CAT")
                .viewCount(128).likeCount(12).replyCount(8)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now().minusHours(2))
                .updatedAt(LocalDateTime.now().minusHours(2))
                .build();
        p1 = postRepository.save(p1);

        Post p2 = Post.builder()
                .title("柯基掉毛严重怎么办？")
                .content("我家柯基最近掉毛特别严重，沙发上到处都是，有没有好的方法？")
                .authorId(u1.getId())
                .authorName(u1.getUsername())
                .category("DAILY")
                .tags(List.of("掉毛", "柯基"))
                .petSpecies("DOG")
                .viewCount(89).likeCount(7).replyCount(5)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now().minusHours(6))
                .updatedAt(LocalDateTime.now().minusHours(6))
                .build();
        p2 = postRepository.save(p2);

        Post p3 = Post.builder()
                .title("新手养猫必看清单")
                .content("刚养了一只英短，整理了一些必买清单和注意事项，希望对大家有用！")
                .authorId(u1.getId())
                .authorName(u1.getUsername())
                .category("FEEDING")
                .tags(List.of("新手", "养猫", "英短"))
                .petSpecies("CAT")
                .viewCount(520).likeCount(34).replyCount(15)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
        postRepository.save(p3);

        // 3. 创建几条回复
        Reply r1 = Reply.builder()
                .postId(p1.getId())
                .content("建议先观察精神状态，测一下体温（正常猫 38-39.2℃）。如果持续不吃东西超过 24 小时，建议去医院查一下。")
                .authorId(u1.getId()).authorName("老铲屎官")
                .likeCount(5).isAccepted(true)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
        replyRepository.save(r1);

        Reply r2 = Reply.builder()
                .postId(p1.getId())
                .content("我家猫上次也这样，后来发现是换了猫粮它不爱吃...你最近换粮了吗？")
                .authorId(u1.getId()).authorName("路人甲")
                .likeCount(3)
                .createdAt(LocalDateTime.now().minusMinutes(30))
                .build();
        replyRepository.save(r2);

        log.info("Demo 数据初始化完成！可以用 demo / 123456 登录");
    }
}
```

### 5.5.5 PostRankService（Redis Sorted Set 热门榜 — 完整实现）

**文件位置**: `pethealth-web/src/main/java/com/pethealth/service/PostRankService.java`

> **实际实现增加 Redis 不可用时的降级**：`getTopPosts` / `updateScore` / `syncFromDatabase` 全部包裹 try/catch，Redis 异常时读路径回源数据库（取最近发布的候选帖按热度分数内存排序），写路径静默跳过，待全量同步任务重建排行榜。下文代码为基础版（不含降级）。

```java
package com.pethealth.service;

import com.pethealth.entity.Post;
import com.pethealth.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 帖子热门排行榜服务
 * 核心设计：
 * 1. 热度分数 = 点赞×3 + 回复×5 + 浏览×1
 * 2. Redis Sorted Set 存帖子ID和分数，实时更新
 * 3. 排行榜查询结果再缓存 1 分钟（避免频繁查数据库）
 * 4. 每 5 分钟全量同步一次，防止实时更新有遗漏
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostRankService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PostRepository postRepository;

    private static final String RANK_KEY        = "pethealth:post:rank";       // Sorted Set
    private static final String RANK_CACHE_KEY  = "pethealth:post:rank:cache"; // 结果缓存
    private static final long   CACHE_TTL_MIN   = 1;                          // 缓存 1 分钟

    /**
     * 更新单个帖子的热度分数（点赞/回复/浏览变化时调用）
     */
    public void updateScore(String postId) {
        Optional<Post> opt = postRepository.findById(postId);
        if (opt.isEmpty()) return;

        Post post = opt.get();
        double score = calculateHotScore(post);

        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(RANK_KEY, postId, score);

        // 清掉排行榜缓存，下次查询时重新计算
        redisTemplate.delete(RANK_CACHE_KEY);
        log.debug("更新帖子 {} 热度为 {}", postId, score);
    }

    /**
     * 获取 Top N 热门帖子（先查缓存，缓存 miss 再查 Sorted Set + 数据库）
     */
    @SuppressWarnings("unchecked")
    public List<Post> getTopPosts(int limit) {
        // 1. 先查缓存
        Object cached = redisTemplate.opsForValue().get(RANK_CACHE_KEY);
        if (cached instanceof List) {
            log.debug("从缓存获取热门排行榜");
            return (List<Post>) cached;
        }

        // 2. 从 Sorted Set 取 Top N 的帖子 ID
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Set<Object> postIds = zSetOps.reverseRange(RANK_KEY, 0, limit - 1);

        // 3. 批量查数据库
        List<Post> result = new ArrayList<>();
        if (postIds != null) {
            List<String> idList = postIds.stream().map(Object::toString).collect(Collectors.toList());
            // 保持 Sorted Set 的顺序去查（MongoDB 没有原生按 in 查询顺序返回）
            Map<String, Post> map = new HashMap<>();
            postRepository.findAllById(idList).forEach(p -> map.put(p.getId(), p));
            for (String id : idList) {
                if (map.containsKey(id)) result.add(map.get(id));
            }
        }

        // 4. 缓存 1 分钟
        redisTemplate.opsForValue().set(RANK_CACHE_KEY, result, CACHE_TTL_MIN, TimeUnit.MINUTES);
        return result;
    }

    /**
     * 定时全量同步（每 5 分钟）
     * 兜底：防止实时更新有遗漏导致排行榜不准
     */
    @Scheduled(fixedRate = 300_000)
    public void syncFromDatabase() {
        log.info("开始全量同步热门排行榜...");
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        redisTemplate.delete(RANK_KEY);

        postRepository.findAll().forEach(p -> {
            double score = calculateHotScore(p);
            zSetOps.add(RANK_KEY, p.getId(), score);
        });

        redisTemplate.delete(RANK_CACHE_KEY);
        log.info("热门排行榜同步完成，共 {} 条", zSetOps.size(RANK_KEY));
    }

    /**
     * 计算热度分数（点赞×3 + 回复×5 + 浏览×1）
     */
    private double calculateHotScore(Post p) {
        int view  = p.getViewCount()  != null ? p.getViewCount()  : 0;
        int like  = p.getLikeCount()  != null ? p.getLikeCount()  : 0;
        int reply = p.getReplyCount() != null ? p.getReplyCount() : 0;
        return like * 3.0 + reply * 5.0 + view * 1.0;
    }

    /**
     * 帖子被删除时从排行榜移除
     */
    public void removePost(String postId) {
        redisTemplate.opsForZSet().remove(RANK_KEY, postId);
        redisTemplate.delete(RANK_CACHE_KEY);
    }
}
```

> 记得在 `PetHealthApplication` 主类上加 `@EnableScheduling`，否则 `@Scheduled` 定时任务不会生效！

### 5.5.6 EmailNotifyService（邮件推送 — SMTP 实现）

> **当前项目状态：邮件推送已下线。** 提醒改为**应用内触达**（提醒中心状态流转），`notifyMethod` 固定为 `INAPP`，不配置 SMTP。以下为历史实现 / 可选扩展参考，生产无需复制。

**文件位置（历史）**: `reminder-service/src/main/java/com/pethealth/service/EmailNotifyService.java`

```java
package com.pethealth.service;

import com.pethealth.entity.Reminder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;

/**
 * 邮件通知服务
 * 使用 JavaMailSender 发送提醒邮件
 * 配置示例见 11.4 节 reminder-service 的 application.yml
 *
 * 发件箱推荐：
 * - QQ 邮箱: smtp.qq.com:465（SSL），需要在 QQ 邮箱设置里开启 SMTP 并获取授权码
 * - 163 邮箱: smtp.163.com:465
 */
@Slf4j
@Service
public class EmailNotifyService {

    @Value("${mail.smtp-host:smtp.example.com}")
    private String smtpHost;

    @Value("${mail.smtp-port:465}")
    private String smtpPort;

    @Value("${mail.smtp-user:}")
    private String smtpUser;

    @Value("${mail.smtp-password:}")
    private String smtpPassword;

    @Value("${mail.from:pethealth@example.com}")
    private String fromAddress;

    /**
     * 发送提醒邮件
     */
    public void send(Reminder reminder) {
        if (smtpUser == null || smtpUser.isEmpty()) {
            log.warn("SMTP 未配置，跳过邮件发送（提醒标题: {}）", reminder.getTitle());
            return;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpPassword);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(reminder.getEmail()));
            message.setSubject("🐾 PetHealth 提醒：" + reminder.getTitle());
            message.setText(buildEmailBody(reminder), "UTF-8");
            message.saveChanges();

            Transport.send(message);
            log.info("提醒邮件发送成功 -> {}", reminder.getEmail());
        } catch (MessagingException e) {
            log.error("邮件发送失败", e);
        }
    }

    /**
     * 发送提醒邮件（直接传入收件人和模板文本）
     */
    public void send(String toEmail, String subject, String plainBody) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpPassword);
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(plainBody, "UTF-8");
            message.saveChanges();
            Transport.send(message);
        } catch (MessagingException e) {
            log.error("邮件发送失败", e);
        }
    }

    private String buildEmailBody(Reminder reminder) {
        return String.format(
                "亲爱的铲屎官：\n\n" +
                "您的宠物【%s】有一条健康提醒：\n\n" +
                "📌 %s\n" +
                "📝 %s\n" +
                "⏰ 提醒时间：%s\n\n" +
                "建议尽快处理哦！\n\n" +
                "—— PetHealth 宠物健康管家",
                reminder.getPetName(),
                reminder.getTitle(),
                reminder.getDescription() != null ? reminder.getDescription() : "",
                reminder.getRemindAt()
        );
    }
}
```

### 5.5.7 ReminderScheduler（定时扫描 + 原子抢占到期提醒）

**文件位置**: `pethealth-web/src/main/java/com/pethealth/service/ReminderScheduler.java`

```java
package com.pethealth.service;

import com.pethealth.entity.Reminder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 提醒到期调度器
 * 每分钟扫描 remindAt <= now && status=PENDING 的提醒，到期即标记 SENT。
 * <p>
 * 发送语义（原子抢占）：用 findAndModify 把 status 从 PENDING 原子翻转为 SENT，
 * 抢占条件里带 status=PENDING —— 多实例并发扫描时同一条提醒只会被抢占一次，
 * 不存在"先查出来再改状态"的竞态窗口，天然防止重复发送。
 * 每抢占一条都重新回查 DB（循环 findAndModify），直到无到期提醒为止。
 * <p>
 * 个人项目采用站内提醒：到期流转状态后，提醒中心页面即展示"已发送"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderScheduler {

    private final MongoTemplate mongoTemplate;

    @Scheduled(fixedDelayString = "${reminder.scheduler-interval-seconds:60}000")
    public void scanAndSend() {
        LocalDateTime now = LocalDateTime.now();
        int sent = 0;
        while (true) {
            // 原子抢占一条到期提醒（仅 status 仍为 PENDING 的会命中）
            Query query = new Query(Criteria.where("status").is("PENDING")
                    .and("remindAt").lte(now));
            Update update = new Update()
                    .set("status", "SENT")
                    .set("updatedAt", LocalDateTime.now());
            Reminder claimed = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().returnNew(false), Reminder.class);

            if (claimed == null) break;
            sent++;
            log.info("到期提醒已发送: id={}, title={}, pet={}",
                    claimed.getId(), claimed.getTitle(), claimed.getPetName());
        }

        if (sent == 0) {
            log.debug("无到期提醒");
        } else {
            log.info("本轮共发送 {} 条到期提醒", sent);
        }
    }
}
```

> 已移除 `EmailNotifyService` 与 RabbitMQ 延迟消息：提醒统一走应用内状态流转（PENDING → SENT → ACKNOWLEDGED / CANCELLED）。记得 `pethealth-web` 主启动类加 `@EnableScheduling`。

### 5.5.8 DelayMessageProducer / DelayMessageConsumer（已移除）

> **RabbitMQ 延迟消息方案已下线**：项目不再依赖 `spring-boot-starter-amqp`，提醒改为 `ReminderScheduler` 定时扫描 + `findAndModify` 原子抢占。以下代码仅作历史参考，**当前代码库中不存在这两个类**，也无需在 pom.xml 引入 AMQP 依赖。

**文件位置**: `reminder-service/src/main/java/com/pethealth/service/DelayMessageProducer.java`

```java
package com.pethealth.service;

import com.pethealth.config.RabbitMQConfig;
import com.pethealth.entity.Reminder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 延迟消息生产者
 * 把 Reminder 封装成带 delay 的消息投递到"延迟队列"
 * 消息到达延迟时间后会自动转到"真正消费的队列"
 *
 * 原理参考第 8.2 节：TTL + Dead Letter Exchange
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DelayMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发送一条延迟提醒消息
     * @param reminder 完整的提醒实体（会序列化成 JSON）
     */
    public void sendDelayedReminder(Reminder reminder) {
        long delayMs = ChronoUnit.MILLIS.between(LocalDateTime.now(), reminder.getRemindAt());
        delayMs = Math.max(60_000L, delayMs); // 最小延迟 1 分钟，防止负数

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
        } catch (JsonProcessingException e) {
            log.error("序列化 Reminder 失败", e);
        }
    }
}
```

**文件位置**: `reminder-service/src/main/java/com/pethealth/service/DelayMessageConsumer.java`

```java
package com.pethealth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pethealth.config.RabbitMQConfig;
import com.pethealth.entity.Reminder;
import com.pethealth.repository.ReminderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * 延迟消息消费者
 * 监听真正的提醒队列（死信队列），收到消息后发送邮件 + 更新状态
 *
 * 注意：这个队列是 TTL 到期后的消息自动转发过来的
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DelayMessageConsumer {

    private final ObjectMapper objectMapper;
    private final ReminderRepository reminderRepository;
    private final EmailNotifyService emailNotifyService;

    @RabbitListener(queues = RabbitMQConfig.REMINDER_QUEUE)
    public void onReminder(String payload) {
        try {
            Reminder reminder = objectMapper.readValue(payload, Reminder.class);
            log.info("收到到期提醒，id={}, title={}", reminder.getId(), reminder.getTitle());

            // 1. 发送邮件
            emailNotifyService.send(reminder);

            // 2. 更新状态
            reminder.setStatus("SENT");
            reminderRepository.save(reminder);
        } catch (Exception e) {
            log.error("处理提醒消息失败", e);
        }
    }
}
```

> **Repository 补充**：上面代码用到了 `ReminderRepository.findPendingBefore(LocalDateTime)`，需要在 ReminderRepository 里加：
> ```java
> List<Reminder> findByStatusAndRemindAtBefore(String status, LocalDateTime time);
> ```

---

## 6. REST API 接口文档

> 所有接口统一返回 `ApiResponse<T>` 格式，默认响应码 200 表示成功。端口 8080。

### 6.1 用户模块

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/users/register` | 用户注册（BCrypt 加密，用户名/邮箱唯一） |
| POST | `/api/users/login` | 用户登录，通过 `Set-Cookie` 下发 HttpOnly Token（响应体仅返回用户信息） |
| POST | `/api/users/logout` | 登出（删除 Redis Token + 清除 Cookie） |
| GET  | `/api/users/me` | 当前登录用户（基于 Token） |
| GET  | `/api/users/{id}` | 查询用户信息 |
| PUT  | `/api/users/{id}` | 更新用户资料（仅本人） |
| POST | `/api/users/avatar` | 上传头像（multipart/form-data，字段 `file`） |

> **认证机制**：Token 存 Redis（`pethealth:auth:token:{token}` Hash，TTL 7 天），通过 HttpOnly Cookie `PETHEALTH_TOKEN` 下发；写接口（POST/PUT/DELETE）必须携带有效 Token，否则 401；Redis 写 Token 失败返回 503。

**注册请求体：**
```json
{ "username": "xiaoming", "password": "123456", "email": "xiaoming@example.com" }
```

### 6.2 宠物档案（pet-service 转发）

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/pets` | 创建宠物档案 |
| GET  | `/api/pets` | 查询当前用户所有宠物 |
| GET  | `/api/pets/{id}` | 查询单只宠物详情 |
| PUT  | `/api/pets/{id}` | 更新宠物基本信息（含嵌套的疫苗/驱虫/体检/就医数组） |
| DELETE | `/api/pets/{id}` | 删除宠物（物理删除，并清理其统计缓存） |

> 疫苗 / 驱虫 / 体检 / 就医记录作为 `PetProfile` 的嵌套数组，通过 `PUT /api/pets/{id}` 整体更新，无独立子路由。

**创建宠物请求体：**
```json
{
  "name": "豆豆",
  "species": "DOG",
  "breed": "柯基",
  "gender": "MALE",
  "birthday": "2022-03-15",
  "description": "活泼好动的小柯基",
  "additionalInfo": { "microchipId": "900115004523345" }
}
```

### 6.3 健康记录（health-record-service 转发）

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/health-records` | 新增健康记录 |
| GET  | `/api/health-records/pet/{petId}` | 查询某宠物的所有记录 |
| GET  | `/api/health-records` | 按 ownerId 查询（可选） |
| GET  | `/api/health-records/{id}` | 查询单条记录 |
| GET  | `/api/health-records/pet/{petId}/trends?period=weekly\|monthly` | 周/月聚合统计（Redis 缓存，键带周期起点） |
| PUT  | `/api/health-records/{id}` | 更新记录（换宠物时旧/新 petId 缓存双失效） |
| DELETE | `/api/health-records/{id}` | 删除记录 |

**新增健康记录请求体：**
```json
{
  "petId": "xxx",
  "recordType": "WEIGHT",
  "value": { "weight": 11.5 },
  "recordedAt": "2026-05-27T08:00:00"
}
```

### 6.4 AI 健康助手

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/ai-diagnosis` | 症状描述 → AI 初步诊断（Dubbo 调 health-record-service） |
| GET  | `/api/ai-diagnosis/report?ownerId=&petId=&period=WEEKLY\|MONTHLY` | AI 生成健康周报/月报 |

**AI 诊断请求体：**
```json
{
  "petId": "xxx",
  "species": "CAT",
  "breed": "英短",
  "ageMonths": 18,
  "symptoms": "今天没精神，不爱动，也不太吃东西，还偶尔打喷嚏",
  "duration": "1天"
}
```

**AI 诊断响应体：**
```json
{
  "possibleCauses": [
    { "name": "天气炎热导致食欲下降", "probability": "中", "description": "..." },
    { "name": "毛球症", "probability": "中高", "description": "..." },
    { "name": "上呼吸道感染", "probability": "低", "description": "..." }
  ],
  "suggestions": [
    "保持环境通风降温，可提供冰垫",
    "梳毛促进排毛，必要时化毛膏",
    "观察 24 小时，若未改善建议就医"
  ],
  "redFlags": ["如果出现持续性呕吐或呼吸困难请立即就医"],
  "disclaimer": "本建议仅供参考，不能替代兽医诊断"
}
```

### 6.5 社区帖子

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/posts` | 发帖 |
| GET  | `/api/posts` | 帖子列表（支持 category / page / size 筛选） |
| GET  | `/api/posts/{id}` | 帖子详情（含回复 + 作者信息） |
| PUT  | `/api/posts/{id}` | 编辑帖子 |
| DELETE | `/api/posts/{id}` | 删除帖子（物理删除） |
| GET  | `/api/posts/hot?limit=` | 热门帖子 Top N（Redis 热门榜，Redis 挂时降级 DB） |
| GET  | `/api/posts/author/{authorId}` | 某用户的帖子 |

### 6.6 回复

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/replies` | 回复帖子 |
| GET  | `/api/replies/post/{postId}` | 某帖子的回复（按点赞排序） |
| DELETE | `/api/replies/{id}` | 删除回复 |
| POST | `/api/replies/{id}/accept` | 标记为最佳答案 |

### 6.7 评论（嵌套）— 未实现

> 当前项目**未实现嵌套评论功能**，无 `CommentController` / `Comment` 实体。回复为扁平结构（`Reply`）。

### 6.8 点赞

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/likes` | 点赞（防重复，幂等） |
| DELETE | `/api/likes/{targetType}/{targetId}` | 取消点赞 |
| GET  | `/api/likes/check` | 检查当前用户是否已点赞 |

### 6.9 提醒系统

| Method | Path | 说明 |
|--------|------|------|
| GET  | `/api/reminders?ownerId=` | 我的所有提醒（前端按进行中 / 已完成分组） |
| GET  | `/api/reminders/due?days=7` | 即将到期（未来 N 天内）的提醒 |
| POST | `/api/reminders` | 创建提醒（应用内触达，notifyMethod 固定 `INAPP`） |
| PUT  | `/api/reminders/{id}` | 编辑提醒（仅 PENDING 可编辑） |
| PUT  | `/api/reminders/{id}/acknowledge` | 确认完成提醒（状态 → ACKNOWLEDGED） |
| PUT  | `/api/reminders/{id}/cancel` | 取消提醒 |
| DELETE | `/api/reminders/{id}` | 删除提醒 |

> 提醒一律在**应用内**触达（提醒中心状态流转），项目内无邮件 / 短信通道，无 RabbitMQ。到期由 `ReminderScheduler` 用 `findAndModify` 原子抢占翻转 PENDING→SENT。

### 6.10 营养助手

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/nutrition/{petId}` | 聚合报告：宠物信息 + 最新体重 + RER/MER 计算 + 体重趋势（最近 30 条） |
| GET | `/api/nutrition/{petId}?bcs=7` | 同上，并返回 BCS 体况调整后的目标热量 |

> 计算规则见 4.10 节；由 pethealth-web **本地聚合** pet_profiles + health_records 实现（非 Dubbo 接口）。

### 6.11 首页聚合数据

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/statistics/home` | 首页统计：帖子数、活跃用户、宠物数、即将到期提醒数 |
| GET | `/api/statistics/dashboard` | 用户 Dashboard：宠物数、健康记录总数、本周打卡、AI 诊断次数 |

### 6.12 站内通知

| Method | Path | 说明 |
|--------|------|------|
| GET  | `/api/notifications` | 我的通知列表 |
| GET  | `/api/notifications/unread-count` | 未读通知数 |
| PUT  | `/api/notifications/{id}/read` | 标记单条已读 |
| PUT  | `/api/notifications/read-all` | 全部标记已读 |
| DELETE | `/api/notifications/{id}` | 删除通知 |

---

## 7. Dubbo 微服务接口设计

> Dubbo 接口定义在 Consumer 侧（pethealth-web），Provider 侧实现。Provider 接口包名统一为 `com.pethealth.service.dubbo`。

### 7.0 Dubbo 接口的"共享模块"模式（重要！）

Dubbo 接口统一放在**共享模块 `pethealth-api`** 中，Consumer（pethealth-web）和各 Provider 都依赖该模块，**无需两边各复制一份接口**：

```
pethealth-api/                                  ← 共享 API 模块
  └── src/main/java/com/pethealth/
      ├── dto/ApiResponse.java
      ├── entity/
      │   ├── PetProfile.java / HealthRecord.java / Reminder.java
      └── service/dubbo/                         ← 纯 Java interface，无注解
          ├── PetDubboService.java
          ├── HealthRecordDubboService.java
          ├── AIDiagnosisDubboService.java
          └── ReminderDubboService.java

pet-service (Provider)
  └── .../service/dubbo/PetDubboServiceImpl.java ← @DubboService 实现

pethealth-web (Consumer)
  └── Controller 中用 @DubboReference(url="dubbo://localhost:20881/...") 注入
```

**Consumer 侧使用示例（pethealth-web 的 PetController）：**
```java
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    // Dubbo Consumer 用 @Reference 注入纯 interface
    @org.apache.dubbo.config.annotation.DubboReference(
        url = "dubbo://localhost:20881/com.pethealth.service.dubbo.PetDubboService",
        check = false,
        timeout = 5000
    )
    private PetDubboService petDubboService;  // 这是 pethealth-web 里那个纯 interface

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PetProfile>> findById(@PathVariable String id) {
        PetProfile pet = petDubboService.findById(id); // 远程调用 pet-service
        return ResponseEntity.ok(ApiResponse.success(pet));
    }
}
```

**Provider 侧实现示例（pet-service 的 PetDubboServiceImpl）：**
```java
@DubboService  // Dubbo 的 @DubboService（不是 Spring 的 @Service）
public class PetDubboServiceImpl implements PetDubboService {

    private final PetProfileRepository repository;

    @Override
    public PetProfile findById(String id) {
        return repository.findById(id).orElse(null);
    }
    // ...其他方法
}
```

### 7.1 PetDubboService（pet-service 提供）

```java
package com.pethealth.service.dubbo;

import org.apache.dubbo.config.annotation.DubboService;
import java.util.List;
import com.pethealth.entity.PetProfile;

@DubboService
public interface PetDubboService {
    PetProfile create(PetProfile profile);
    PetProfile findById(String id);
    List<PetProfile> findByOwnerId(String ownerId);
    PetProfile update(String id, PetProfile partial);
    boolean delete(String id);

    // === 嵌套数组操作 ===
    PetProfile addVaccine(String petId, PetProfile.Vaccine vaccine);
    PetProfile addDeworming(String petId, PetProfile.Deworming deworming);
    PetProfile addCheckup(String petId, PetProfile.Checkup checkup);

    // === 到期查询（提醒服务调用） ===
    List<PetProfile> findDueVaccinesWithinDays(int days);
    List<PetProfile> findDueDewormingsWithinDays(int days);
}
```

**Consumer 配置（pethealth-web 的 application.yml）：**
```yaml
dubbo:
  consumer:
    reference:
      PetDubboService:
        url: dubbo://localhost:20881/com.pethealth.service.dubbo.PetDubboService
        check: false
```

### 7.2 HealthRecordDubboService（health-record-service 提供）

```java
@DubboService
public interface HealthRecordDubboService {
    HealthRecord create(HealthRecord record);
    List<HealthRecord> findByPetId(String petId, int page, int size);
    Map<String, Object> getWeeklyStats(String petId);       // 走 Redis 缓存
    Map<String, Object> getMonthlyStats(String petId);     // 走 Redis 缓存
    List<Map<String, Object>> getTrend(String petId, String recordType, int days);
}
```

### 7.3 AIDiagnosisDubboService（health-record-service 提供）

```java
@DubboService
public interface AIDiagnosisDubboService {
    Map<String, Object> diagnose(String petId, String species, String breed,
                                 int ageMonths, String symptoms, String duration);
    String generateHealthReport(String ownerId, String petId, String period); // WEEKLY / MONTHLY
}
```

### 7.4 ReminderDubboService（reminder-service 提供）

```java
@DubboService
public interface ReminderDubboService {
    Reminder create(Reminder reminder);
    List<Reminder> findByOwnerId(String ownerId);
    List<Reminder> findPendingWithin(int hours);
    Reminder markSent(String reminderId);
    Reminder acknowledge(String reminderId);

    // 由 pet-service 的事件触发，自动从疫苗/驱虫记录创建提醒
    void scheduleFromPet(String petId);
}
```

---

## 8. ~~RabbitMQ 消息队列设计~~（已下线）

> **本项目已移除 RabbitMQ 依赖**：`pom.xml` 不再引入 `spring-boot-starter-amqp`，提醒系统改为 `ReminderScheduler` 定时扫描 + `findAndModify` 原子抢占（详见 5.5.7 节）。以下交换机/队列规划与代码仅作历史方案参考，**当前代码库中不存在 `RabbitMQConfig` / `DelayMessageProducer` / `DelayMessageConsumer`**。

### 8.1 交换机 / 队列规划（历史方案）

| 交换机类型 | Exchange | Queue | Routing Key | 用途 |
|------------|----------|-------|-------------|------|
| Direct | `pethealth.reminder.exchange` | `pethealth.reminder.queue` | `reminder.send` | 普通提醒消息 |
| Direct | `pethealth.reminder.delay.exchange` | `pethealth.reminder.delay.queue` | `reminder.delay` | **延迟消息**（到期提醒） |
| Topic | `pethealth.health.exchange` | `pethealth.health.suggestion.queue` | `health.suggestion.#` | AI 健康报告异步生成 |
| Direct | `pethealth.post.exchange` | `pethealth.post.rank.queue` | `post.rank.update` | 热门榜异步刷新 |

### 8.2 延迟消息实现（RabbitMQ 原生 TTL + Dead Letter）

RabbitMQ 原生不支持延迟队列，用 **TTL 队列 + 死信交换机** 实现：

```java
@Configuration
public class RabbitMQConfig {

    // ========== 延迟提醒 ==========

    public static final String DELAY_EXCHANGE = "pethealth.reminder.delay.exchange";
    public static final String DELAY_QUEUE     = "pethealth.reminder.delay.queue";
    public static final String DELAY_ROUTING  = "reminder.delay";

    // 真正消费的队列（死信队列）
    public static final String REMINDER_EXCHANGE = "pethealth.reminder.exchange";
    public static final String REMINDER_QUEUE    = "pethealth.reminder.queue";
    public static final String REMINDER_ROUTING  = "reminder.send";

    /** 延迟队列：消息过期后进入死信交换机 → 真正的提醒队列 */
    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable(DELAY_QUEUE)
                .withArgument("x-dead-letter-exchange", REMINDER_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", REMINDER_ROUTING)
                .build();
    }

    @Bean public DirectExchange delayExchange() {
        return ExchangeBuilder.directExchange(DELAY_EXCHANGE).durable(true).build();
    }

    @Bean public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with(DELAY_ROUTING);
    }

    /** 真正消费的提醒队列 */
    @Bean public Queue reminderQueue() { return QueueBuilder.durable(REMINDER_QUEUE).build(); }
    @Bean public DirectExchange reminderExchange() {
        return ExchangeBuilder.directExchange(REMINDER_EXCHANGE).durable(true).build();
    }
    @Bean public Binding reminderBinding() {
        return BindingBuilder.bind(reminderQueue()).to(reminderExchange()).with(REMINDER_ROUTING);
    }
}
```

### 8.3 发送延迟消息（ReminderService 示例）

```java
public void scheduleVaccineReminder(Reminder reminder) {
    long delayMs = ChronoUnit.MILLIS.between(LocalDateTime.now(), reminder.getRemindAt());
    // 最小 1 分钟
    delayMs = Math.max(60_000L, delayMs);

    rabbitTemplate.convertAndSend(
            RabbitMQConfig.DELAY_EXCHANGE,
            RabbitMQConfig.DELAY_ROUTING,
            reminder,
            msg -> { msg.getMessageProperties().setDelay((int) delayMs); return msg; }
    );
    reminderRepository.save(reminder);
}
```

### 8.4 消费提醒 → 发送邮件

```java
@RabbitListener(queues = RabbitMQConfig.REMINDER_QUEUE)
public void onReminder(String payload) {
    Reminder reminder = objectMapper.readValue(payload, Reminder.class);
    emailNotifyService.send(reminder);
    reminder.setStatus("SENT");
    reminderRepository.save(reminder);
    log.info("提醒已发送: {}", reminder.getTitle());
}
```

---

## 9. AI 功能设计

### 9.1 技术选型

- **LLM**: DeepSeek API（便宜且效果好，适合 AI 健康诊断场景）
- **HTTP 客户端**: OkHttp 4.12.0
- **API**: `https://api.deepseek.com/v1/chat/completions`
- **模型**: `deepseek-v4-flash`（或 `deepseek-chat`）

### 9.2 LLMClient 完整实现代码（可直接复制粘贴）

**文件位置**: `health-record-service/src/main/java/com/pethealth/service/LLMClient.java`

**依赖说明**: 需要 pom.xml 引入 `com.squareup.okhttp3:okhttp:4.12.0` 和 `com.alibaba.fastjson2:fastjson2`

```java
package com.pethealth.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

/**
 * AI 大模型客户端
 * 调用 DeepSeek API 实现：AI 健康诊断、健康周报生成、提醒文案生成
 */
@Slf4j
@Component
public class LLMClient {

    private final OkHttpClient client;

    @Value("${llm.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.model:deepseek-v4-pro}")
    private String model;

    @Value("${llm.max-tokens:4096}")
    private Integer maxTokens;

    @Value("${llm.temperature:0.7}")
    private Double temperature;

    public LLMClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .retryOnConnectionFailure(true)
                .build();
    }

    /** 检查 API Key 是否已配置 */
    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * AI 健康诊断
     * 用户输入症状描述，AI 返回可能原因、建议、危险信号
     */
    public String diagnose(String species, String breed, int ageMonths,
                           String symptoms, String duration) {
        String prompt = String.format(
                """
                你是一位经验丰富的宠物医生AI助手。请基于以下信息给出诊断建议：

                【宠物信息】
                类型：%s
                品种：%s
                年龄：%d 个月

                【症状描述】
                %s
                持续时间：%s

                请严格按以下 JSON 格式返回（只返回纯 JSON，不要任何 markdown 代码块标记、解释性文字或前后缀）：
                {
                  "possibleCauses": [
                    { "name": "可能原因", "probability": "高/中/低", "description": "简短说明" }
                  ],
                  "suggestions": ["建议1", "建议2"],
                  "redFlags": ["需要立即就医的危险信号"],
                  "disclaimer": "温馨提示：AI诊断仅供参考，不能替代专业兽医诊断"
                }

                特别注意：
                - 必须根据 %s 的常见疾病进行匹配
                - 概率判断要有依据（如 %s 幼猫的常见问题）
                - 危险信号要明确，不可模棱两可
                - 至少列出 2-3 个可能原因
                - 不要出现任何 markdown 标记，直接输出 JSON
                """,
                species, breed, ageMonths, symptoms, duration, species, species
        );
        return callLLM(prompt);
    }

    /**
     * 生成健康周报
     */
    public String generateHealthReport(String ownerName, String petName,
                                       String species, String statsSummary) {
        String prompt = String.format(
                "作为宠物健康管家，请为 %s（%s，%s）生成一份健康周报。\n\n" +
                "本周数据概览：\n%s\n\n" +
                "请生成：\n1. 本周健康评价（自然语言一段话）\n2. 体重/食欲/运动的趋势分析\n" +
                "3. 给铲屎官的温馨提示\n4. 下周关注重点\n\n" +
                "要求：语言要亲切友好，像一个懂行的朋友，不要使用 markdown 格式，直接输出纯文本。",
                petName, species, ownerName, statsSummary
        );
        return callLLM(prompt);
    }

    /**
     * 生成疫苗/驱虫到期提醒文案
     */
    public String generateVaccineReminder(String petName, String vaccineName, int daysLeft) {
        String prompt = String.format(
                "请为宠物【%s】生成一条疫苗到期提醒文案：\n" +
                "疫苗：%s，还有 %d 天到期。\n" +
                "要求：亲切活泼，不超过 100 字，适合发邮件/推送，纯文本。",
                petName, vaccineName, daysLeft
        );
        return callLLM(prompt);
    }

    // ===================== 核心 HTTP 调用（完整实现） =====================

    /**
     * 调用 DeepSeek API 的核心方法
     * OkHttp 构造请求 → 发送 → FastJSON2 解析响应 → 提取 content 字段
     */
    private String callLLM(String prompt) {
        if (!isApiKeyConfigured()) {
            log.error("DeepSeek API Key 未配置！请设置环境变量 LLM_API_KEY 或 application.yml 中的 llm.api-key");
            throw new IllegalStateException("LLM API Key 未配置");
        }

        try {
            log.info("开始调用 LLM API，模型: {}, prompt 长度: {}", model, prompt.length());

            // 1. 构造请求体
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);

            JSONObject requestBody = new JSONObject();
            requestBody.put("messages", new Object[]{message});
            requestBody.put("model", model);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("stream", false);

            // 2. 构造 OkHttp 请求
            RequestBody body = RequestBody.create(
                    JSON.toJSONString(requestBody),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .post(body)
                    .build();

            // 3. 发送请求
            long startTime = System.currentTimeMillis();
            try (Response response = client.newCall(request).execute()) {
                long duration = System.currentTimeMillis() - startTime;
                log.info("LLM API 调用耗时: {}ms, HTTP 状态: {}", duration, response.code());

                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().source().readString(StandardCharsets.UTF_8);
                    log.debug("LLM 原始响应 (前500字): {}",
                            responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

                    // 4. 解析响应 JSON
                    JSONObject jsonResponse = JSON.parseObject(responseBody);
                    if (jsonResponse.containsKey("choices")
                            && !jsonResponse.getJSONArray("choices").isEmpty()) {

                        String content = jsonResponse.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        // 5. 清理 AI 可能返回的 markdown 代码块
                        content = cleanAiResponse(content);
                        log.info("LLM 调用成功，content 长度: {}", content.length());
                        return content.trim();
                    }
                } else if (response.body() != null) {
                    String errorBody = response.body().source().readString(StandardCharsets.UTF_8);
                    log.error("LLM 调用失败: HTTP {} - {}", response.code(),
                            errorBody.length() > 300 ? errorBody.substring(0, 300) + "..." : errorBody);

                    if (response.code() == 401) throw new RuntimeException("API Key 无效或过期");
                    if (response.code() == 403) throw new RuntimeException("API 调用被拒绝，可能额度不足");
                    if (response.code() == 429) throw new RuntimeException("API 调用频率超限");
                } else {
                    log.error("LLM 调用失败: HTTP {}，响应体为空", response.code());
                }
            }
        } catch (java.net.SocketTimeoutException e) {
            log.error("LLM API 调用超时", e);
            throw new RuntimeException("AI 服务响应超时，请稍后重试", e);
        } catch (java.net.UnknownHostException e) {
            log.error("无法连接到 LLM 服务器，请检查网络", e);
            throw new RuntimeException("无法连接到 AI 服务，请检查网络", e);
        } catch (IOException e) {
            log.error("LLM API 调用 IO 异常", e);
            throw new RuntimeException("AI 服务调用失败", e);
        } catch (Exception e) {
            log.error("LLM API 调用未知错误", e);
            throw new RuntimeException("AI 服务调用异常: " + e.getMessage(), e);
        }

        throw new RuntimeException("LLM API 调用失败");
    }

    /**
     * 清理 AI 可能返回的 markdown 代码块标记
     * 比如 ```json ... ``` 或 ``` ... ```
     */
    private String cleanAiResponse(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim();
        // 去掉 ```json 开头
        cleaned = cleaned.replaceAll("^```(?:json|JSON)?\\s*", "");
        // 去掉结尾 ```
        cleaned = cleaned.replaceAll("\\s*```\\s*$", "");
        return cleaned.trim();
    }
}
```

### 9.3 Prompt 安全设计

```
1. 输入校验：species 白名单 (DOG/CAT/RABBIT/HAMSTER/BIRD/OTHER)
2. 症状内容长度限制：500 字
3. API Key 走环境变量注入，不写死到代码
4. AI 响应解析 + JSON schema 校验，避免注入恶意内容
5. 所有 AI 输出都附带 disclaimer（免责声明）
6. 调用超时 120s，防阻塞
7. 失败降级：返回预设模板 + 建议"请尽快就医"
```

### 9.4 AI 调用链路

```
前端 POST /api/ai-diagnosis
   │
   ▼
AIDiagnosisController (pethealth-web :8080)
   │
   ▼ Dubbo
AIDiagnosisDubboServiceImpl (health-record-service :8086)
   │
   ▼
LLMClient.callLLM()
   │
   ▼ HTTP
DeepSeek API (api.deepseek.com/v1/chat/completions)
   │
   ▼
解析 JSON → 校验 → 返回结构化结果
```

---

## 10. 前端页面设计

### 10.1 页面列表（完整对应导航栏）

| 导航 | 页面 | 核心内容 |
|------|------|----------|
| 首页 | Home | 统计卡片、热门社区帖子、即将到期提醒、我的宠物卡片 |
| 宠物档案 | PetProfile | 宠物列表 → 详情（疫苗时间轴、驱虫、体检、就医记录 Tab） |
| 健康记录 | HealthRecord | 新增记录表单 + ECharts 趋势图（体重/体温/食量/运动） |
| AI 健康助手 | AIDiagnosis | 症状输入 → AI 诊断结果卡片（可能原因/建议/危险信号） |
| 社区 | Community | 帖子列表（分类 Tab + 热门榜 + 标签筛选） → 帖子详情（回复 + 嵌套评论 + 点赞） |
| 营养助手 | Nutrition | 选宠物 → RER/MER 每日能量 + 喂食克数 + BCS 体重管理 + 体重趋势图 |
| 提醒中心 | Reminder | 所有提醒列表 + 创建自定义提醒 + 疫苗/驱虫到期自动提醒 |
| 统计 | Statistics | 个人 Dashboard（宠物数、本周打卡、健康数据趋势） |

### 10.2 UI 风格

- **主色调**：深海军蓝 `#3873B6`（主色）+ 天蓝 `#6DA1D8`（强调/链接），页面浅灰底 `#F5F7FA`；状态徽章用统一的浅色系（浅黄/浅绿/浅蓝/浅红/浅灰），详见 styles.css 顶部 CSS 变量
- **卡片风格**：圆角 12px + 浅阴影 + 悬停微动画，配合 `card-glow` / `card-shine` 风格类使用（完整 CSS 见 10.7 节）
- **图标**：Lucide Icons，CDN 引入，`data-lucide="paw-print"` / `heart-pulse` / `thermometer` / `calendar-bell` 等
- **图表库**：ECharts 5.4.3
- **字体**：系统默认 + 中文友好

### 10.3 关键页面 Mockup 描述

#### 首页（Dashboard）

```
┌─────────────────────────────────────────────────────────┐
│  🐾 PetHealth · 宠物健康管家      首页 | 档案 | 记录 | AI | 社区 | 营养 | 提醒  [登录/注册] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  👋 欢迎回来，小明！                                     │
│                                                         │
│  ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐               │
│  │ 🐕 豆豆 │ │ 🐈 咪咪 │ │ 🐰 白白 │ │ + 添加 │               │
│  │ 柯基  │ │ 英短  │ │ 垂耳兔│ │ 新宠物 │               │
│  │ 11.5kg│ │ 4.2kg │ │ 2.1kg│ │       │               │
│  └───────┘ └───────┘ └───────┘ └───────┘               │
│                                                         │
│  ┌─────────────────┐  ┌─────────────────────┐          │
│  │ 📊 统计数据      │  │ 🔥 热门社区帖子      │          │
│  │ 帖子总数    1,234│  │ 1. 我家猫突然不吃东西  │          │
│  │ 宠物档案       26│  │ 2. 柯基掉毛严重怎么办  │          │
│  │ 健康记录    8,567│  │ 3. 新手养猫必看清单    │          │
│  │ 活跃用户     3,456│  │ ...                 │          │
│  └─────────────────┘  └─────────────────────┘          │
│                                                         │
│  ⏰ 即将到期提醒                                         │
│  ┌─────────────────────────────────────────────────────┐│
│  │ 🔔 豆豆的狂犬疫苗  还有 3 天到期  [标记已处理]       ││
│  │ 🔔 咪咪的驱虫      还有 12 天到期  [标记已处理]       ││
│  └─────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────┘
```

#### 宠物详情页（疫苗时间轴）

```
┌─────────────────────────────────────────────────────────────┐
│  ← 返回  豆豆（柯基 · 2岁 · 公）                             │
│  活泼好动的小柯基 🐕         体重: 11.5kg  体温: 38.6℃       │
├─────────────────────────────────────────────────────────────┤
│  [基本信息] [💉疫苗] [💊驱虫] [🩺体检] [🏥就医] [➕添加新记录] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  💉 疫苗记录                                                 │
│  │  ● 2026-05-01 狂犬疫苗    爱宠宠物医院  → 下次 2027-05-01  │
│  │  ● 2025-11-15 狗四联      爱宠宠物医院  → 下次 2026-11-15  │
│  │  ○ 2025-05-01 狂犬疫苗    爱宠宠物医院                     │
│  │  ○ 2024-11-20 狗四联      爱宠宠物医院                     │
│  │  ───────────────────────► 时间轴                          │
│                                                             │
│  ➕ 添加疫苗记录   ➕ 添加驱虫记录   ➕ 添加体检记录            │
└─────────────────────────────────────────────────────────────┘
```

#### AI 诊断页

```
┌─────────────────────────────────────────────────────────────┐
│  🤖 AI 健康助手                                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  选择宠物: [豆豆 ▼] 柯基 · 2岁 · 公 · 11.5kg                 │
│                                                             │
│  描述症状:                                                    │
│  ┌─────────────────────────────────────────────────────────┐│
│  │ 我家豆豆今天没精神，不爱动，也不太吃东西，还偶尔打哈欠...    ││
│  │                                                          ││
│  │                                                          ││
│  └─────────────────────────────────────────────────────────┘│
│  持续时间: [1天 ▼]   [🔍 开始 AI 诊断]                       │
│                                                             │
│  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
│                                                             │
│  📋 AI 诊断结果（仅供参考，不能替代兽医诊断）                   │
│                                                             │
│  ⚠️ 可能原因：                                                │
│  ├─ 🔸 天气炎热导致食欲下降          概率：中                  │
│  │  夏季高温环境下，狗狗食欲减退较常见...                       │
│  ├─ 🔸 毛球症（柯基也会舔毛）         概率：中高               │
│  │  表现为食欲不佳、偶尔呕吐...                                │
│  └─ 🔸 上呼吸道感染                  概率：低                 │
│     伴随打喷嚏、流鼻涕等...                                    │
│                                                             │
│  💡 建议：                                                    │
│  1. 保持环境通风降温，可提供冰垫                                │
│  2. 梳毛促进排毛，必要时化毛膏                                │
│  3. 观察 24 小时，若未改善建议就医                             │
│                                                             │
│  🚨 危险信号：                                                │
│  • 如果出现持续性呕吐或呼吸困难请立即就医                       │
│  • 如果 24 小时不进食请立即就医                                │
│                                                             │
│  [📝 把症状记录到健康档案]  [🧮 去营养助手算每日喂食量]        │
└─────────────────────────────────────────────────────────────┘
```

#### 社区帖子列表 + 详情

```
┌─ 帖子列表 ─────────────────────────────┐
│ 分类: [全部] [💊健康] [🍖饮食] [🎾训练] [🏠日常] [🚨紧急]  │
│                                                          │
│ 🔥 热门排行:                                             │
│  ┌────────────────────────────────────────────────────┐ │
│  │ 🐈 我家猫突然不吃东西了怎么办？                       │ │
│  │ 柯基掉毛严重怎么办？  6小时前 · 128查看 · 12赞 · 8回复│ │
│  │ 新手养猫必看清单    1天前 · 520查看 · 34赞 · 15回复 │ │
│  │ ...                                                │ │
│  └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘

┌─ 帖子详情 ─────────────────────────────┐
│ 标题: 我家猫突然不吃东西了怎么办？        │
│ 作者: 小明 · 2小时前 · 28°CAT标签        │
│                                          │
│ 内容: 昨天还好好的，今天就不吃猫粮了...    │
│                                         │
│ 💕 12赞   💬 8回复   👁 128查看          │
│                                         │
│ ── 最佳答案 ──                           │
│ 老铲屎官: 建议先观察精神状态，测一下体温...│
│ 💕 5赞  [作者采纳 ✓]                     │
│                                         │
│ ── 其他回复 ──                           │
│ 路人甲: 说的有道理！💕 3赞               │
│   └─ 回复: 对呀我家猫上次也这样 [嵌套评论] │
│ 宠物医生小李: 建议测一下体温... 💕 2赞   │
└───────────────────────────────────────────┘
```

#### 健康趋势图（ECharts）

```
┌─────────────────────────────────────────────────────────────┐
│ 豆豆 · 健康趋势（最近 30 天）                                │
│ [体重] [体温] [食量] [运动量]                                │
│                                                             │
│  13 ┤                                       ┌─13.0kg        │
│  12.5┤     ┌─12.8kg    ┌─12.9kg            │               │
│  12 ┤ ┌─12.3kg   ──────┘        ┌─12.7kg  ─┘               │
│  11.5┤─┘                                              ╲     │
│  11 ┤                                                   ─┘ │
│     └──┬──────┬──────┬──────┬──────┬──────┬──────┬──────┬─── │
│       5/1   5/5   5/10  5/15  5/20  5/25  5/27             │
│                                                             │
│  趋势分析：本周体重略有上升（+0.2kg），整体稳定 ✅            │
│  ➕ 今天打卡：体重 [____]kg  体温 [____]℃  食量 [____]g      │
└─────────────────────────────────────────────────────────────┘
```

### 10.4 前端技术约定

与上文 10.5-10.8 节的 `card-glow` / `card-shine` 等 CSS 类直接对应，风格类名在 styles.css 中已有完整定义。

| 约定 | 说明 |
|------|------|
| 图标 | `<script src="https://unpkg.com/lucide@latest"></script>` + `data-lucide="heart-pulse"` |
| 图表 | `echarts.init(dom)` + `chart.setOption({...})` |
| API 调用 | `fetch('/api/xxx').then(r => r.json())` |
| 页面切换 | 单页应用，CSS class `section.active` 控制显示/隐藏 |
| 主题色 CSS 变量 | `--primary: #FF8C42; --success: #4CAF50; --info: #4A90D9;` |
| 卡片类 | `.card-glow` `.card-shine` `.white-line-top`（完整定义见 10.7 节 styles.css） |

### 10.5 导航栏 HTML 模板

```html
<header>
    <div class="header-container">
        <div class="logo">
            <h1>🐾 PetHealth</h1>
            <span class="tagline">宠物健康管家</span>
        </div>
        <nav>
            <ul>
                <li><a href="#home" class="nav-link" onclick="showSection('home')">首页</a></li>
                <li><a href="#pets" class="nav-link" onclick="showSection('pets')">宠物档案</a></li>
                <li><a href="#health-records" class="nav-link" onclick="showSection('health-records')">健康记录</a></li>
                <li><a href="#ai-diagnosis" class="nav-link" onclick="showSection('ai-diagnosis')">AI 健康助手</a></li>
                <li><a href="#community" class="nav-link" onclick="showSection('community')">宠物社区</a></li>
                <li><a href="#nutrition" class="nav-link" onclick="showSection('nutrition')">营养助手</a></li>
                <li><a href="#reminders" class="nav-link" onclick="showSection('reminders')">提醒中心</a></li>
                <li><a href="#statistics" class="nav-link" onclick="showSection('statistics')">统计</a></li>
            </ul>
        </nav>
        <div id="user-section">
            <button class="btn btn-secondary" onclick="showLoginModal()">登录</button>
            <button class="btn btn-primary" onclick="showRegisterModal()">注册</button>
        </div>
    </div>
</header>
```

### 10.6 script.js 核心骨架（完整可运行）

**文件位置**: `pethealth-web/src/main/resources/static/script.js`

下面是一个**能直接跑通**的单页应用骨架。包含：API 封装、页面切换、登录/注册、帖子 CRUD、热门榜渲染、ECharts 初始化模板。**复制粘贴即可，不需要改一行代码就能看到效果**（前提是后端已启动）。

```javascript
// ===================== 全局状态 =====================
const AppState = {
    currentSection: 'home',
    currentUser: null,
    petCache: [],
    postCache: [],
};

// ===================== 通用 API 封装 =====================
/**
 * 统一 fetch 封装，自动处理 ApiResponse 结构
 * 后端返回 { code: 200, message: "success", data: {...}, timestamp: ... }
 */
async function api(method, url, body = null) {
    const opts = {
        method,
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
    };
    if (body) opts.body = JSON.stringify(body);

    const res = await fetch(url, opts);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    if (json.code !== 200) throw new Error(json.message || '请求失败');
    return json.data;
}
const apiGet    = (url)         => api('GET',    url);
const apiPost   = (url, body)   => api('POST',   url, body);
const apiPut    = (url, body)   => api('PUT',    url, body);
const apiDelete = (url)         => api('DELETE', url);

// ===================== 页面切换 =====================
/**
 * 单页应用核心：把所有 section 默认隐藏，只显示指定 id 的那个
 * 同时更新导航栏 active 状态
 */
function showSection(sectionId) {
    // 1. 隐藏所有 section
    document.querySelectorAll('main > section').forEach(s => s.classList.remove('active'));
    // 2. 显示目标 section
    const target = document.getElementById(sectionId);
    if (target) target.classList.add('active');
    // 3. 更新导航栏高亮
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    const activeLink = document.querySelector(`.nav-link[href="#${sectionId}"]`);
    if (activeLink) activeLink.classList.add('active');

    // 4. 切换时加载对应数据（懒加载）
    AppState.currentSection = sectionId;
    loadSectionData(sectionId);
    // 5. 如果有 Lucide 图标，刷新
    if (window.lucide) window.lucide.createIcons();
}

/** 各 section 数据懒加载入口 */
async function loadSectionData(id) {
    try {
        switch (id) {
            case 'home':        await loadHome(); break;
            case 'pets':        await loadPets(); break;
            case 'community':   await loadPosts(); break;
            case 'ai-diagnosis': /* 静态页面，不需要预加载 */ break;
            case 'health-records':  await loadHealthRecords(); break;
            case 'nutrition':   await loadNutrition(); break;
            case 'reminders':   await loadReminders(); break;
            case 'statistics':  await loadStatistics(); break;
        }
    } catch (e) {
        console.warn(`加载 ${id} 数据失败（可能后端未启动）:`, e);
    }
}

// ===================== 首页加载 =====================
async function loadHome() {
    // 热门帖子（Redis Sorted Set）
    try {
        const hotPosts = await apiGet('/api/posts/hot?limit=5');
        const container = document.getElementById('hot-posts-list');
        if (container) container.innerHTML = hotPosts.map(p => `
            <div class="hot-post-item" onclick="goToPost('${p.id}')">
                <span class="hot-rank">🔥</span>
                <span class="hot-title">${p.title}</span>
                <span class="hot-meta">${p.replyCount || 0}回复</span>
            </div>
        `).join('');
    } catch (e) { /* 后端未启动时显示占位符 */ }

    // 即将到期提醒
    try {
        const dueReminders = await apiGet('/api/reminders/due?days=7');
        const container = document.getElementById('due-reminders-list');
        if (container && dueReminders.length > 0) {
            container.innerHTML = dueReminders.map(r => `
                <div class="reminder-item">
                    <span class="reminder-icon">🔔</span>
                    <span>${r.title} — 还有 ${r.daysLeft} 天</span>
                </div>
            `).join('');
        }
    } catch (e) { /* 忽略 */ }
}

// ===================== 登录注册（Modal） =====================
function showLoginModal() {
    const html = `
        <div class="modal" id="login-modal">
            <div class="modal-content">
                <h3>🐾 登录 PetHealth</h3>
                <div class="form-group">
                    <label>用户名</label>
                    <input id="login-username" type="text" placeholder="demo">
                </div>
                <div class="form-group">
                    <label>密码</label>
                    <input id="login-password" type="password" placeholder="123456">
                </div>
                <button class="btn btn-primary full-width" onclick="doLogin()">登录</button>
                <p class="modal-hint">测试账号: demo / 123456</p>
                <button class="modal-close" onclick="closeModal('login-modal')">✕</button>
            </div>
        </div>`;
    openModal(html);
}

function showRegisterModal() {
    const html = `
        <div class="modal" id="register-modal">
            <div class="modal-content">
                <h3>🐾 注册 PetHealth</h3>
                <div class="form-group">
                    <label>用户名</label>
                    <input id="reg-username" type="text" placeholder="你的昵称">
                </div>
                <div class="form-group">
                    <label>邮箱</label>
                    <input id="reg-email" type="email" placeholder="you@example.com">
                </div>
                <div class="form-group">
                    <label>密码</label>
                    <input id="reg-password" type="password" placeholder="至少 6 位">
                </div>
                <button class="btn btn-primary full-width" onclick="doRegister()">注册</button>
                <button class="modal-close" onclick="closeModal('register-modal')">✕</button>
            </div>
        </div>`;
    openModal(html);
}

function openModal(html) { document.body.insertAdjacentHTML('beforeend', html); }
function closeModal(id)  { document.getElementById(id)?.remove(); }

async function doLogin() {
    const username = document.getElementById('login-username').value;
    const password = document.getElementById('login-password').value;
    try {
        const user = await apiPost('/api/users/login', { username, password });
        AppState.currentUser = user;
        closeModal('login-modal');
        updateUserSection();
        showToast(`欢迎回来，${user.username}！`);
    } catch (e) {
        showToast(e.message, 'error');
    }
}

async function doRegister() {
    const username = document.getElementById('reg-username').value;
    const email    = document.getElementById('reg-email').value;
    const password = document.getElementById('reg-password').value;
    try {
        await apiPost('/api/users/register', { username, email, password });
        closeModal('register-modal');
        showToast('注册成功！请登录');
        showLoginModal();
    } catch (e) {
        showToast(e.message, 'error');
    }
}

function updateUserSection() {
    const section = document.getElementById('user-section');
    if (!AppState.currentUser) {
        section.innerHTML = `
            <button class="btn btn-secondary" onclick="showLoginModal()">登录</button>
            <button class="btn btn-primary" onclick="showRegisterModal()">注册</button>`;
    } else {
        section.innerHTML = `
            <span class="user-welcome">👋 ${AppState.currentUser.username}</span>
            <button class="btn btn-secondary" onclick="doLogout()">退出</button>`;
    }
}

function doLogout() {
    AppState.currentUser = null;
    updateUserSection();
    showToast('已退出登录');
}

// ===================== 帖子列表 + 热门榜 =====================
async function loadPosts() {
    const container = document.getElementById('posts-container');
    try {
        const posts = await apiGet('/api/posts?page=0&size=20');
        AppState.postCache = posts;
        container.innerHTML = posts.map(p => `
            <div class="post-card card-glow">
                <div class="post-header">
                    <span class="post-category">${p.category || 'GENERAL'}</span>
                    <span class="post-author">by ${p.authorName}</span>
                </div>
                <h3 class="post-title">${p.title}</h3>
                <p class="post-content">${truncate(p.content, 100)}</p>
                <div class="post-meta">
                    <span>👁 ${p.viewCount || 0}</span>
                    <span>👍 ${p.likeCount || 0}</span>
                    <span>💬 ${p.replyCount || 0}</span>
                    <button class="btn-tiny" onclick="goToPost('${p.id}')">查看详情 →</button>
                </div>
            </div>
        `).join('');
    } catch (e) {
        container.innerHTML = `<p class="empty-hint">💡 后端还没启动，或者还没有帖子 —— 你可以先启动后端试试</p>`;
    }
}

async function goToPost(postId) {
    // 切到社区并滚动
    showSection('community');
    // 展开详情（简化：直接弹窗显示）
    try {
        const post = await apiGet(`/api/posts/${postId}`);
        const replies = await apiGet(`/api/replies/post/${postId}`);
        alert(`${post.title}\n\n${post.content}\n\n--- ${replies.length} 条回复 ---`);
    } catch (e) { console.warn(e); }
}

// ===================== 宠物档案 =====================
async function loadPets() {
    const container = document.getElementById('pets-container');
    try {
        const pets = await apiGet('/api/pets');
        AppState.petCache = pets;
        container.innerHTML = pets.map(p => `
            <div class="pet-card card-glow card-shine" onclick="showPetDetail('${p.id}')">
                <div class="pet-avatar">🐾</div>
                <h3>${p.name}</h3>
                <p class="pet-meta">${p.species || ''} · ${p.breed || ''}</p>
                <p class="pet-meta">${p.gender || ''}</p>
                ${p.vaccines && p.vaccines.length ?
                    `<span class="pet-badge">💉 ${p.vaccines.length} 疫苗</span>` : ''}
            </div>
        `).join('');
    } catch (e) {
        container.innerHTML = `
            <div class="pet-card card-glow">
                <div class="pet-avatar">🐾</div>
                <h3>示例宠物</h3>
                <p class="pet-meta">DOG · 柯基</p>
                <p class="pet-meta">MALE</p>
            </div>`;
    }
}

function showPetDetail(petId) {
    alert(`宠物详情（${petId}）—— 这里可以打开一个详情弹窗显示疫苗时间轴、驱虫记录等`);
}

// ===================== 健康记录 + ECharts 趋势图 =====================
async function loadHealthRecords() {
    // 先尝试加载第一只宠物的趋势数据
    const chartDom = document.getElementById('trend-chart');
    if (chartDom && window.echarts) {
        renderSampleTrendChart(chartDom);
    }
}

/**
 * ECharts 体重趋势图模板
 * （这里先用示例数据渲染，后端 ready 后改成 fetch 实际数据）
 */
function renderSampleTrendChart(dom) {
    const chart = echarts.init(dom);
    const days = ['5/1', '5/5', '5/10', '5/15', '5/20', '5/25', '5/27'];
    const weights = [11.2, 11.5, 11.3, 11.7, 11.5, 11.8, 11.5];

    chart.setOption({
        title: { text: '🐾 豆豆 · 体重趋势（近 30 天）', left: 'center' },
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: days },
        yAxis: { type: 'value', name: 'kg', min: 10, max: 13 },
        series: [{
            name: '体重', type: 'line', data: weights,
            smooth: true,
            itemStyle: { color: '#FF8C42' },
            areaStyle: { color: 'rgba(255,140,66,0.15)' },
            markPoint: {
                data: [
                    { type: 'max', name: '最高' },
                    { type: 'min', name: '最低' },
                ]
            }
        }]
    });

    // 窗口 resize 时重绘
    window.addEventListener('resize', () => chart.resize());
}

// ===================== AI 诊断 =====================
async function submitDiagnosis() {
    const petId = document.getElementById('diag-pet-select')?.value || '';
    const symptoms = document.getElementById('diag-symptoms')?.value || '';
    const duration = document.getElementById('diag-duration')?.value || '1天';
    const resultBox = document.getElementById('ai-result');

    if (!symptoms.trim()) {
        showToast('请描述症状哦', 'error');
        return;
    }

    resultBox.innerHTML = '<p class="loading">🤖 AI 正在分析中...</p>';

    try {
        const data = await apiPost('/api/ai-diagnosis', {
            petId, species: 'CAT', breed: '英短', ageMonths: 18,
            symptoms, duration
        });
        // 后端返回的可能是 JSON 字符串（AI 返回），需要解析
        const parsed = typeof data === 'string' ? JSON.parse(data) : data;

        resultBox.innerHTML = `
            <div class="ai-result-card">
                <h4>📋 可能原因：</h4>
                <ul>${parsed.possibleCauses?.map(c => `<li>🔸 <b>${c.name}</b>（概率：${c.probability}）— ${c.description || ''}</li>`).join('') || ''}</ul>
                <h4>💡 建议：</h4>
                <ol>${parsed.suggestions?.map(s => `<li>${s}</li>`).join('') || ''}</ol>
                ${parsed.redFlags?.length ? `<h4>🚨 危险信号：</h4><ul>${parsed.redFlags.map(s => `<li class="red-flag">${s}</li>`).join('')}</ul>` : ''}
                <p class="disclaimer">⚠️ ${parsed.disclaimer || '本建议仅供参考，不能替代兽医诊断'}</p>
            </div>
        `;
    } catch (e) {
        resultBox.innerHTML = `<p class="empty-hint">AI 调用失败：${e.message} —— 检查 LLM_API_KEY 是否配置</p>`;
    }
}

// ===================== 营养助手 / 提醒 / 统计（简化版） =====================
// 营养助手：选择宠物 → 请求后端聚合报告 → 渲染 RER/MER/喂食克数与体重趋势
async function loadNutrition() {
    const sel = document.getElementById('nt-pet-select');
    const c = document.getElementById('nutrition-container');
    if (!sel || !c) return;
    if (!AppState.petCache.length) AppState.petCache = await apiGet('/api/pets');
    sel.innerHTML = '<option value="">请选择宠物</option>' + AppState.petCache.map(p =>
        `<option value="${p.id}">${p.name} · ${p.species || ''} · ${p.breed || ''}</option>`).join('');
    await loadNutritionReport();
}

async function loadNutritionReport() {
    const petId = document.getElementById('nt-pet-select')?.value;
    const c = document.getElementById('nutrition-container');
    if (!petId || !c) return;
    const report = await apiGet(`/api/nutrition/${petId}`);
    const calc = report.calculation;
    c.innerHTML = calc
        ? `<div class="card-glow nutrition-report">
             <h3>${report.pet.name} · 每日营养需求</h3>
             <p>RER ${Math.round(calc.rer)} kcal · 系数 ${calc.merFactor}（${calc.stageLabel}）
                · MER ${Math.round(calc.mer)} kcal/天</p>
             <p>主粮 380 kcal/100g 时每日喂食约
                <b>${(Math.round(calc.mer / 3.8 * 10) / 10).toFixed(1)} g</b></p>
             <p class="nutrition-disclaimer">参考 NRC 2006 / WSAVA 通用标准，实际需求因个体而异，请遵医嘱。</p>
           </div>`
        : `<div class="empty-hint">${report.notice || '暂无体重记录，暂无法计算'}</div>`;
}

async function loadReminders() {
    const c = document.getElementById('reminders-container');
    if (c) {
        c.innerHTML = `
            <div class="reminder-item">🔔 豆豆的狂犬疫苗 — 还有 3 天到期</div>
            <div class="reminder-item">🔔 咪咪的驱虫 — 还有 12 天到期</div>
            <div class="reminder-item">🔔 柯基的体检 — 本月待完成</div>`;
    }
}

async function loadStatistics() {
    const c = document.getElementById('stats-container');
    if (c && c.children.length === 0) {
        c.innerHTML = `
            <div class="stat-card">🐾 我的宠物 <b>2</b></div>
            <div class="stat-card">📝 健康记录 <b>128</b></div>
            <div class="stat-card">🤖 AI 诊断 <b>15</b></div>
            <div class="stat-card">💉 疫苗记录 <b>6</b></div>`;
    }
}

// ===================== 工具函数 =====================
function truncate(str, n) { return str.length > n ? str.slice(0, n) + '...' : str; }

function showToast(msg, type = 'success') {
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.textContent = msg;
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 2500);
}

// ===================== 启动入口 =====================
document.addEventListener('DOMContentLoaded', () => {
    // 初始化 Lucide 图标
    if (window.lucide) window.lucide.createIcons();
    // 默认显示首页
    showSection('home');
    // 启动时加载一次用户状态（cookie / localStorage）
    const cached = localStorage.getItem('pethealth_user');
    if (cached) {
        try { AppState.currentUser = JSON.parse(cached); updateUserSection(); } catch (e) {}
    }
});
```

### 10.7 styles.css 核心样式骨架

**文件位置**: `pethealth-web/src/main/resources/static/styles.css`

```css
/* ===================== CSS 变量（主题色） ===================== */
:root {
    --primary: #FF8C42;        /* 温暖橙 — 主色 */
    --primary-dark: #E67329;
    --success: #4CAF50;        /* 健康绿 */
    --info: #4A90D9;           /* 温馨蓝 */
    --warning: #FFC107;
    --danger: #F44336;
    --bg: #FFF8F0;             /* 暖米色背景 */
    --card-bg: #FFFFFF;
    --text: #333333;
    --text-light: #777777;
    --border: #EEE5D9;
    --radius: 12px;
    --shadow: 0 2px 12px rgba(255, 140, 66, 0.08);
    --shadow-hover: 0 6px 20px rgba(255, 140, 66, 0.18);
}

/* ===================== 全局 ===================== */
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
                 "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
    background: var(--bg);
    color: var(--text);
    line-height: 1.6;
    min-height: 100vh;
}

/* ===================== 头部导航栏 ===================== */
header {
    background: linear-gradient(135deg, var(--primary), var(--primary-dark));
    color: white;
    padding: 0 2rem;
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    position: sticky; top: 0; z-index: 100;
}
.header-container {
    max-width: 1400px;
    margin: 0 auto;
    display: flex; align-items: center; justify-content: space-between;
    height: 64px;
}
.logo h1 { font-size: 1.3rem; }
.logo .tagline { font-size: 0.75rem; opacity: 0.85; }
nav ul { display: flex; gap: 0.5rem; list-style: none; }
nav a {
    color: rgba(255,255,255,0.9);
    text-decoration: none;
    padding: 0.5rem 0.9rem;
    border-radius: 20px;
    font-size: 0.9rem;
    transition: background 0.2s;
}
nav a:hover, nav a.active { background: rgba(255,255,255,0.2); }

/* ===================== 主内容区 ===================== */
main {
    max-width: 1400px;
    margin: 2rem auto;
    padding: 0 2rem;
}
main > section { display: none; }            /* 默认隐藏所有 section */
main > section.active { display: block; }    /* 当前激活的 section 显示 */
section h2 {
    font-size: 1.6rem;
    margin-bottom: 1.5rem;
    padding-bottom: 0.5rem;
    border-bottom: 2px solid var(--primary);
    display: inline-block;
}

/* ===================== 卡片组件 ===================== */
.card-glow {
    background: var(--card-bg);
    border-radius: var(--radius);
    padding: 1.25rem;
    box-shadow: var(--shadow);
    transition: box-shadow 0.25s, transform 0.25s;
}
.card-glow:hover {
    box-shadow: var(--shadow-hover);
    transform: translateY(-2px);
}
.card-shine {
    position: relative; overflow: hidden;
}
.card-shine::after {
    content: '';
    position: absolute; top: -50%; left: -50%;
    width: 200%; height: 200%;
    background: linear-gradient(45deg, transparent 40%,
                    rgba(255,255,255,0.15) 50%, transparent 60%);
    transform: translateX(-100%);
    transition: transform 0.6s;
}
.card-shine:hover::after { transform: translateX(100%); }

/* ===================== 帖子卡片 ===================== */
.post-card { margin-bottom: 1rem; }
.post-header {
    display: flex; justify-content: space-between;
    font-size: 0.8rem; color: var(--text-light);
    margin-bottom: 0.5rem;
}
.post-category {
    background: var(--primary); color: white;
    padding: 0.15rem 0.6rem; border-radius: 12px; font-size: 0.75rem;
}
.post-title { font-size: 1.15rem; margin-bottom: 0.5rem; cursor: pointer; }
.post-content { color: var(--text-light); font-size: 0.9rem; margin-bottom: 0.75rem; }
.post-meta {
    display: flex; gap: 1rem; font-size: 0.85rem; color: var(--text-light);
    align-items: center;
}

/* ===================== 宠物卡片网格 ===================== */
.pet-card {
    text-align: center; cursor: pointer;
    transition: all 0.2s;
}
.pet-avatar { font-size: 2.5rem; margin-bottom: 0.5rem; }
.pet-meta { font-size: 0.85rem; color: var(--text-light); }
.pet-badge {
    display: inline-block;
    background: var(--info); color: white;
    padding: 0.2rem 0.7rem; border-radius: 12px; font-size: 0.75rem;
    margin-top: 0.5rem;
}
.pets-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 1rem;
}

/* ===================== 网格布局 ===================== */
.dashboard-grid {
    display: grid;
    grid-template-columns: 2fr 1fr;
    gap: 1.5rem;
}
.stat-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 1rem;
}
.stat-card {
    background: var(--card-bg); border-radius: var(--radius);
    padding: 1.5rem; text-align: center; box-shadow: var(--shadow);
}
.stat-card b { font-size: 2rem; color: var(--primary); margin-left: 0.5rem; }

/* ===================== 按钮 ===================== */
.btn {
    padding: 0.5rem 1.25rem; border: none; border-radius: 20px;
    cursor: pointer; font-size: 0.85rem; font-weight: 500;
    transition: background 0.2s, transform 0.15s;
}
.btn:hover { transform: translateY(-1px); }
.btn-primary   { background: var(--primary);   color: white; }
.btn-primary:hover   { background: var(--primary-dark); }
.btn-secondary { background: white; border: 1px solid var(--border); color: var(--text); }
.btn-secondary:hover { background: var(--border); }
.btn-tiny      { padding: 0.2rem 0.7rem; font-size: 0.75rem; }
.full-width    { width: 100%; }

/* ===================== Modal ===================== */
.modal {
    position: fixed; top: 0; left: 0;
    width: 100%; height: 100%;
    background: rgba(0,0,0,0.5);
    display: flex; align-items: center; justify-content: center;
    z-index: 1000;
    animation: fadeIn 0.2s;
}
.modal-content {
    background: white; padding: 2rem;
    border-radius: var(--radius);
    width: 360px; position: relative;
    box-shadow: var(--shadow-hover);
}
.modal-content h3 { margin-bottom: 1rem; color: var(--primary); }
.modal-close {
    position: absolute; top: 0.75rem; right: 0.75rem;
    background: none; border: none; font-size: 1.2rem; cursor: pointer;
}
.modal-hint {
    font-size: 0.75rem; color: var(--text-light); text-align: center;
    margin-top: 0.5rem;
}

/* ===================== 表单 ===================== */
.form-group { margin-bottom: 1rem; }
.form-group label {
    display: block; font-size: 0.85rem; margin-bottom: 0.3rem;
    color: var(--text-light);
}
.form-group input, .form-group select, .form-group textarea {
    width: 100%; padding: 0.6rem 0.9rem;
    border: 1px solid var(--border); border-radius: 8px;
    font-size: 0.9rem; background: var(--bg);
    transition: border-color 0.2s;
}
.form-group input:focus, .form-group textarea:focus {
    outline: none; border-color: var(--primary); background: white;
}

/* ===================== Toast ===================== */
.toast {
    position: fixed; top: 80px; left: 50%;
    transform: translateX(-50%);
    padding: 0.6rem 1.5rem; border-radius: 20px;
    color: white; font-size: 0.9rem; z-index: 2000;
    animation: slideDown 0.25s;
}
.toast-success { background: var(--success); }
.toast-error   { background: var(--danger); }

/* ===================== AI 诊断结果卡 ===================== */
.ai-result-card {
    background: var(--card-bg); border-radius: var(--radius);
    padding: 1.5rem; box-shadow: var(--shadow); margin-top: 1rem;
}
.ai-result-card h4 { margin: 1rem 0 0.5rem; color: var(--primary); }
.ai-result-card ul, .ai-result-card ol { padding-left: 1.2rem; }
.ai-result-card li { margin-bottom: 0.3rem; }
.red-flag { color: var(--danger); font-weight: 500; }
.disclaimer {
    margin-top: 1rem; font-size: 0.8rem; color: var(--text-light);
    padding-top: 1rem; border-top: 1px dashed var(--border);
}
.loading { text-align: center; padding: 2rem; color: var(--text-light); }
.empty-hint {
    text-align: center; padding: 2rem;
    color: var(--text-light); font-style: italic;
}

/* ===================== 热门榜 ===================== */
.hot-post-item {
    padding: 0.5rem 0.75rem; margin-bottom: 0.3rem;
    border-radius: 8px; cursor: pointer;
    display: flex; gap: 0.5rem; align-items: center;
    transition: background 0.2s;
}
.hot-post-item:hover { background: var(--bg); }
.hot-rank { color: var(--primary); }
.hot-title { flex: 1; font-size: 0.85rem; }
.hot-meta { font-size: 0.75rem; color: var(--text-light); }

/* ===================== 提醒列表 ===================== */
.reminder-item {
    padding: 0.75rem 1rem; margin-bottom: 0.5rem;
    background: white; border-radius: 8px;
    display: flex; gap: 0.5rem; align-items: center;
    box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}

/* ===================== 图表容器 ===================== */
#trend-chart {
    width: 100%; height: 400px;
    background: var(--card-bg); border-radius: var(--radius);
    box-shadow: var(--shadow);
}

/* ===================== 响应式 ===================== */
@media (max-width: 768px) {
    .dashboard-grid { grid-template-columns: 1fr; }
    nav ul { gap: 0.25rem; flex-wrap: wrap; }
    nav a { padding: 0.3rem 0.5rem; font-size: 0.8rem; }
    .header-container { height: auto; padding: 0.5rem 1rem; flex-wrap: wrap; }
}

/* ===================== 动画 ===================== */
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideDown { from { transform: translate(-50%, -20px); opacity: 0; }
                        to   { transform: translate(-50%, 0);       opacity: 1; } }
```

### 10.8 index.html 骨架（必须包含的 CDN 引用）

**文件位置**: `pethealth-web/src/main/resources/static/index.html`

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>🐾 PetHealth · 宠物健康管家</title>

    <!-- 图表库 ECharts -->
    <script src="https://cdn.jsdelivr.net/npm/echarts@5.4.3/dist/echarts.min.js"></script>
    <!-- 图标库 Lucide -->
    <script src="https://unpkg.com/lucide@latest"></script>

    <!-- 核心样式（10.7 节的完整 CSS 内容直接粘到 styles.css 里） -->
    <link rel="stylesheet" href="styles.css">
</head>
<body>
    <!-- 10.5 节的导航栏 HTML 粘到这里 -->

    <main>
        <!-- ====== 首页 ====== -->
        <section id="home" class="active">
            <h2>🏠 首页</h2>
            <div class="dashboard-grid">
                <div class="card-glow">
                    <h3>🐾 我的宠物</h3>
                    <div id="home-pets" class="pets-grid"></div>
                </div>
                <div>
                    <div class="card-glow" style="margin-bottom:1rem">
                        <h3>🔥 热门社区</h3>
                        <div id="hot-posts-list"></div>
                    </div>
                    <div class="card-glow">
                        <h3>⏰ 即将到期提醒</h3>
                        <div id="due-reminders-list"></div>
                    </div>
                </div>
            </div>
        </section>

        <!-- ====== 宠物档案 ====== -->
        <section id="pets">
            <h2>🐾 宠物档案</h2>
            <div id="pets-container" class="pets-grid"></div>
        </section>

        <!-- ====== 健康记录 ====== -->
        <section id="health-records">
            <h2>📊 健康记录</h2>
            <div id="trend-chart"></div>
        </section>

        <!-- ====== AI 健康助手 ====== -->
        <section id="ai-diagnosis">
            <h2>🤖 AI 健康助手</h2>
            <div class="card-glow">
                <div class="form-group">
                    <label>选择宠物</label>
                    <select id="diag-pet-select">
                        <option value="">（示例）豆豆 · 英短 · 18个月</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>描述症状</label>
                    <textarea id="diag-symptoms" rows="4"
                        placeholder="例如：今天没精神，不爱动，也不太吃东西，还偶尔打喷嚏"></textarea>
                </div>
                <div class="form-group">
                    <label>持续时间</label>
                    <select id="diag-duration">
                        <option>半天</option><option selected>1天</option>
                        <option>2-3天</option><option>一周以上</option>
                    </select>
                </div>
                <button class="btn btn-primary" onclick="submitDiagnosis()">🔍 开始 AI 诊断</button>
                <div id="ai-result"></div>
            </div>
        </section>

        <!-- ====== 社区 ====== -->
        <section id="community">
            <h2>💬 宠物社区</h2>
            <div id="posts-container"></div>
        </section>

        <!-- ====== 营养助手 ====== -->
        <section id="nutrition">
            <h2>🥗 营养助手</h2>
            <select id="nt-pet-select" onchange="loadNutritionReport()">
                <option value="">请选择宠物</option>
            </select>
            <div id="nutrition-container"></div>
        </section>

        <!-- ====== 提醒 ====== -->
        <section id="reminders">
            <h2>⏰ 提醒中心</h2>
            <div id="reminders-container"></div>
        </section>

        <!-- ====== 统计 ====== -->
        <section id="statistics">
            <h2>📈 统计</h2>
            <div id="stats-container" class="stat-grid"></div>
        </section>
    </main>

    <!-- 核心脚本（10.6 节的完整 JS 内容直接粘到 script.js 里） -->
    <script src="script.js"></script>
</body>
</html>
```

---

## 11. 配置文件模板

### 11.1 pethealth-web 的 application.yml（主入口，单体）

```yaml
server:
  port: 8080
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true

spring:
  main:
    allow-circular-references: true
  application:
    name: PetHealth
  data:
    mongodb:
      host: localhost
      port: 27017
      database: pethealth_web
    redis:
      host: localhost
      port: 6379
      database: 0
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms

# Dubbo Consumer —— 调用各微服务
dubbo:
  application:
    name: pethealth-web
  registry:
    address: N/A    # 本地直连，生产环境改成 nacos://localhost:8848
  consumer:
    timeout: 5000
    retries: 0
    check: false
    reference:
      PetDubboService:
        url: dubbo://localhost:20881/com.pethealth.service.dubbo.PetDubboService
        check: false
      HealthRecordDubboService:
        url: dubbo://localhost:20885/com.pethealth.service.dubbo.HealthRecordDubboService
        check: false
      AIDiagnosisDubboService:
        url: dubbo://localhost:20885/com.pethealth.service.dubbo.AIDiagnosisDubboService
        check: false
      ReminderDubboService:
        url: dubbo://localhost:20884/com.pethealth.service.dubbo.ReminderDubboService
        check: false
  qos:
    enable: false

# Zipkin 分布式追踪
spring.zipkin:
  base-url: http://localhost:9411
  sender.type: web

management:
  tracing:
    sampling:
      probability: 1.0

logging:
  level:
    org.springframework.data.mongodb: DEBUG
    com.pethealth: DEBUG
```

### 11.2 pet-service 的 application.yml（端口 8081）

```yaml
server:
  port: 8081

spring:
  application:
    name: pet-service
  data:
    mongodb:
      host: localhost
      port: 27017
      # 与 pethealth-web 共享同一份 pet_profiles 集合：
      # 保证 Dubbo 正常路径与 web 降级本地 Repository 读写的数据完全一致
      database: pethealth_web

dubbo:
  application:
    name: pet-service
  protocol:
    name: dubbo
    port: 20881
  registry:
    address: N/A    # 生产: nacos://localhost:8848
  provider:
    timeout: 5000
    retries: 0
  qos:
    enable: false

logging:
  level:
    org.springframework.data.mongodb: DEBUG
    com.pethealth: DEBUG
```

### 11.3 health-record-service 的 application.yml（端口 8086 + LLM）

```yaml
server:
  port: 8086

spring:
  application:
    name: health-record-service
  data:
    mongodb:
      host: localhost
      port: 27017
      # 统一使用 pethealth_web 库（与 pethealth-web 共享 health_records 集合）
      database: pethealth_web
    redis:
      host: localhost
      port: 6379
      database: 2

dubbo:
  application:
    name: health-record-service
  protocol:
    name: dubbo
    port: 20885
  registry:
    address: N/A
  provider:
    timeout: 10000
    retries: 0
  qos:
    enable: false

# DeepSeek AI 配置
llm:
  api-url: ${LLM_API_URL:https://api.deepseek.com/v1/chat/completions}
  api-key: ${LLM_API_KEY:your-api-key-here}
  model: ${LLM_MODEL:deepseek-v4-flash}
  max-tokens: 4096
  temperature: 0.7

logging:
  level:
    org.springframework.data.mongodb: DEBUG
    com.pethealth: DEBUG
```

> 已移除 `rabbitmq` 与 `zipkin` 配置（项目未使用 MQ 与链路追踪）。

### 11.4 reminder-service 的 application.yml（端口 8084）

```yaml
server:
  port: 8084

spring:
  application:
    name: reminder-service
  data:
    mongodb:
      host: localhost
      port: 27017
      # 统一使用 pethealth_web 库（与 pethealth-web 共享 reminders 集合）
      database: pethealth_web

dubbo:
  application:
    name: reminder-service
  protocol:
    name: dubbo
    port: 20884
  registry:
    address: N/A
  provider:
    timeout: 5000
  qos:
    enable: false

# 定时扫描频率（秒）
reminder:
  scheduler-interval-seconds: 60
  default-advance-days: 7   # 疫苗默认提前 7 天提醒

logging:
  level:
    org.springframework.data.mongodb: DEBUG
    com.pethealth: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
```

> 已移除 `rabbitmq` 与 `mail.*` 配置：提醒为应用内触达（无邮件/SMS），无 MQ 依赖。

### 11.5 营养助手配置说明

营养助手是 pethealth-web 的**内置功能**（本地聚合计算），无需独立服务与额外配置：

- 数据依赖：`pet_profiles`（含 `neutered` 字段）+ `health_records`（体重序列）
- 计算规则、系数表与代码位置见 4.10 节
- 演示数据：可用 `pethealth-web/fix-data/inject-health-data.ps1` 一键生成近 30 天、多类型、符合物种特征的健康记录

### 11.6 microservices 父 POM（版本管理）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project ...>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>
    <groupId>com.pethealth</groupId>
    <artifactId>pethealth-microservices</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    <modules>
        <module>../pet-service</module>
        <module>../health-record-service</module>
        <module>../reminder-service</module>
    </modules>
    <properties>
        <java.version>17</java.version>
        <dubbo.version>3.2.4</dubbo.version>
        <nacos.version>2.3.0</nacos.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <!-- Dubbo / Nacos / Spring Cloud 版本管理 -->
        </dependencies>
    </dependencyManagement>
</project>
```

### 11.7 环境变量速查

```bash
# LLM
export LLM_API_KEY=sk-xxx
export LLM_MODEL=deepseek-v4-flash

# 提醒为应用内触达（无邮件/SMS）；mail.* 配置仅为框架预留，未启用
# （如需启用邮件可配置 SMTP_HOST / SMTP_PORT / SMTP_USER / SMTP_PASSWORD / MAIL_FROM）
```

---

## 12. 分布式追踪与监控

### 12.1 Zipkin 链路追踪

每个服务都启用 Zipkin + Micrometer + Brave（算法设计简单直接，兼顾性能与可维护性）：

```xml
<!-- 每个微服务都加这些依赖 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

**链路示例（用户提交 AI 诊断）：**
```
[client ──►] pethealth-web:8080
            └── Dubbo ──► health-record-service:8086
                            └── HTTP ──► DeepSeek API
                            └── MongoDB ──► pethealth_web
```

### 12.2 各服务 Actuator 端点

默认 Spring Boot 3.x 暴露 `/actuator/health`、`/actuator/info`、`/actuator/prometheus`，方便 Prometheus 采集。

---

## 13. 部署指南

### 13.1 本地开发环境准备（Windows PowerShell）

> 所有依赖都**在本机直接安装**，不使用 Docker。
> 下面是在 PowerShell 中可以直接复制粘贴执行的**真实命令**。

```powershell
# ====== 1. 启动 MongoDB（端口 27017）======
# 使用项目根目录的 mongod.cfg（dbPath 已配置为项目内 .mongodb\data，数据隔离）
& "C:\Program Files\MongoDB\Server\8.2\bin\mongod.exe" --config "f:\code\pet-health\mongod.cfg"

# ====== 2. 启动 Redis（端口 6379）======
# Redis 已安装在 F:\Redis\ 并加入 PATH，直接运行
redis-server

# ====== 3. 无需 RabbitMQ（提醒已改为定时调度 + 应用内触达，无 MQ 依赖）======

# ====== 4. 无需 Nacos（Dubbo 采用直连模式，见 2.4）======

# ====== 5. 导入环境变量（同一 PowerShell 会话内生效）======
$env:LLM_API_KEY = "sk-your-key-here"
$env:LLM_MODEL   = "deepseek-v4-flash"
# 提醒为应用内触达，无需 SMTP 邮件配置
```

### 13.2 启动顺序（PowerShell）

本地开发可以**跳过所有微服务，只启动 pethealth-web**（把 Dubbo reference 的 check: false 保留，Controller 里加 fallback 返回模拟数据），这是一种快速出 Demo 的策略。

**完整启动顺序：**

```powershell
# ====== 前置依赖（必须先起）======
# MongoDB（27017）
& "C:\Program Files\MongoDB\Server\8.2\bin\mongod.exe" --config "f:\code\pet-health\mongod.cfg"

# Redis（6379）
redis-server

# ====== Step 1 — 启动所有微服务 Provider ======
Start-Process mvn -ArgumentList "spring-boot:run","-DskipTests" -WorkingDirectory "f:\code\pet-health\pet-service" -NoNewWindow
Start-Process mvn -ArgumentList "spring-boot:run","-DskipTests" -WorkingDirectory "f:\code\pet-health\health-record-service" -NoNewWindow
Start-Process mvn -ArgumentList "spring-boot:run","-DskipTests" -WorkingDirectory "f:\code\pet-health\reminder-service" -NoNewWindow

# ====== Step 2 — 启动主 Web 应用 ======
cd f:\code\pet-health
mvn spring-boot:run -pl pethealth-web

# ====== Step 3 — 打开浏览器 ======
# http://localhost:8080
```
**手动启动步骤**
```
# ① 前置依赖（13.1）
& "C:\Program Files\MongoDB\Server\8.2\bin\mongod.exe" --config "f:\code\pet-health\mongod.cfg"   # MongoDB 27017
redis-server                                                                                       # Redis 6379（已在跑可跳过）

# ② 微服务 Provider（13.2 Step 1）— 3 个终端分别执行
cd f:\code\pet-health\pet-service            ; mvn spring-boot:run -DskipTests   # 8081
cd f:\code\pet-health\health-record-service  ; mvn spring-boot:run -DskipTests   # 8086
cd f:\code\pet-health\reminder-service       ; mvn spring-boot:run -DskipTests   # 8084

# ③ 主 Web（13.2 Step 2）
cd f:\code\pet-health
mvn spring-boot:run -pl pethealth-web                                                 # 8080

# ④ 浏览器打开 http://localhost:8080
```

### 13.3 一键启动脚本（PowerShell）

```powershell
# run-all.ps1 — 在项目根目录执行
$services = @(
    @{Dir="pet-service"; Port=8081},
    @{Dir="health-record-service"; Port=8086},
    @{Dir="reminder-service"; Port=8084},
    @{Dir="pethealth-web"; Port=8080}
)

foreach ($s in $services) {
    Write-Host "Starting $($s.Dir) on port $($s.Port)..." -ForegroundColor Green
    Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run","-DskipTests" `
        -WorkingDirectory (Join-Path $PSScriptRoot $s.Dir) -NoNewWindow
}
```

### 13.4 阿里云 ECS 云服务器部署

#### 🎯 目标

把项目部署到公网服务器上，让面试官能直接访问：**http://121.43.118.128**

#### 🖥️ 服务器配置（已购买）

| 项目 | 值 |
|------|-----|
| 实例 ID | `i-bp1ilmjgd4sbyn872g70Z` |
| 地域 | 华东1（杭州）- H 可用区 |
| 公网 IP | `121.43.118.128` |
| 操作系统 | Ubuntu 22.04 LTS 64位 |
| CPU / 内存 | 2 vCPU / 2 GiB RAM |
| 系统盘 | ESSD 40 GiB |

#### ⚠️ 2核2G 内存约束 — 只跑单体

2G RAM 装完系统 + MongoDB + Redis 只剩不到 600MB，跑多个 Spring Boot 微服务肯定 OOM。**云服务器上只部署 `pethealth-web` 单体**（Dubbo reference 保持 `check: false`，相关 Controller 里加 try-catch 返回空数据或模拟数据）。微服务架构在本地演示即可，云服务器上展示核心功能。

#### Step 1 — SSH 登录服务器

```bash
# 本地 Windows PowerShell 执行
ssh root@121.43.118.128

# 如果有密钥文件：
ssh -i C:\path\to\your-key.pem root@121.43.118.128
```

#### Step 2 — 服务器初始化（一键执行）

SSH 进去后，**复制粘贴下面整段命令一次性执行完**。全部是 `apt` 官方源安装，不需要 Docker。

```bash
# ===== 系统更新 + 基础工具 =====
apt update && apt upgrade -y
apt install -y curl wget vim unzip git ufw gnupg lsb-release ca-certificates

# ===== JDK 17 =====
apt install -y openjdk-17-jdk
java -version    # 验证：openjdk version "17.0.x"

# ===== Maven 3.9 =====
cd /opt
wget https://dlcdn.apache.org/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz
tar -xzf apache-maven-3.9.6-bin.tar.gz
ln -s /opt/apache-maven-3.9.6 /opt/maven
cat >> /etc/profile << 'EOF'
export MAVEN_HOME=/opt/maven
export PATH=$MAVEN_HOME/bin:$PATH
EOF
source /etc/profile
mvn -v    # 验证

# ===== MongoDB 7.0 (官方源，Ubuntu 22.04 jammy) =====
curl -fsSL https://pgp.mongodb.com/server-7.0.asc | \
   gpg --dearmor -o /usr/share/keyrings/mongodb-server-7.0.gpg
echo "deb [ signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] http://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" \
   | tee /etc/apt/sources.list.d/mongodb-org-7.0.list
apt update
apt install -y mongodb-org

# 2G 内存限制：重写 mongod.conf（备份 → 覆盖，避免 YAML key 重复）
cp /etc/mongod.conf /etc/mongod.conf.bak
cat > /etc/mongod.conf << 'MONGOD_CONF'
systemLog:
  destination: file
  path: "/var/log/mongodb/mongod.log"
  logAppend: true
storage:
  dbPath: /var/lib/mongodb
  journal:
    enabled: true
  wiredTiger:
    engineConfig:
      cacheSizeGB: 0.25
      directoryPerDB: false
processManagement:
  fork: true
  pidFilePath: /var/run/mongod.pid
net:
  bindIp: 127.0.0.1
  port: 27017
# 注：先不开启 auth，等初始化完账号再启用（见下方 MongoDB 账号创建步骤）
MONGOD_CONF

systemctl enable mongod
systemctl start mongod
mongosh --eval "db.runCommand({connectionStatus: 1})"   # 验证

# ===== MongoDB —— 创建应用账号（重要！不要裸奔）=====
mongosh << 'MONGO_INIT'
use admin
db.createUser({
  user: "pethealth_app",
  pwd: "ChangeMe_Use_Strong_Password_123!",
  roles: [{ role: "dbOwner", db: "pethealth_web" }]
})
MONGO_INIT

# 账号创建成功后，开启 auth：在 mongod.conf 末尾追加 security 段
cat >> /etc/mongod.conf << 'AUTH_CONF'

security:
  authorization: enabled
AUTH_CONF
systemctl restart mongod
mongosh -u pethealth_app -p 'ChangeMe_Use_Strong_Password_123!' \
  --authenticationDatabase admin \
  pethealth_web --eval "db.runCommand({ping: 1})"   # 验证

# ===== Redis 7 (官方源，Ubuntu 22.04 = jammy) =====
curl -fsSL https://packages.redis.io/gpg | gpg --dearmor -o /usr/share/keyrings/redis-archive-keyring.gpg
echo "deb [ signed-by=/usr/share/keyrings/redis-archive-keyring.gpg ] https://packages.redis.io/deb jammy main" \
   | tee /etc/apt/sources.list.d/redis.list
apt update
apt install -y redis-server

# 2G 内存限制：用 sed 修改现有 redis.conf（避免追加导致的重复配置冲突）
cp /etc/redis/redis.conf /etc/redis/redis.conf.bak
sed -i 's/^# *bind 127.0.0.1$/bind 127.0.0.1/' /etc/redis/redis.conf
sed -i 's/^bind .*/bind 127.0.0.1/' /etc/redis/redis.conf
sed -i 's/^# *maxmemory .*$/maxmemory 256mb/' /etc/redis/redis.conf
sed -i 's/^# *maxmemory-policy .*$/maxmemory-policy allkeys-lru/' /etc/redis/redis.conf
sed -i 's/^maxmemory .*/maxmemory 256mb/' /etc/redis/redis.conf
sed -i 's/^maxmemory-policy .*/maxmemory-policy allkeys-lru/' /etc/redis/redis.conf

systemctl enable redis-server
systemctl restart redis-server
redis-cli ping    # 验证：返回 PONG

# ===== Nginx 反代 =====
apt install -y nginx
systemctl enable nginx
systemctl start nginx

# ===== 防火墙（ufw）=====
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# ===== 阿里云安全组（重要！还要去阿里云控制台）=====
# 控制台 → ECS → 安全组 → 添加入方向规则：
# - 自定义 TCP  80    0.0.0.0/0   (HTTP)
# - 自定义 TCP  443   0.0.0.0/0   (HTTPS 可选)
# - 自定义 TCP  22    你的本地IP/32  (SSH，限制来源更安全)
```

#### Step 3 — Maven 打包（在本地 Windows 执行）

```powershell
# 本地 Windows PowerShell，进入 pethealth-web 目录
cd F:\code\ydyy\lab11\pethealth-web
mvn clean package -DskipTests

# 打包完成后，jar 文件在 target/ 目录下
# 文件名类似：pethealth-web-1.0.0.jar
dir target\*.jar
```

#### Step 4 — 上传 jar 到服务器

```powershell
# 本地 Windows PowerShell
# 把 jar + application-ecs.yml 一起上传
scp target/pethealth-web-1.0.0.jar root@121.43.118.128:/opt/pethealth/
```

#### Step 5 — 写 ECS 专用的 application-ecs.yml（服务器本地）

```bash
# SSH 进去服务器
ssh root@121.43.118.128
mkdir -p /opt/pethealth/config
vim /opt/pethealth/config/application-ecs.yml
```

**粘贴下面内容（适配服务器环境）：**

```yaml
server:
  port: 8080

spring:
  application:
    name: PetHealth
  data:
    mongodb:
      host: 127.0.0.1
      port: 27017
      database: pethealth_web
      username: ${MONGO_USER:pethealth_app}
      password: ${MONGO_PASS:ChangeMe_Use_Strong_Password_123!}
      authentication-database: admin
    redis:
      host: 127.0.0.1
      port: 6379
      password: ${REDIS_PASS:}
      lettuce:
        pool:
          max-active: 4
          max-idle: 2
          min-idle: 0
          max-wait: 3000ms

# Dubbo Consumer —— 云服务器只跑单体，全部直连跳过
# check: false 表示 Provider 没启动也不会阻塞启动
dubbo:
  consumer:
    check: false
    timeout: 3000
    retries: 0

# JVM 内存：2G 机器给 Spring Boot 最多 512MB
server-tuning:
  max-http-header-size: 8192

llm:
  api-url: https://api.deepseek.com/v1/chat/completions
  api-key: ${LLM_API_KEY:}
  model: deepseek-v4-pro
  max-tokens: 2048
  temperature: 0.7

reminder:
  scheduler-interval-seconds: 120

logging:
  level:
    root: INFO
    com.pethealth: INFO
```

#### Step 6 — 创建 Systemd 服务（开机自启 + 进程守护）

```bash
vim /etc/systemd/system/pethealth.service
```

**粘贴下面内容：**

```ini
[Unit]
Description=PetHealth Spring Boot Application
# Requires 确保这些服务必须启动成功才能启动 pethealth
# After 确保启动顺序：网络 → MongoDB → Redis → PetHealth
Requires=mongod.service redis-server.service
After=network.target mongod.service redis-server.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/pethealth

# ===== 环境变量（敏感信息通过环境变量注入，不要硬编码到 jar 里）=====
Environment="SPRING_PROFILES_ACTIVE=ecs"
Environment="LLM_API_KEY=sk-your-actual-api-key-here"
Environment="MONGO_USER=pethealth_app"
Environment="MONGO_PASS=ChangeMe_Use_Strong_Password_123!"
Environment="JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 启动命令
ExecStart=/usr/bin/java $JAVA_OPTS \
    -Duser.timezone=Asia/Shanghai \
    -jar /opt/pethealth/pethealth-web-1.0.0.jar \
    --spring.config.additional-location=/opt/pethealth/config/

# 进程守护：崩溃自动重启
Restart=on-failure
RestartSec=10

# 日志（journalctl -u pethealth 查看）
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

```bash
# 启用并启动
systemctl daemon-reload
systemctl enable pethealth
systemctl start pethealth

# 查看运行状态
systemctl status pethealth
# 查看实时日志
journalctl -u pethealth -f
```

#### Step 7 — Nginx 反向代理（端口 80 → 8080）

```bash
vim /etc/nginx/sites-available/pethealth
```

**粘贴下面内容：**

```nginx
server {
    listen 80;
    server_name 121.43.118.128;

    # 请求体大小限制（上传图片时需要）
    client_max_body_size 10m;

    # 后端 Spring Boot
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout    60s;
    }
}
```

```bash
# 启用站点 + 检查配置 + 重启
ln -sf /etc/nginx/sites-available/pethealth /etc/nginx/sites-enabled/
nginx -t              # 检查语法（必须显示 test is successful）
systemctl reload nginx
```

#### Step 8 — 一键验证部署

把下面脚本贴进服务器执行，一行出结果：

```bash
#!/bin/bash
# ===== 一键部署验证脚本 =====
echo "====== 1. 系统资源 ======"
free -h && echo ""
df -h / && echo ""
echo "====== 2. 各服务状态 ======"
systemctl is-active mongod redis-server nginx pethealth
echo "====== 3. MongoDB 连接（带认证）======"
mongosh -u pethealth_app -p 'ChangeMe_Use_Strong_Password_123!' \
  --authenticationDatabase admin pethealth_web \
  --eval "db.runCommand({ping:1, dbStats:1})"
echo "====== 4. Redis 连接 ======"
redis-cli ping
echo "====== 5. Spring Boot 健康检查 ======"
curl -s http://127.0.0.1:8080/api/statistics/home | head -c 300; echo ""
echo "====== 6. Nginx 反代测试 ======"
curl -s -o /dev/null -w "HTTP %{http_code} — %{remote_ip}\n" http://127.0.0.1/
echo "====== 7. 公网连通性 ======"
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://121.43.118.128/
echo "====== ✅ 验证完成 ======"
```

**全部通过 → 本地浏览器打开 http://121.43.118.128 能看到 PetHealth 首页 🎉**

#### Step 9（可选）— 绑定域名 + HTTPS

如果后面买了域名（比如 `pethealth-demo.com`）：

**A. 阿里云侧**
1. 域名 DNS 解析 → 添加 A 记录 → `pethealth-demo.com` → `121.43.118.128`
2. 阿里云免费证书 → 申请 DV 单域名证书（有效期 3 个月，可续）

**B. 服务器侧 — certbot 自动配置（推荐）**

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d pethealth-demo.com
# 会自动改 Nginx 配置，加好 SSL + HTTP→HTTPS 301 重定向
```

**C. 或者手动写 SSL 配置（展示技术深度用）**

```bash
# 把 /etc/nginx/sites-available/pethealth 改成：
cat > /etc/nginx/sites-available/pethealth << 'NGINX_SSL'
server {
    listen 80;
    server_name pethealth-demo.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name pethealth-demo.com;

    ssl_certificate     /etc/nginx/ssl/pethealth-demo.com.pem;
    ssl_certificate_key /etc/nginx/ssl/pethealth-demo.com.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 10m;

    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_read_timeout    60s;
    }
}
NGINX_SSL

mkdir -p /etc/nginx/ssl
# 把阿里云下载的证书文件放到 /etc/nginx/ssl/ 下
nginx -t && systemctl reload nginx
# 访问 https://pethealth-demo.com
```

---

#### 📋 服务器常用运维命令速查

```bash
# ===== 服务管理 =====
systemctl status pethealth          # 查看状态
systemctl restart pethealth         # 重启
systemctl stop pethealth            # 停止
journalctl -u pethealth -f          # 实时看日志
journalctl -u pethealth --since "1 hour ago"   # 查最近1小时日志

# ===== 更新部署（一键：本地打包 → 上传 → 重启）=====
# 本地 Windows PowerShell 执行：
scp target/pethealth-web-1.0.0.jar root@121.43.118.128:/opt/pethealth/
ssh root@121.43.118.128 "systemctl restart pethealth"

# ===== 内存排查 =====
free -h                              # 看总内存/已用
ps aux --sort=-%mem | head -10       # 谁最占内存
journalctl -u pethealth | grep -i "oom\|memory\|outofmemory"   # 查 OOM
dmesg | tail -30                     # 内核级 OOM 日志

# ===== MongoDB 备份/恢复 =====
# 手动备份
mongodump -u pethealth_app -p 'ChangeMe_Use_Strong_Password_123!' \
  --authenticationDatabase admin \
  --db pethealth_web \
  --out /opt/backup/$(date +%Y%m%d_%H%M)
# 恢复
mongorestore -u pethealth_app -p 'ChangeMe_Use_Strong_Password_123!' \
  --authenticationDatabase admin \
  /opt/backup/20260815_1000/pethealth_web/

# ===== MongoDB 定时备份 cron（每天凌晨 3 点，保留 7 天）=====
crontab -e
# 粘贴：
# 0 3 * * * mongodump -u pethealth_app -p 'ChangeMe_Use_Strong_Password_123!' --authenticationDatabase admin --db pethealth_web --out /opt/backup/$(date +\%Y\%m\%d) 2>&1 >> /var/log/mongo-backup.log && find /opt/backup -maxdepth 1 -type d -mtime +7 -exec rm -rf {} \;

# ===== 阿里云控制台（浏览器打开）=====
# https://ecs.console.aliyun.com → 实例 → 安全组 → 端口放行
```

---

#### 🚨 常见故障排查清单

| 现象 | 排查步骤 |
|------|----------|
| **systemctl start pethealth 后立即退出** | `journalctl -u pethealth -n 50` 看报错；常见：JDK 路径错、jar 路径错、内存不足 OOM |
| **应用启动了但连不上 MongoDB** | ① `mongosh -u pethealth_app -p 'xxx' pethealth_web` 手动连看能不能通；② 检查 mongod.conf 的 bindIp；③ 检查 auth 是否启用但 Spring Boot 没配密码 |
| **应用启动了但连不上 Redis** | ① `redis-cli ping` 看返回 PONG；② 检查 redis.conf 的 bind 和 requirepass；③ 检查 Spring Boot 的 redis.password |
| **502 Bad Gateway 打开网页** | ① `curl http://127.0.0.1:8080/api/statistics/home` 看 Spring Boot 活着没；② 看 nginx error.log `/var/log/nginx/error.log`；③ 常见：Spring Boot 还没启动完 Nginx 就转发了 |
| **页面能开但数据都是空的** | 检查 Dubbo Consumer 的 `check: false` 是否生效（云服务器微服务没跑，会返回空数据，属正常行为） |
| **内存不够 OOM** | ① `-Xmx` 调小到 384m；② MongoDB cacheSizeGB 调小到 0.128；③ Redis maxmemory 调到 128mb；④ 关了 swap（Ubuntu 默认开 swap，2G RAM + 2G swap = 4G 能用）|
| **MongoDB 授权报错 "Authentication failed"** | ① 用户名密码对不对；② `authenticationDatabase` 是不是 admin；③ 创建用户时的角色对不对 |

---

#### 🧠 简历上怎么写这段

> **阿里云 ECS 云部署（加分亮点）：**
> - Ubuntu 22.04 / 2核2G · 公网访问 http://121.43.118.128
> - JDK 17 + Maven 3.9 + MongoDB 7 + Redis 7 + Nginx（全部 apt 官方源，零 Docker）
> - **Systemd** 进程守护（Requires + After 强依赖链）+ JVM 内存调优（-Xmx512m 适配 2G 机器）+ MongoDB/Redis 缓存限额
> - **Nginx 反向代理**（HTTP/HTTPS 可选）+ 阿里云安全组 + UFW 双层防火墙
> - **MongoDB 账号体系**（application-only user）+ 定时 `mongodump` cron 备份（保留 7 天）
> - **一键更新**：本地打包 → scp 上传 → `systemctl restart`
> - **2G 内存极限优化**：MongoDB cacheSizeGB=0.25 + Redis maxmemory=256mb + 开启 swap

---

## 14. 开发路线图

> 按优先级排序，**先让 pethealth-web 单体跑起来**，再拆微服务，这样你能最快看到效果。

### Phase 1 — 最小可运行 Demo（1-2 天）

- [ ] 用第 3.1 节的目录结构，在 `pethealth-web/` 下创建一个全新的 Maven 项目（groupId `com.pethealth`，artifactId `pethealth-web`）
- [ ] 写主启动类 `PetHealthApplication.java`（`@SpringBootApplication` + `@EnableScheduling`）
- [ ] 写 User / Post / Reply / Comment / Like 实体 + MongoDB Repository（参考第 5.3 节和附录 A 的 collection 名）
- [ ] 写 PetProfile 实体 + PetProfileRepository（参考第 5.1 节完整代码，嵌套数组先写空 List）
- [ ] 写 HealthRecord 实体 + HealthRecordRepository（参考第 5.2 节）
- [ ] Controller 层：UserController、PostController、ReplyController、CommentController、LikeController、PetController、HealthRecordController
- [ ] 前端三件套：根据第 10.5-10.8 节完整代码创建 index.html / script.js / styles.css（直接复制粘贴，改一下标题和 logo）
- [ ] 配置好 MongoDB + Redis（第 11.1 节的 application.yml），启动 pethealth-web，浏览器访问 http://localhost:8080
- [ ] **验证：** 能注册登录（demo/123456）、发帖回复点赞、看到首页热门榜、ECharts 示例图表能显示

### Phase 2 — AI 健康助手（1 天）

- [ ] 在 `health-record-service/` 下创建独立 Maven 模块（第 3.1 节目录结构）
- [ ] 把第 9.2 节的 **完整 LLMClient.java** 复制进去，不需要改一行代码
- [ ] 把第 10.6 节的 AI 诊断 UI 粘到 index.html 里，调用 `/api/ai-diagnosis`
- [ ] 写 `AIDiagnosisController`，内部调 `LLMClient.diagnose(species, breed, ageMonths, symptoms, duration)`
- [ ] **验证：** 输入"我家猫今天没精神，不爱动"，AI 返回可能原因 + 建议 + 危险信号（如果没配 LLM_API_KEY，会抛异常，检查日志）

### Phase 3 — Redis 统计缓存 + 热门榜（1 天）

- [ ] 把第 5.5.5 节的 **PostRankService 完整代码** 粘进去（Redis Sorted Set 实现热门榜）
- [ ] 在 PostController 的发帖、点赞、回复接口里，加上 `postRankService.updateScore(postId)` 调用
- [ ] 热门榜分数公式：`like×3 + reply×5 + view×1`（第 5.5.5 节已实现）
- [ ] HealthRecord 的周/月聚合统计 → Redis Hash 缓存（参考第 4.12 节 Redis 键设计）
- [ ] **验证：** 首页热门榜显示 Top 5，帖子点赞后热度实时变化，5 分钟后定时全量同步

### Phase 4 — 提醒系统（应用内定时调度，已实现）

> **已实现**，无需 RabbitMQ。提醒采用 `ReminderScheduler` 定时扫描 + `findAndModify` 原子抢占（见 5.5.7 节），状态流转为 PENDING → SENT → ACKNOWLEDGED / CANCELLED。

### Phase 5 — Dubbo 微服务拆分（2-3 天）

- [ ] 按第 3 节目录结构，把 Pet / HealthRecord / Reminder 各拆成独立 Maven 模块
- [ ] Provider 侧实现 Dubbo 接口 + 启动类 + application.yml
- [ ] Consumer 侧（pethealth-web）声明 Dubbo reference
- [ ] **验证：** 单独启动每个微服务，pethealth-web 能通过 Dubbo 正常调用

### Phase 6 — 营养助手（1 天）

- [ ] `NutritionCalculator`：RER/MER 公式 + 物种/年龄/绝育系数规则表（纯静态类，配 JUnit 测试）
- [ ] `NutritionService` 聚合 pet_profiles + health_records（最新体重 + 体重序列）
- [ ] `NutritionController`：GET `/api/nutrition/{petId}`（支持 `?bcs=`）
- [ ] 前端营养助手页：宠物选择 → RER/MER/喂食克数 + 体重趋势图（ECharts）+ BCS 体重管理
- [ ] 宠物档案表单补充"是否绝育"字段（`pet_profiles.neutered`）
- [ ] **验证：** 选择已录体重的猫/犬，返回 RER/MER 与喂食克数；鹦鹉等非猫犬物种正确提示"仅支持猫/犬"

### Phase 7 — 打磨 + 生产化（1 天）

- [ ] Zipkin 分布式追踪（每个微服务都加，第 12 节）
- [ ] Actuator health 端点 + Prometheus 指标
- [ ] 统一异常处理 `GlobalExceptionHandler`
- [ ] 输入参数校验（`@Valid` + JSR 303）
- [ ] 登录 Session 管理（Redis Token）
- [ ] 所有微服务都能独立 `mvn spring-boot:run` 启动成功

### Phase 8 — 可选加分项（有时间再做）

- [ ] 用户头像上传（本地或 OSS）
- [ ] 微信小程序端（Taro 框架）
- [ ] 宠物走失寻宠板块
- [ ] 宠物商品 / 服务推荐（AI 个性化）

---

## 附录 A：MongoDB 初始化脚本示例

```javascript
// mongo-init.js — 首次启动时在 mongo shell 中执行
use pethealth

// 用户表索引
db.users.createIndex({ "username": 1 }, { unique: true })
db.users.createIndex({ "email": 1 }, { unique: true })

// 宠物档案索引
db.pet_profiles.createIndex({ "ownerId": 1 })
db.pet_profiles.createIndex({ "vaccines.nextDueAt": 1 })
db.pet_profiles.createIndex({ "dewormings.nextDueAt": 1 })

// 健康记录索引
db.health_records.createIndex({ "petId": 1, "recordedAt": -1 })
db.health_records.createIndex({ "ownerId": 1 })

// 社区帖子索引
db.posts.createIndex({ "authorId": 1 })
db.posts.createIndex({ "category": 1, "createdAt": -1 })
db.posts.createIndex({ "tags": 1 })

// 点赞防重复 unique
db.likes.createIndex({ "userId": 1, "targetType": 1, "targetId": 1 }, { unique: true })

// 提醒状态 + 时间
db.reminders.createIndex({ "ownerId": 1, "status": 1 })
db.reminders.createIndex({ "remindAt": 1, "status": 1 })

// === Demo 数据 ===
db.users.insertMany([
  { _id: ObjectId(), username: "demo", password: "$2a$10$...BCrypt...", email: "demo@pethealth.com", createdAt: new Date() }
])

db.pet_profiles.insertMany([
  {
    _id: ObjectId(), ownerId: "xxx", ownerName: "demo",
    name: "豆豆", species: "DOG", breed: "柯基", gender: "MALE",
    birthday: ISODate("2022-03-15"),
    vaccines: [
      { id: ObjectId(), name: "狂犬疫苗", vaccinatedAt: ISODate("2025-05-01"), nextDueAt: ISODate("2027-05-01"), vetClinic: "爱宠宠物医院" }
    ],
    dewormings: [
      { id: ObjectId(), type: "INTERNAL", medicine: "拜宠清", dewormedAt: ISODate("2026-04-01"), nextDueAt: ISODate("2026-07-01") }
    ],
    createdAt: new Date()
  }
])
```

## 附录 B：pom.xml 完整依赖模板

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <!-- MongoDB -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-pool2</artifactId>
    </dependency>
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <!-- Security (BCrypt) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <!-- Dubbo -->
    <dependency>
        <groupId>org.apache.dubbo</groupId>
        <artifactId>dubbo-spring-boot-starter</artifactId>
    </dependency>
    <!-- 监控指标（Prometheus，仅指标，无链路追踪） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

> **说明**：以上为 `pethealth-web` 的关键依赖。`okhttp` / `fastjson2`（LLM HTTP 调用）位于 `health-record-service`，不属于 pethealth-web；项目**未使用** Zipkin / `micrometer-tracing-bridge-brave` 链路追踪，也无 `spring-boot-starter-amqp` / `spring-boot-starter-mail`。
    <dependency>
        <groupId>io.zipkin.reporter2</groupId>
        <artifactId>zipkin-reporter-brave</artifactId>
    </dependency>
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

**🎉 文档结束。祝开发顺利！记得：先让单体跑起来（Phase 1），再拆微服务，这样你能最快看到效果。**