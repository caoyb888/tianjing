# 天柱·天镜 · Harbor 镜像仓库安装部署指南

> **版本**：V1.0 · **日期**：2026-04-17
> **适用服务器**：ai-platform（172.17.1.95）
> **Harbor 版本**：v2.10.3（符合 CLAUDE.md §2.5 要求）

---

## 目录

1. [环境概述](#1-环境概述)
2. [安装前准备](#2-安装前准备)
3. [下载离线安装包](#3-下载离线安装包)
4. [配置 harbor.yml](#4-配置-harboryml)
5. [执行安装](#5-执行安装)
6. [验证安装](#6-验证安装)
7. [配置 Docker 客户端（所有使用机器）](#7-配置-docker-客户端所有使用机器)
8. [推送现有镜像到 Harbor](#8-推送现有镜像到-harbor)
9. [日常运维](#9-日常运维)
10. [端口分配说明](#10-端口分配说明)

---

## 1. 环境概述

| 项目 | 值 |
|------|-----|
| 服务器主机名 | `ai-platform` |
| 服务器 IP | `172.17.1.95` |
| 操作系统 | Ubuntu 22.04 LTS |
| Docker 版本 | 29.4.0 |
| Docker Compose 版本 | v5.1.2 |
| 可用内存 | ~120 GB |
| 可用磁盘 | ~3.0 TB（`/dev/sdb2`） |
| Harbor 访问地址 | `http://172.17.1.95`（80 端口，内网 HTTP） |
| 镜像数据存储路径 | `/opt/harbor-data` |

**端口占用确认**：80 和 443 端口当前空闲，不与现有服务冲突。

---

## 2. 安装前准备

```bash
# 创建 Harbor 安装目录和数据目录
sudo mkdir -p /opt/harbor
sudo mkdir -p /opt/harbor-data
sudo chown -R $USER:$USER /opt/harbor /opt/harbor-data

# 确认 Docker 和 Compose 正常
docker --version          # 期望：Docker version 29.x
docker compose version    # 期望：Docker Compose version v5.x
```

---

## 3. 下载离线安装包

### 方法 A：服务器直接下载（服务器有外网）

```bash
wget -c \
  "https://github.com/goharbor/harbor/releases/download/v2.10.3/harbor-offline-installer-v2.10.3.tgz" \
  -O /tmp/harbor-offline-installer-v2.10.3.tgz

# 校验文件完整性（可选，checksum 见 GitHub Release 页面）
# sha256sum /tmp/harbor-offline-installer-v2.10.3.tgz
```

### 方法 B：本地下载后上传（服务器无外网）

```bash
# 本地机器下载（约 760 MB）
# https://github.com/goharbor/harbor/releases/tag/v2.10.3
# 文件：harbor-offline-installer-v2.10.3.tgz

# 上传到服务器
scp harbor-offline-installer-v2.10.3.tgz tzai@172.17.1.95:/tmp/
```

### 解压

```bash
tar -xzf /tmp/harbor-offline-installer-v2.10.3.tgz -C /opt/
# 解压后目录：/opt/harbor/
ls /opt/harbor/
# 期望看到：harbor.yml.tmpl  install.sh  prepare  common/  ...
```

---

## 4. 配置 harbor.yml

```bash
cd /opt/harbor
cp harbor.yml.tmpl harbor.yml
```

编辑 `/opt/harbor/harbor.yml`，修改以下关键项：

```yaml
# ── 必改项 ────────────────────────────────────────────────────

# 1. 主机名/IP（用于生成访问地址和 token 签发）
hostname: 172.17.1.95

# 2. HTTP 端口（80 空闲，可直接使用）
http:
  port: 80

# 3. 注释掉 HTTPS 配置（内网 HTTP 使用，后续如需 HTTPS 再开启）
# https:
#   port: 443
#   certificate: /your/certificate/path
#   private_key: /your/private/key/path

# 4. 管理员密码（请修改为强密码）
harbor_admin_password: Harbor@Tianjing2024

# 5. 数据库密码
database:
  password: Harbor@Tianjing2024
  max_idle_conns: 100
  max_open_conns: 900

# 6. 镜像数据存储路径（3TB 磁盘，充裕）
data_volume: /opt/harbor-data

# ── 可选项 ────────────────────────────────────────────────────

# 日志级别
log:
  level: warning
  local:
    rotate_count: 50
    rotate_size: 200m
    location: /var/log/harbor
```

> **注意**：`harbor.yml` 中如果 `https` 段存在但未注释，安装脚本会强制要求证书文件，导致安装失败。务必将 `https:` 整段注释掉。

---

## 5. 执行安装

```bash
cd /opt/harbor

# 运行预检和配置生成（不启动服务）
sudo ./prepare

# 执行安装并启动
sudo ./install.sh

# 安装过程约 2-5 分钟，结尾看到以下输出表示成功：
# ✔ ----Harbor has been installed and started successfully.----
```

安装完成后，Harbor 会以 Docker Compose 方式运行（配置文件在 `/opt/harbor/docker-compose.yml`）。

---

## 6. 验证安装

```bash
# 检查容器状态（全部 healthy）
cd /opt/harbor
docker compose ps

# 期望看到以下容器均为 healthy/running：
# harbor-core       harbor-db         harbor-jobservice
# harbor-log        harbor-portal     harbor-redis
# harbor-registryctl nginx            registry

# 浏览器访问（内网机器）
# http://172.17.1.95
# 账号：admin
# 密码：Harbor@Tianjing2024
```

### 创建项目

登录 Harbor Web UI 后，创建以下项目（对应 CLAUDE.md §2.3）：

| 项目名 | 访问级别 | 用途 |
|--------|---------|------|
| `tianjing` | 私有 | 推理服务、算法镜像 |
| `tianjing-training` | 私有 | 训练环境镜像 |

---

## 7. 配置 Docker 客户端（所有使用机器）

由于使用 HTTP（非 HTTPS），Docker 默认拒绝连接，需配置 `insecure-registries`：

```bash
# 在需要 push/pull 的每台机器上执行
sudo tee /etc/docker/daemon.json > /dev/null << 'EOF'
{
  "insecure-registries": ["172.17.1.95"],
  "registry-mirrors": []
}
EOF

sudo systemctl reload docker

# 验证配置生效
docker info | grep -A3 "Insecure Registries"
```

> **本机（ai-platform）也需要执行**，本机 push 同样走 HTTP。

### 登录

```bash
docker login 172.17.1.95 -u admin -p Harbor@Tianjing2024
# 期望输出：Login Succeeded
```

---

## 8. 推送现有镜像到 Harbor

安装完成后，将服务器上已有的镜像推送到 Harbor 统一管理：

```bash
# ── GPU 推理服务镜像 ──────────────────────────────────────────
docker tag tianjing/gpu-infer:trt-1.0.0 \
           172.17.1.95/tianjing/gpu-infer:trt-1.0.0
docker push 172.17.1.95/tianjing/gpu-infer:trt-1.0.0

docker tag tianjing/gpu-infer-service:1.0.0 \
           172.17.1.95/tianjing/gpu-infer-service:1.0.0
docker push 172.17.1.95/tianjing/gpu-infer-service:1.0.0

# ── 后续新镜像命名规范（推送时直接用 Harbor 地址作为前缀）──
# docker build -t 172.17.1.95/tianjing/<image-name>:<tag> .
# docker push 172.17.1.95/tianjing/<image-name>:<tag>
```

### 其他机器拉取镜像

```bash
# 目标机器已配置 insecure-registries 并登录后
docker pull 172.17.1.95/tianjing/gpu-infer:trt-1.0.0

# 使用 TRT 推理服务
docker run -d \
  --name gpu-infer-trt \
  --gpus all \
  -p 8102:8102 \
  -v /models:/models \
  -e INFER_BACKEND=tensorrt \
  -e TRT_ENGINE_PATH=/models/yolov8n_fp16.trt \
  172.17.1.95/tianjing/gpu-infer:trt-1.0.0 \
  bash -c "cd /app/src && python3 main.py"
```

> **TRT 引擎注意**：`yolov8n_fp16.trt` 编译于 V100（SM_70）。迁移到不同架构 GPU 时，需在目标机器重新运行 `trtexec`，ONNX 模型（`yolov8n.onnx`）可从 MinIO 直接下载，无架构限制。

---

## 9. 日常运维

### 启动 / 停止 / 重启

```bash
cd /opt/harbor

# 停止
docker compose down

# 启动
docker compose up -d

# 重启单个组件（如 core）
docker compose restart harbor-core
```

### 配置开机自启

Harbor 的 Docker Compose 容器已带 `restart: always`，只要 Docker 服务自启，Harbor 会自动拉起。确认 Docker 自启：

```bash
sudo systemctl enable docker
```

### 查看日志

```bash
# 查看所有组件日志
cd /opt/harbor && docker compose logs -f --tail=100

# 查看特定组件
docker compose logs -f harbor-core
docker compose logs -f nginx
```

### 磁盘清理（定期）

```bash
# 在 Harbor Web UI → 系统管理 → 垃圾清理 → 立即执行
# 或命令行触发 GC
curl -u admin:Harbor@Tianjing2024 \
  -X POST "http://172.17.1.95/api/v2.0/system/gc/schedule" \
  -H "Content-Type: application/json" \
  -d '{"schedule":{"type":"Manual"}}'
```

### 备份

```bash
# 数据备份（停服备份最安全）
cd /opt/harbor && docker compose down
sudo tar -czf /backup/harbor-data-$(date +%Y%m%d).tar.gz /opt/harbor-data/
docker compose up -d
```

---

## 10. 端口分配说明

Harbor 使用端口 **80**（HTTP）。根据 `docs/天柱天镜_端口分配表_V1.0.md` 原规划为 443（HTTPS），本次内网部署改用 80，待后续配置 TLS 证书后可切换回 443。

**安装完成后请在端口分配表中更新 Harbor 条目：**

| 服务 | 端口 | 协议 | 备注 |
|------|------|------|------|
| Harbor 镜像仓库 | **80** | HTTP | `http://172.17.1.95`，内网 HTTP（暂未启用 HTTPS） |
| Harbor Portal | 80 | HTTP | Web UI，Admin 账号管理 |

---

## 附录：常见问题

**Q：安装时报 `https certificate` 相关错误**
A：确认 `harbor.yml` 中 `https:` 整段已注释掉（包括 `port:`、`certificate:`、`private_key:` 三行）。

**Q：`docker login` 报 `http: server gave HTTP response to HTTPS client`**
A：目标机器未配置 `insecure-registries`，参考第 7 节添加配置后执行 `sudo systemctl reload docker`。

**Q：Harbor 容器启动后 `harbor-core` 一直 unhealthy**
A：通常是数据库初始化未完成，等待 1-2 分钟后再检查；或查看 `docker compose logs harbor-db` 确认数据库状态。

**Q：push 时报 `unauthorized: authentication required`**
A：需先 `docker login 172.17.1.95`，并确认 Harbor 中已创建对应项目（`tianjing`）且当前账号有写权限。

---

*本文档由天柱·天镜项目组整理 · 2026-04-17*
*Harbor 官方文档：https://goharbor.io/docs/2.10.0/*
