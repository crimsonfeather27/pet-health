# PetHealth · 宠物健康管家

> 这是一个个人学习项目，代码公开仅供查看和参考。

一站式宠物健康管理平台：宠物档案管理、健康数据追踪、疫苗 / 驱虫智能提醒、AI 辅助诊断、养宠社区与营养助手。

## 功能特性

- **宠物档案**：多宠物管理，支持疫苗、驱虫、体检等动态字段扩展
- **健康记录**：体重 / 体温 / 饮食 / 运动量等指标记录，ECharts 趋势图表可视化
- **提醒系统**：疫苗到期、驱虫、体检提醒，定时调度 + `findAndModify` 原子抢占，应用内通知（无 MQ / 无邮件）
- **AI 健康助手**：症状描述 → DeepSeek AI 初步诊断建议与就医指引；API Key 可在前端弹窗填写（仅存浏览器），未配置时自动降级内置规则引擎
- **宠物社区**：经验分享、问答互动、回复、点赞与 Redis 热门榜
- **营养助手**：基于 NRC / WSAVA 公式计算每日能量与喂食量，支持 BCS 体重管理

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.0 |
| 微服务 | Apache Dubbo | 3.2.4 |
| 注册中心 | Nacos（可选） | 2.3.0 |
| Spring Cloud | Spring Cloud | 2023.0.0 |
| 数据库 | MongoDB | 8.x |
| 缓存 | Redis | - |
| AI | DeepSeek LLM API（Key 由前端按请求透传，服务端不内置） | - |
| **前端框架** | **Vue 3 + Vite + TypeScript** | **3.5.13 + 6.0.5 + 5.6.3** |
| **状态管理** | **Pinia** | **2.3.0** |
| **路由** | **Vue Router** | **4.5.0** |
| **HTTP 客户端** | **Axios** | **1.7.9** |
| **图表** | **ECharts + vue-echarts** | **5.5.1 + 7.0.3** |
| **图标** | **lucide-vue-next** | **0.469.0** |
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
├── pethealth-api/                 # 共享模块（实体 / Dubbo 接口 / ApiResponse）
├── pet-service/                   # 宠物档案服务
├── health-record-service/         # 健康记录 / AI 诊断服务
├── reminder-service/              # 提醒服务
├── pethealth-web/                 # Web 应用（前端 + 业务聚合）
│   ├── frontend/                  # Vue 3 前端源码（独立 Vite 项目）
│   │   ├── package.json
│   │   ├── vite.config.ts         # Vite 配置：dev 代理 /api→8080，build 输出到后端 static
│   │   └── src/
│   │       ├── main.ts            # Vue 入口：createApp + Pinia + Router
│   │       ├── App.vue            # 根组件：导航栏 + RouterView + 登录/注册 Modal
│   │       ├── api/index.ts       # axios 封装（withCredentials + ApiResponse 拦截）
│   │       ├── router/index.ts    # Vue Router（hash 模式，9 条路由）
│   │       ├── stores/            # Pinia 状态（user.ts / pets.ts）
│   │       ├── composables/       # 组合式函数（useUnreadBadge）
│   │       ├── styles/global.css  # 全局样式（CSS 变量 + 通用类）
│   │       ├── components/        # 共享组件（AppChart / AppToast / PostFormModal / ProfileModal）
│   │       └── views/             # 9 个视图（HomeView / PetsView / HealthRecordsView / ...）
│   └── src/main/resources/static/ # Vite 构建产物输出目录（index.html + assets/）
├── PETHEALTH_DESIGN.md            # 完整项目设计文档
└── cankao.md
```

## 环境要求

- JDK 17+
- Maven 3.6+
- Node.js 18+（仅前端开发需要，生产构建已内嵌到 Maven 流程）
- MongoDB 8.x（默认端口 27017，库 `pethealth_web`）
- Redis（默认端口 6379）
- 无需 RabbitMQ / Nacos（Dubbo 采用直连模式；提醒为应用内定时调度）

## 快速开始

### 方式一：生产模式（推荐）

前端已预构建并打包进 `pethealth-web`，直接启动后端即可：

1. 启动基础设施：MongoDB、Redis。
2. 在项目根目录编译安装基础依赖（含 pethealth-api 共享模块）：

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

### 方式二：开发模式（前后端分离热更新）

如需修改前端代码并实时预览：

1. 启动基础设施：MongoDB、Redis。
2. 启动后端微服务（同上，web 最后启动）。
3. 启动前端开发服务器（端口 5173，自动代理 `/api` 到 8080）：

   ```bash
   cd pethealth-web/frontend
   npm install
   npm run dev
   ```

4. 浏览器访问 <http://localhost:5173>。

> 修改前端后如需更新生产构建，运行 `npm run build`（输出到 `pethealth-web/src/main/resources/static/`），然后重新打包或重启 `pethealth-web`。

## 配置说明

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `LLM_API_KEY` | DeepSeek API Key（可选；不设置时可在前端"设置 API Key"弹窗输入，均无则走内置规则引擎） | 空 |
| `LLM_MODEL` | LLM 模型名 | `deepseek-v4-flash` |
| `DUBBO_ENABLED` | 是否启用 Dubbo 远程调用 | `true` |

> **AI Key 的两种提供方式**（优先级：请求头 ＞ 环境变量 ＞ 规则引擎）：
> 1. 前端方式（推荐用于本地/演示）：页面"AI 健康助手 → 设置 API Key"输入，仅保存在浏览器
>    localStorage（键 `pethealth:llm-api-key`），经请求头 `X-LLM-Api-Key` 透传，仓库与服务端均不存储；
> 2. 服务端方式（部署兜底）：设置环境变量 `LLM_API_KEY`。
>
> dev 环境内置测试账号：`demo / 123456`、`admin / admin123`。

## 详细文档

完整设计与接口文档见 [PETHEALTH_DESIGN.md](PETHEALTH_DESIGN.md)。
