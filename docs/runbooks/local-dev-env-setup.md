# 本地开发环境搭建手册

> **适用场景**：单机 Docker Compose 本地开发 / 功能演示
> **不适用**：生产环境（生产环境使用 Kubernetes，见 `deploy/helm/`）
> **更新日期**：2026-04-01

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

> ⚠ **当前开发机特别说明（Intel Core 2 Duo T7700）**
>
> 本机 CPU 仅支持 SSE / SSE2 / SSE3 / SSSE3 / SSE4.2，**不支持 AVX / AVX2 / AVX512 / AES-NI**。
> 这导致部分官方镜像无法运行，`deploy/docker/docker-compose.yml` 已对应调整。
> **待更换硬件后需将下表中的镜像版本回滚至标准版本。**

| 服务 | 标准版本（生产 / 新机器） | 当前开发机使用版本 | 原因 |
|------|------------------------|-----------------|------|
| MinIO | `RELEASE.2024+` | `RELEASE.2023-03-20T20-16-18Z` | 2024+ 版本 Go 编译启用 AVX2 |
| minio/mc | `latest` | 替换为 `python:3.11-alpine` | `mc:latest` 依赖 glibc x86-64-v2（需 POPCNT+SSE4.1） |
| Elasticsearch | `8.x` | **已移除** | 8.x 强依赖 AVX 指令，启动即崩溃 |
| Kafka | `bitnami/kafka:3.6` | `confluentinc/cp-kafka:7.6.0` | bitnami 镜像国内不可用（非 CPU 问题） |
| Nacos | `nacos/nacos-server:v2.3.2` | `v2.4.3` | v2.3.2 国内镜像不可用（非 CPU 问题，API 兼容） |

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
cd /home/laigang/tianjing/deploy/docker

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

### 升级服务器后恢复标准镜像

更换支持 AVX2 的 CPU 后，修改 `deploy/docker/docker-compose.yml`：

```yaml
# MinIO 恢复官方最新版
minio:
  image: minio/minio:RELEASE.2024-xx-xxTxx-xx-xxZ

# MinIO 初始化恢复 mc
minio-init:
  image: minio/mc:latest
  # 恢复原 mc alias / mb 命令，移除 python 脚本

# Elasticsearch 可重新加入（如需告警日志全文检索）
```

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
