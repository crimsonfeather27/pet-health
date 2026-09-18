# PetHealth 阿里云 ECS 部署操作手册

> 目标读者：第一次把本项目部署到公网服务器的开发者。
> 按本文档**从上到下顺序执行**，预计耗时 40~60 分钟。
>
| 项 | 值 |
|----|-----|
| 公网访问地址 | http://121.43.118.128 |
| 实例 ID | `i-bp1ilmjgd4sbyn872g70Z` |
| 地域 / 可用区 | 华东1（杭州）/ H |
| 操作系统 | Ubuntu 22.04 LTS 64 位 |
| 规格 | 2 vCPU / 2 GiB RAM / ESSD 40 GiB |

---

## 0. 部署方案总览（先读，决定部署形态）

### 0.1 为什么不是"只跑一个 jar"

pethealth-web 虽然内置了宠物档案、健康记录、提醒、社区等功能的**本地 Repository 降级实现**（Dubbo 不可用时自动直连 MongoDB），但 **AI 健康助手的 LLM 调用与规则引擎只存在于 health-record-service**：

- `AIDiagnosisController` 是纯 Dubbo 适配层，Provider 缺失时直接返回 `503 AI 诊断服务暂不可用`
- 因此服务器上需要**同时运行 pethealth-web + health-record-service 两个进程**，AI 功能才可用

其余两个微服务（pet-service、reminder-service）**不部署**：

| 功能 | 服务器上由谁提供 |
|------|------------------|
| 页面 / 社区 / 点赞 / 通知 | pethealth-web 本地实现 |
| 用户认证（Redis Token + Cookie） | pethealth-web 本地实现 |
| 宠物档案 CRUD | pethealth-web 本地 Repository（Dubbo 失败自动降级） |
| 健康记录 CRUD / 周月统计 | pethealth-web 本地实现（统计服务 web 内有副本） |
| 到期提醒调度 | pethealth-web 内的 `ReminderScheduler`（`findAndModify` 原子抢占） |
| **AI 诊断（DeepSeek / 规则引擎）** | **health-record-service（Dubbo 端口 20885，必须部署）** |
| AI 健康周报/月报 | health-record-service |

> Dubbo 直连地址在注解中硬编码为 `dubbo://localhost:20885/...`，两个进程同机部署无需改代码；
> `check=false` 保证任一方先启动都不会启动失败。

### 0.2 内存预算（2 GiB 机器能跑，但必须控制）

| 组件 | 内存上限 | 控制手段 |
|------|----------|----------|
| Ubuntu 系统基线 | ~300 MB | — |
| MongoDB | wiredTiger cache 0.25 GB，RSS 约 400 MB | mongod.conf |
| Redis | maxmemory 200 MB | redis.conf |
| pethealth-web JVM | `-Xms256m -Xmx512m`，实际约 600~700 MB | systemd JAVA_OPTS |
| health-record-service JVM | `-Xms128m -Xmx256m`，实际约 350 MB | systemd JAVA_OPTS |
| **合计** | **约 1.8~2.0 GB** | 另建 **2 GB swap** 兜底，防突发 OOM |

> 如果上线后观察到频繁 OOM Kill，备选方案见 [§10 故障排查](#10-故障排查)，
> 极端情况下可停掉 health-record-service 保核心功能（AI 变为 503，其余正常）。

### 0.3 端口规划（全部内网，仅 22/80 对公网开放）

| 端口 | 进程 | 是否对公网开放 |
|------|------|----------------|
| 22 | SSH | ✅（安全组建议限制来源 IP） |
| 80 | Nginx → 8080 | ✅ |
| 8080 | pethealth-web HTTP | ❌ 仅 127.0.0.1 |
| 8086 | health-record-service HTTP（actuator） | ❌ 仅 127.0.0.1 |
| 20885 | health-record-service Dubbo | ❌ 仅 127.0.0.1 |
| 27017 | MongoDB（bind 127.0.0.1，无密码） | ❌ |
| 6379 | Redis（bind 127.0.0.1，无密码） | ❌ |

### 0.4 部署架构

```
公网用户 ──HTTP:80──> Nginx ──proxy_pass──> pethealth-web:8080
                                              │
                                   本地 MongoDB / Redis（db0）
                                              │
                                              │ Dubbo 直连 localhost:20885
                                              ▼
                                   health-record-service:8086
                                      ├─ Redis db2（统计缓存）
                                      ├─ MongoDB pethealth_web（共享库）
                                      └─ HTTPS ──> api.deepseek.com
                                            （Key 由用户浏览器 X-LLM-Api-Key 透传，
                                             或服务端 LLM_API_KEY 环境变量兜底）
```

---

## 1. 阶段一：阿里云控制台准备

### 1.1 安全组放行端口

ECS 控制台 → 实例 → 安全组 → 配置规则 → **入方向** 添加：

| 协议类型 | 端口 | 授权对象 | 说明 |
|----------|------|----------|------|
| 自定义 TCP | 80/80 | 0.0.0.0/0 | HTTP（网站访问） |
| 自定义 TCP | 22/22 | 你的办公 IP/32 | SSH（**不建议**对 0.0.0.0 开放） |

> 不要放行 8080、8086、20885、27017、6379。
> 后续要上 HTTPS 时再放行 443。

### 1.2 获取服务器登录凭证

- 若创建实例时绑定了密钥对：准备好 `.pem` 私钥文件
- 若使用密码：控制台 → 实例 → 重置实例密码（记牢，下文用 `<服务器密码>` 代替）

### 1.3 本地 SSH 连通性验证

在本地 Windows PowerShell 执行：

```powershell
ssh root@121.43.118.128
# 首次连接输入 yes，再输入密码；看到 Ubuntu 欢迎信息即成功，输入 exit 退出
```

---

## 2. 阶段二：服务器基础环境初始化

SSH 登录后，按顺序执行以下脚本。**建议分步执行并确认每步的验证命令输出**。

### 2.1 系统更新 + 基础工具 + 2GB swap

```bash
apt update && apt upgrade -y
apt install -y curl wget vim gnupg lsb-release ca-certificates ufw

# ===== 2GB swap（2G 内存机器必须做，防 JVM 突发被 OOM Kill）=====
if [ ! -f /swapfile ]; then
  fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
  # 降低 swap 使用倾向，优先用物理内存
  sysctl vm.swappiness=10
  echo 'vm.swappiness=10' >> /etc/sysctl.conf
fi
free -h    # 验证：Swap 行显示 2.0Gi
```

### 2.2 安装 JDK 17

```bash
apt install -y openjdk-17-jdk-headless
java -version    # 期望输出 openjdk version "17.0.x"
```

> 不在服务器上打包，因此**不需要安装 Maven**（jar 在本地构建后上传）。

### 2.3 安装 MongoDB 8.x（官方源，Ubuntu 22.04 = jammy）

```bash
curl -fsSL https://pgp.mongodb.com/server-8.0.asc | \
  gpg -o /usr/share/keyrings/mongodb-server-8.0.gpg --dearmor
echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-8.0.gpg ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/8.0 multiverse" \
  > /etc/apt/sources.list.d/mongodb-org-8.0.list
apt update
apt install -y mongodb-org
```

按 2G 内存约束重写配置（先备份）：

```bash
cp /etc/mongod.conf /etc/mongod.conf.bak
cat > /etc/mongod.conf << 'MONGOD_CONF'
systemLog:
  destination: file
  path: "/var/log/mongodb/mongod.log"
  logAppend: true
storage:
  dbPath: /var/lib/mongodb
  wiredTiger:
    engineConfig:
      cacheSizeGB: 0.25
net:
  bindIp: 127.0.0.1
  port: 27017
MONGOD_CONF

systemctl enable mongod
systemctl start mongod
systemctl is-active mongod    # 应为 active
```

### 2.4 验证 MongoDB

```bash
mongosh --eval "db.runCommand({ping:1})"    # 期望 ok: 1
```

### 2.5 安装 Redis 7.x（官方源）+ 内存上限

```bash
curl -fsSL https://packages.redis.io/gpg | \
  gpg -o /usr/share/keyrings/redis-archive-keyring.gpg --dearmor
echo "deb [ signed-by=/usr/share/keyrings/redis-archive-keyring.gpg ] https://packages.redis.io/deb jammy main" \
  > /etc/apt/sources.list.d/redis.list
apt update
apt install -y redis-server

cp /etc/redis/redis.conf /etc/redis/redis.conf.bak
sed -i 's/^bind .*/bind 127.0.0.1/' /etc/redis/redis.conf
sed -i 's/^# *maxmemory .*/maxmemory 200mb/'          /etc/redis/redis.conf
sed -i 's/^maxmemory .*/maxmemory 200mb/'             /etc/redis/redis.conf
sed -i 's/^# *maxmemory-policy .*/maxmemory-policy allkeys-lru/' /etc/redis/redis.conf
sed -i 's/^maxmemory-policy .*/maxmemory-policy allkeys-lru/'    /etc/redis/redis.conf
# 不设 requirepass（bind 127.0.0.1 + 安全组未放行 6379，仅本机可访问）

systemctl enable redis-server
systemctl restart redis-server
redis-cli ping    # 期望 PONG
```

> 两个服务共用同一 Redis 实例：pethealth-web 用 **db0**，health-record-service 用 **db2**，互不干扰。

### 2.6 安装 Nginx 与防火墙

```bash
apt install -y nginx
systemctl enable nginx
systemctl start nginx

ufw allow 22/tcp
ufw allow 80/tcp
ufw --force enable
ufw status    # 确认只放行了 22、80
```

---

## 3. 阶段三：本地打包（在你的 Windows 开发机执行）

两个 jar 都在根 reactor 中构建（pethealth-web 依赖共享模块 pethealth-api，必须带 `-am`）：

```powershell
# 本地 Windows PowerShell，项目根目录
cd d:\code\pethealth
mvn clean package -pl pethealth-web,health-record-service -am -DskipTests
```

产物确认（pom 中配置了 `finalName=${project.artifactId}`，产物不带版本号）：

```powershell
dir pethealth-web\target\pethealth-web.jar
dir health-record-service\target\health-record-service.jar
```

---

## 4. 阶段四：上传 jar 与目录规划

```powershell
# 本地 PowerShell 执行（本地产物本就不带版本号，直接上传同名文件）
scp pethealth-web\target\pethealth-web.jar root@121.43.118.128:/opt/pethealth/
scp health-record-service\target\health-record-service.jar root@121.43.118.128:/opt/pethealth/
```

> `/opt/pethealth` 目录不存在时，先 SSH 进去 `mkdir -p /opt/pethealth/config /opt/pethealth/logs` 再上传。

服务器上的目录约定：

```
/opt/pethealth/
├── pethealth-web.jar
├── health-record-service.jar
├── pethealth.env                  # 敏感环境变量（chmod 600）
├── config/
│   ├── application-ecs.yml        # web 外部配置
│   └── application-ecs-hrs.yml    # health-record-service 外部配置
└── logs/
```

---

## 5. 阶段五：编写外部配置（服务器上执行）

### 5.1 敏感环境变量文件

```bash
cat > /opt/pethealth/pethealth.env << 'EOF'
LLM_API_KEY=
LLM_MODEL=deepseek-v4-flash
EOF
chmod 600 /opt/pethealth/pethealth.env
```

### 5.2 pethealth-web 外部配置

```bash
cat > /opt/pethealth/config/application-ecs.yml << 'YAML'
server:
  port: 8080

spring:
  data:
    mongodb:
      host: 127.0.0.1
      port: 27017
      database: pethealth_web
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
      lettuce:
        pool:
          max-active: 4
          max-idle: 2
          min-idle: 0
          max-wait: 3000ms

# 保持 true：同机部署了 health-record-service，AI 走 Dubbo；
# 其余接口 Dubbo 异常时仍自动降级本地 Repository
app:
  dubbo:
    enabled: true

dubbo:
  application:
    name: pethealth-web
  consumer:
    check: false        # Provider 晚启动也不阻塞 web 启动
    timeout: 5000
    retries: 0
  qos:
    enable: false

reminder:
  scheduler-interval-seconds: 120

logging:
  level:
    root: INFO
    com.pethealth: INFO

management:
  endpoints:
    web:
      exposure:
        include: health,info
YAML
```

### 5.3 health-record-service 外部配置

```bash
cat > /opt/pethealth/config/application-ecs-hrs.yml << 'YAML'
server:
  port: 8086

spring:
  data:
    mongodb:
      host: 127.0.0.1
      port: 27017
      database: pethealth_web       # 与 web 共享同一个库
    redis:
      host: 127.0.0.1
      port: 6379
      database: 2                   # 注意：统计缓存用 db2

dubbo:
  application:
    name: health-record-service
  protocol:
    name: dubbo
    port: 20885
  registry:
    address: N/A
  provider:
    timeout: 65000                  # 必须大于 HTTP 调 DeepSeek 的 readTimeout(60s)
    retries: 0
  qos:
    enable: false

llm:
  api-url: ${LLM_API_URL:https://api.deepseek.com/v1/chat/completions}
  api-key: ${LLM_API_KEY:}
  model: ${LLM_MODEL:deepseek-v4-flash}
  max-tokens: 4096
  temperature: 0.7

logging:
  level:
    root: INFO
    com.pethealth: INFO

management:
  endpoints:
    web:
      exposure:
        include: health,info
YAML
```

> 关于 Spring Profile：systemd 中设置 `SPRING_PROFILES_ACTIVE=dev,ecs`。
> - `dev`：让幂等的 `DataInitializer` 创建演示数据（demo/123456、admin/admin123，含宠物/帖子/记录/提醒）；
>   库中已有用户时它会自动跳过，重复启动不会造脏数据。
> - `ecs`：用于加载上面的外部配置文件。
>
> 不想要演示数据时，把 profile 改为 `ecs` 即可（此时需通过页面"注册"功能自己建账号）。

---

## 6. 阶段六：Systemd 服务（开机自启 + 崩溃重启）

### 6.1 health-record-service（先启动，web 依赖它的 Dubbo）

```bash
cat > /etc/systemd/system/health-record.service << 'UNIT'
[Unit]
Description=PetHealth Health Record Service (Dubbo Provider + AI)
Requires=mongod.service redis-server.service
After=network.target mongod.service redis-server.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/pethealth
EnvironmentFile=/opt/pethealth/pethealth.env
Environment="SPRING_PROFILES_ACTIVE=ecs"
Environment="JAVA_OPTS=-Xms128m -Xmx256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ExecStart=/usr/bin/java $JAVA_OPTS -Duser.timezone=Asia/Shanghai \
  -jar /opt/pethealth/health-record-service.jar \
  --spring.config.additional-location=file:/opt/pethealth/config/application-ecs-hrs.yml
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/pethealth/logs/health-record.log
StandardError=append:/opt/pethealth/logs/health-record.log

[Install]
WantedBy=multi-user.target
UNIT
```

### 6.2 pethealth-web

```bash
cat > /etc/systemd/system/pethealth.service << 'UNIT'
[Unit]
Description=PetHealth Web Application
Requires=mongod.service redis-server.service health-record.service
After=network.target mongod.service redis-server.service health-record.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/pethealth
EnvironmentFile=/opt/pethealth/pethealth.env
# dev：初始化演示账号（幂等）；ecs：加载外部配置
Environment="SPRING_PROFILES_ACTIVE=dev,ecs"
Environment="JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ExecStart=/usr/bin/java $JAVA_OPTS -Duser.timezone=Asia/Shanghai \
  -jar /opt/pethealth/pethealth-web.jar \
  --spring.config.additional-location=file:/opt/pethealth/config/application-ecs.yml
Restart=on-failure
RestartSec=10
StandardOutput=append:/opt/pethealth/logs/pethealth.log
StandardError=append:/opt/pethealth/logs/pethealth.log

[Install]
WantedBy=multi-user.target
UNIT
```

### 6.3 启动（有先后顺序）

```bash
systemctl daemon-reload
systemctl enable health-record pethealth

systemctl start health-record
# 等到出现 "Dubbo Application ready" 或 8086/20885 监听（约 20~40 秒）
sleep 30
ss -ltnp | grep -E '8086|20885'

systemctl start pethealth
sleep 30
ss -ltnp | grep 8080

systemctl status health-record --no-pager
systemctl status pethealth --no-pager
```

日志实时跟踪：

```bash
tail -f /opt/pethealth/logs/health-record.log
tail -f /opt/pethealth/logs/pethealth.log
# 或
journalctl -u pethealth -f
```

---

## 7. 阶段七：Nginx 反向代理（80 → 8080）

```bash
cat > /etc/nginx/sites-available/pethealth << 'NGINX'
server {
    listen 80;
    server_name 121.43.118.128;

    client_max_body_size 10m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 10s;
        # ⚠️ 必须 ≥ 75s：AI 诊断是非流式调用，DeepSeek 完整生成最长约 70 秒，
        # 这里若用默认 60s，AI 结果会在 Nginx 层被掐断成 504。
        proxy_read_timeout    75s;
        proxy_send_timeout    75s;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/pethealth /etc/nginx/sites-enabled/pethealth
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
```

---

## 8. 阶段八：上线验证（逐项打勾）

### 8.1 服务器本机验证

```bash
# 1) 进程与端口
systemctl is-active mongod redis-server nginx health-record pethealth
ss -ltnp | grep -E '8080|8086|20885'

# 2) 健康检查
curl -s http://127.0.0.1:8080/actuator/health        # 期望 {"status":"UP"}
curl -s http://127.0.0.1:8086/actuator/health        # 期望 {"status":"UP"}

# 3) 经 Nginx
curl -s -o /dev/null -w "HTTP %{http_code}\n" http://127.0.0.1/          # 期望 200

# 4) 资源水位（关注 available 与 Swap 使用）
free -h
df -h /
```

### 8.2 公网功能验证（本地浏览器）

打开 **http://121.43.118.128** ，逐项验证：

| # | 操作 | 期望结果 |
|---|------|----------|
| 1 | 打开首页 | 页面正常、静态资源无 404 |
| 2 | 用 `demo / 123456` 登录 | 登录成功，写入 Cookie `PETHEALTH_TOKEN` |
| 3 | 宠物档案：新增/编辑/删除 | 正常（Dubbo 不可用部分会自动降级，功能不受影响） |
| 4 | 健康记录：新增一条、查看周/月趋势 | 正常 |
| 5 | 提醒列表 | 能看到初始化的疫苗/驱虫提醒 |
| 6 | 社区：发帖、回复、点赞 | 正常，热门榜可加载（Redis 不可用时也会降级 DB） |
| 7 | **AI 健康助手** | 见 8.3 |
| 8 | 退出登录 | 成功清除登录态 |

### 8.3 AI 诊断专项验证（重点）

1. 进入"AI 健康助手"页面，点击"**设置 API Key**"，输入你自己的 DeepSeek Key 并保存
   （Key 只存在当前浏览器 localStorage 中，服务器不保存）；
2. 选择宠物，症状输入"精神萎靡、食欲不振、偶尔呕吐"，点击"开始 AI 诊断"；
3. **耐心等待 5~30 秒，期间不要重复点击**（按钮已置灰，重复点击会重复扣费）；
4. 期望：返回标题为"AI 分析结果："的纯文本建议，无 `# * ** -` 等 Markdown 符号；
5. 清除 Key 后再诊断一次：应返回内置规则引擎结果（带概率的条目），不报错。

排查：若 AI 返回 503/504，直接看 [§10 FAQ](#10-故障排查)。

---

## 9. 日常运维

### 9.1 常用命令速查

```bash
# 服务管理
systemctl restart pethealth              # 重启 Web
systemctl restart health-record          # 重启 AI/健康记录 Provider
systemctl stop pethealth                  # 停 Web（单体降级演示时可用）

# 看日志
tail -f /opt/pethealth/logs/pethealth.log
tail -f /opt/pethealth/logs/health-record.log
journalctl -u pethealth --since "10 min ago"

# MongoDB
mongosh pethealth_web

# Redis
redis-cli -n 0 keys '*'       # web 库
redis-cli -n 2 keys '*'       # 统计缓存库
```

### 9.2 发布新版本（更新部署）

```bash
# ① 服务器：先备份当前 jar（在 scp 覆盖之前执行）
cd /opt/pethealth
cp pethealth-web.jar pethealth-web.jar.bak
cp health-record-service.jar health-record-service.jar.bak
```

```powershell
# ② 本地：重新打包并上传（覆盖同名文件）
cd d:\code\pet-health
mvn clean package -pl pethealth-web,health-record-service -am -DskipTests
scp pethealth-web\target\pethealth-web.jar root@121.43.118.128:/opt/pethealth/
scp health-record-service\target\health-record-service.jar root@121.43.118.128:/opt/pethealth/
```

```bash
# ③ 服务器：重启（先 Provider 后 Web）
systemctl restart health-record && sleep 25 && systemctl restart pethealth
```

### 9.3 重置演示数据

```bash
systemctl stop pethealth health-record
mongosh --eval "db.getSiblingDB('pethealth_web').dropDatabase()"
redis-cli FLUSHALL
systemctl start health-record && sleep 25 && systemctl start pethealth
# dev profile 的 DataInitializer 会重新造一套 demo/admin 演示数据
```

---

## 10. 故障排查

| 现象 | 排查方向 |
|------|----------|
| 公网打不开、超时 | ① 阿里云安全组是否放行 80；② `ufw status`；③ `systemctl status nginx pethealth` |
| 本机 `curl :8080` 通但公网不通 | 99% 是安全组问题，与 Nginx 无关 |
| Web 启动失败，日志含连接超时/拒绝 | MongoDB 未启动：`systemctl status mongod`；或 `mongosh --eval "db.runCommand({ping:1})"` 验证 |
| Web 日志大量 `RedisConnectionFailureException` | Redis 未启动：`systemctl status redis-server`；或 `redis-cli ping` 验证 |
| AI 返回 **503** "AI 诊断服务暂不可用" | ① `systemctl status health-record`；② `ss -ltnp \| grep 20885`；③ 看 health-record.log；Dubbo 是 localhost 直连，不用开安全组 |
| AI 返回 **504** 或等 60 秒后网关错误 | Nginx `proxy_read_timeout` 被改回 60s，必须 ≥75s；改完 `nginx -t && systemctl reload nginx` |
| AI 返回规则引擎结果而非 LLM | 正常降级：检查 Key 是否正确、服务器能否出网 `curl -I https://api.deepseek.com`、余额是否充足 |
| 进程被系统杀掉（服务反复 Restart） | `dmesg -T \| grep -i oom` 确认 OOM Kill；处理：`swapon --show` 确认 swap、把 web 的 `-Xmx` 降到 384m、hrs 降到 192m |
| 内存长期吃紧 | 停掉非必需的 health-record 保核心：`systemctl disable --now health-record`（AI 变 503，宠物/记录/社区/提醒不受影响） |
| 登录后马上掉线 | 确认是通过 Nginx 80 端口访问（非 8080），Cookie 域/端口一致；检查服务器时间是否准确 `timedatectl` |
| 22 端口把自己锁在外面 | 阿里云控制台 → VPC → 安全组临时放行你的新 IP |

---

## 11. 安全加固（上线后建议补做）

1. **SSH**：禁用密码登录、只用密钥；`PermitRootLogin` 视需要改为密钥-only（`/etc/ssh/sshd_config`）；
2. **HTTP 明文提示**：当前为 HTTP，登录 Cookie 与用户输入的 LLM Key 均明文传输。正式对外时绑定域名 + 证书：
   ```bash
   apt install -y certbot python3-certbot-nginx
   certbot --nginx -d your-domain.com      # 自动签发 Let's Encrypt 并开 443
   ```
3. **MongoDB/Redis 无密码说明**：当前未设密码，安全性依赖 `bind 127.0.0.1` + 安全组不放行 27017/6379。若后续需要更高安全级别，可补设密码（MongoDB `security.authorization: enabled` + 创建账号；Redis `requirepass`），并在 `application-ecs.yml` / `application-ecs-hrs.yml` 中补回 `username`/`password` 字段；
4. 定期 `apt upgrade` 打补丁；MongoDB/Redis 始终保持 `bind 127.0.0.1`，不加入安全组放行；
5. Actuator 仅暴露 `health,info`，不要暴露 `env`、`heapdump`。

---

## 12. 部署检查清单（交付前最后一遍）

- [ ] 安全组仅放行 22（限 IP）、80
- [ ] MongoDB：bind 127.0.0.1（无密码，仅本机可访问）
- [ ] Redis：bind 127.0.0.1，maxmemory 200mb（无密码，仅本机可访问）
- [ ] swap 2GB 已启用（`free -h` 可见）
- [ ] health-record、pethealth 均 `active` 且 `enabled`（开机自启）
- [ ] `curl http://127.0.0.1:8080/actuator/health` 返回 UP
- [ ] Nginx `proxy_read_timeout 75s`，`nginx -t` 通过
- [ ] http://121.43.118.128 公网可打开，demo/123456 可登录
- [ ] AI 诊断携带 Key 能返回 LLM 纯文本结果（约 5~30 秒）
- [ ] 无 Key 时 AI 返回规则引擎结果（不报错）
- [ ] 服务器上不存放任何真实 LLM Key（或明确知晓 `pethealth.env` 中有兜底 Key）
- [ ] `pethealth.env` 权限为 600，仓库中不包含任何真实密码/Key
