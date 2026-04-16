# 本地开发环境搭建手册

> **适用场景**：单机 Docker Compose 本地开发 / 功能演示
> **不适用**：生产环境（生产环境使用 Kubernetes，见 `deploy/helm/`）
> **更新日期**：2026-04-03

---

## 1. 环境要求

### 1.1 必要软件

| 软件 | 最低版本 | 说明 |
|------|---------|------|
| Docker Engine | 24.x+ | 含 Docker Compose v2 插件 |
| Git | 2.x+ | |
| Java | 21 LTS | 后端服务本地运行时需要 |
| Node.js | 20.x+ | 前端本地开发需要 |
| Python | 3.11+ | 推理服务本地运行时需要 |

### 1.2 CPU 指令集要求

> **当前开发机（Intel Core Processor Haswell，KVM 虚拟化，16 vCPU）**
>
> 本机 CPU 支持 SSE / SSE2 / SSE3 / SSSE3 / SSE4.1 / SSE4.2 / **AVX / AVX2 / AES-NI** / POPCNT / BMI1 / BMI2 / FMA，**不支持 AVX-512**。
> 所有官方标准镜像均可正常运行，`deploy/docker/docker-compose.yml` 使用标准版本。

| 服务 | 使用版本 | 说明 |
|------|---------|------|
| MinIO | `RELEASE.2024-11-07T00-52-20Z` | 标准版，AVX2 已满足 ✅ |
| minio/mc | `latest` | 标准版，POPCNT+SSE4.1 已满足 ✅ |
| Elasticsearch | `8.13.4`（可选，见 compose 注释块） | AVX 已满足，按需启用 ✅ |
| Kafka | `confluentinc/cp-kafka:7.6.0` | bitnami 国内不可用，使用 Confluent（非 CPU 问题） |
| Nacos | `nacos/nacos-server:v2.4.3` | v2.3.2 国内镜像不可用，使用 v2.4.3（API 兼容，非 CPU 问题） |

---

## 2. 首次启动步骤

### 2.1 克隆仓库

```bash
git clone https://github.com/caoyb888/tianjing.git
cd tianjing
```

### 2.2 准备环境变量

```bash
cd deploy/docker
cp .env.example .env
# 按需修改 .env 中的密码（开发环境使用默认值即可）
```

`.env` 包含以下配置项：

| 变量 | 默认值 | 说明 |
|------|-------|------|
| `POSTGRES_PASSWORD` | `tianjing_dev_pg_2024` | PostgreSQL 超级用户密码 |
| `TIANJING_PROD_DB_PASSWORD` | `prod_user_dev_2024` | 生产库用户密码 |
| `TIANJING_SANDBOX_DB_PASSWORD` | `sandbox_user_dev_2024` | Sandbox 库用户密码 |
| `TIANJING_TRAIN_DB_PASSWORD` | `train_user_dev_2024` | 训练库用户密码 |
| `NACOS_DB_PASSWORD` | `nacos_dev_2024` | Nacos 配置库用户密码 |
| `REDIS_PASSWORD` | `tianjing_dev_redis_2024` | Redis 密码 |
| `MINIO_ROOT_USER` | `minioadmin` | MinIO 管理员账号 |
| `MINIO_ROOT_PASSWORD` | `minioadmin123` | MinIO 管理员密码 |
| `KAFKA_KRAFT_CLUSTER_ID` | `MkU3OEVBNTcwNTJENDM2Qg` | Kafka KRaft 集群 ID（本地固定值） |

### 2.3 启动中间件

```bash
# 在 deploy/docker/ 目录下执行
docker compose up -d
```

首次启动会自动执行以下初始化任务（通过 init 容器完成，`restart: "no"`）：

| 任务容器 | 内容 |
|---------|------|
| `kafka-init` | 创建 10 个 Kafka Topic |
| `minio-init` | 创建 8 个 MinIO Bucket |
| `tdengine-init` | 创建 TDengine 时序库及超级表 |
| `postgresql`（启动时） | 创建 4 个数据库、用户，导入 Flyway Schema |

等待所有服务就绪（约 60–90 秒）：

```bash
docker compose ps
# 确认 6 个服务均显示 (healthy)
```

---

## 3. 日常启动 / 停止命令

```bash
cd /home/xintong/tianjing/deploy/docker

# 启动所有服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看某个服务日志（以 kafka 为例）
docker compose logs kafka -f --tail=100

# 停止所有服务（保留数据卷）
docker compose down

# 停止并清除所有数据（完全重置）
docker compose down -v
```

---

## 4. 服务访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| PostgreSQL | `localhost:5432` | 用户：`postgres`，密码见 `.env` |
| Redis | `localhost:6379` | 密码见 `.env` |
| Kafka | `localhost:9094` | 宿主机直连（服务间通信用 `kafka:9092`） |
| Nacos 控制台 | `http://localhost:8848/nacos` | 账号：`nacos` / `nacos` |
| MinIO 控制台 | `http://localhost:9001` | 账号密码见 `.env` |
| MinIO S3 API | `http://localhost:9000` | |
| TDengine REST | `http://localhost:6041` | 账号：`root` / `taosdata` |
| TDengine 原生 | `localhost:6030` | JDBC-JNI 驱动使用 |

---

## 5. 常见问题

### TDengine 初始化失败

TDengine native TCP（端口 6030）在 Docker 跨容器场景不稳定，初始化改用 REST API（端口 6041）。
若 `tdengine-init` 容器报错，手动重跑：

```bash
docker compose rm -sf tdengine-init
docker compose up -d tdengine-init
docker compose logs tdengine-init -f
```

### 端口冲突

本机已有以下容器占用端口，启动前确认它们已停止：

```bash
# 检查冲突容器
docker ps --format "table {{.Names}}\t{{.Ports}}"

# 停止冲突容器（按实际容器名调整）
docker stop asset-nacos asset-redis asset-mysql-tmp
```

### Elasticsearch 告警日志全文检索（可选）

当前 CPU 已支持 AVX 指令，Elasticsearch 8.x 可正常运行。如需启用告警日志全文检索，
取消 `deploy/docker/docker-compose.yml` 末尾 Elasticsearch 注释块，并在 `volumes` 块中添加 `es-data:` 卷。

---

## 6. 网络与数据卷

```
Docker 网络：tianjing-dev_tianjing-net（172.30.0.0/24）

数据卷：
  tianjing-dev_pg-data       → PostgreSQL 数据
  tianjing-dev_redis-data    → Redis 数据
  tianjing-dev_kafka-data    → Kafka 日志
  tianjing-dev_minio-data    → MinIO 对象存储
  tianjing-dev_tdengine-data → TDengine 时序数据
  tianjing-dev_nacos-data    → Nacos 配置数据
```
