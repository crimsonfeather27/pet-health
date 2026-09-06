# PetHealth · 宠物健康管家

一站式宠物健康管理平台：宠物档案管理、健康数据追踪、疫苗 / 驱虫智能提醒、AI 辅助诊断、养宠社区与营养助手。

## 功能特性

- **宠物档案**：多宠物管理，支持疫苗、驱虫、体检等动态字段扩展
- **健康记录**：体重 / 体温 / 饮食 / 运动量等指标记录，ECharts 趋势图表可视化
- **提醒系统**：疫苗到期、驱虫、体检提醒，RabbitMQ 延迟队列 + 应用内通知 / 邮件
- **AI 健康助手**：症状描述 → DeepSeek AI 初步诊断建议与就医指引
- **宠物社区**：经验分享、问答互动、嵌套评论、点赞与 Redis 热门榜
- **营养助手**：基于 NRC / WSAVA 公式计算每日能量与喂食量，支持 BCS 体重管理

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.0 |
| 微服务 | Apache Dubbo | 3.2.4 |
| 注册中心 | Nacos（可选） | 2.3.0 |
| Spring Cloud | Spring Cloud | 2023.0.0 |
| 数据库 | MongoDB | 6.x |
| 缓存 | Redis | - |
| 消息队列 | RabbitMQ | - |
| AI | DeepSeek LLM API | - |
| 前端 | HTML + CSS + JavaScript（ECharts） | - |
| 构建 | Maven | - |

## 系统架构

本地演示采用 Dubbo 直连（`registry: N/A`），不使用注册中心；各服务共享同一 MongoDB 库 `pethealth_web`。

| 模块 | 职责 | HTTP 端口 | Dubbo 端口 |
|------|------|-----------|-----------|
| pethealth-web | Web 网关 / 前端静态资源 / 业务聚合 | 8080 | 消费方 |
| pet-service | 宠物档案服务 | 8081 | 20881 |
| health-record-service | 健康记录与 AI 诊断服务 | 8086 | 20885 |
| reminder-service | 提醒服务 | 8084 | 20884 |

> pethealth-web 内已集成各服务的本地降级 Repository 实现，Dubbo 不可用时自动降级为本地直连，保证功能可用。

## 目录结构

```
pet-health
├── pom.xml                        # 父 POM（统一依赖版本）
├── microservices/                 # 微服务父 POM
│   └── pom.xml
├── pet-service/                   # 宠物档案服务
├── health-record-service/         # 健康记录 / AI 诊断服务
├── reminder-service/              # 提醒服务
├── pethealth-web/                 # Web 应用（前端 + 业务聚合）
│   └── src/main/resources/static/ # 前端页面（index.html / styles.css / script.js）
├── PETHEALTH_DESIGN.md            # 完整项目设计文档
└── cankao.md
```

## 环境要求

- JDK 17+
- Maven 3.6+
- MongoDB 6.x（默认端口 27017，库 `pethealth_web`）
- Redis（默认端口 6379）
- RabbitMQ（可选，默认 guest/guest，可通过 `RABBITMQ_ENABLED=false` 关闭降级运行）

## 快速开始

1. 启动基础设施：MongoDB、Redis，可选 RabbitMQ。
2. 在项目根目录编译安装基础依赖：

   ```bash
   mvn clean install -DskipTests
   ```

3. 依次启动微服务（顺序无关，web 最后启动即可）：

   ```bash
   # 宠物档案服务
   mvn -pl pet-service spring-boot:run

   # 健康记录服务
   mvn -pl health-record-service spring-boot:run

   # 提醒服务
   mvn -pl reminder-service spring-boot:run

   # Web 应用
   mvn -pl pethealth-web spring-boot:run
   ```

4. 浏览器访问 <http://localhost:8080>。

## 配置说明

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `LLM_API_KEY` | DeepSeek API Key（AI 诊断） | 空 |
| `LLM_MODEL` | LLM 模型名 | `deepseek-chat` |
| `RABBITMQ_ENABLED` | 是否启用 RabbitMQ | `false` |
| `DUBBO_ENABLED` | 是否启用 Dubbo 远程调用 | `true` |
| `SMTP_HOST` / `SMTP_USER` / `SMTP_PASSWORD` | 邮件提醒 SMTP 配置 | 空 |

## 详细文档

完整设计与接口文档见 [PETHEALTH_DESIGN.md](PETHEALTH_DESIGN.md)。
