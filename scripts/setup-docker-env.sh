#!/usr/bin/env bash
# =============================================================
# 天柱·天镜 本地开发环境一键安装脚本
# 功能：安装 Docker Engine + Docker Compose，启动所有中间件服务
# 使用：sudo bash scripts/setup-docker-env.sh
# =============================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_DIR="${REPO_ROOT}/deploy/docker"

log()  { echo "[$(date '+%H:%M:%S')] $*"; }
info() { log "INFO  $*"; }
ok()   { log "OK    $*"; }
err()  { log "ERROR $*" >&2; exit 1; }

# ──────────────────────────────────────────────────────────────
# 1. 安装 Docker Engine（官方脚本，适用 Ubuntu 22.04）
# ──────────────────────────────────────────────────────────────
install_docker() {
    if command -v docker &>/dev/null; then
        ok "Docker 已安装：$(docker --version)"
        return
    fi

    info "安装 Docker Engine（官方 apt 源）..."

    # 依赖
    apt-get update -qq
    apt-get install -y -qq ca-certificates curl gnupg lsb-release

    # GPG key
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
        | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    # Apt 源
    echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
      https://download.docker.com/linux/ubuntu \
      $(lsb_release -cs) stable" \
      > /etc/apt/sources.list.d/docker.list

    apt-get update -qq
    apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    ok "Docker Engine 安装完成：$(docker --version)"
}

# ──────────────────────────────────────────────────────────────
# 2. 将当前用户加入 docker 组
# ──────────────────────────────────────────────────────────────
setup_docker_group() {
    local user="${SUDO_USER:-${USER}}"
    if id -nG "$user" | grep -qw docker; then
        ok "用户 ${user} 已在 docker 组"
    else
        info "将用户 ${user} 加入 docker 组..."
        usermod -aG docker "$user"
        ok "已加入 docker 组（下次登录后生效，本脚本继续用 sudo 运行 docker）"
    fi
}

# ──────────────────────────────────────────────────────────────
# 3. 确保 .env 存在
# ──────────────────────────────────────────────────────────────
ensure_env() {
    if [[ ! -f "${COMPOSE_DIR}/.env" ]]; then
        info "复制 .env.example → .env"
        cp "${COMPOSE_DIR}/.env.example" "${COMPOSE_DIR}/.env"
        ok ".env 已创建（使用默认开发密码）"
    else
        ok ".env 已存在，跳过"
    fi
}

# ──────────────────────────────────────────────────────────────
# 4. 启动服务
# ──────────────────────────────────────────────────────────────
start_services() {
    info "拉取镜像并启动中间件服务..."
    cd "${COMPOSE_DIR}"
    docker compose pull --quiet 2>/dev/null || true
    docker compose up -d

    info "等待服务健康检查（最长 3 分钟）..."
    local elapsed=0
    local interval=10
    while true; do
        # 统计非 healthy 的核心服务数量
        local unhealthy
        unhealthy=$(docker compose ps --format json 2>/dev/null \
            | python3 -c "
import sys, json
rows = []
for line in sys.stdin:
    line = line.strip()
    if not line: continue
    try:
        rows.append(json.loads(line))
    except Exception:
        pass
# 只检查 restart:unless-stopped 的 6 个核心服务
core = {'tianjing-postgresql','tianjing-redis','tianjing-kafka','tianjing-nacos','tianjing-minio','tianjing-tdengine'}
not_healthy = [r['Name'] for r in rows if r.get('Name') in core and r.get('Health','') != 'healthy']
print(len(not_healthy))
for n in not_healthy: print(' -', n, file=__import__('sys').stderr)
" 2>/tmp/unhealthy_list || echo "99")

        if [[ "$unhealthy" == "0" ]]; then
            ok "所有核心服务健康 ✓"
            break
        fi

        elapsed=$((elapsed + interval))
        if [[ $elapsed -ge 180 ]]; then
            echo ""
            err "超时！以下服务未就绪：$(cat /tmp/unhealthy_list 2>/dev/null)"
        fi

        echo -n "  等待中（${elapsed}s）...未就绪：$(cat /tmp/unhealthy_list 2>/dev/null | tr '\n' ' ')"
        echo ""
        sleep $interval
    done
}

# ──────────────────────────────────────────────────────────────
# 5. 打印服务地址汇总
# ──────────────────────────────────────────────────────────────
print_summary() {
    cat <<EOF

============================================================
  天柱·天镜 本地开发环境 — 服务地址汇总
============================================================
  PostgreSQL    localhost:5432   (用户: postgres)
  Redis         localhost:6379
  Kafka         localhost:9094   (宿主机直连)
  Nacos 控制台  http://localhost:8848/nacos  (账号: nacos/nacos)
  MinIO 控制台  http://localhost:9001        (账号见 .env)
  MinIO S3 API  http://localhost:9000
  TDengine REST http://localhost:6041        (账号: root/taosdata)
  TDengine 原生 localhost:6030
============================================================
  查看状态：cd deploy/docker && docker compose ps
  查看日志：cd deploy/docker && docker compose logs <service> -f
============================================================
EOF
}

# ──────────────────────────────────────────────────────────────
# main
# ──────────────────────────────────────────────────────────────
if [[ $EUID -ne 0 ]]; then
    err "请以 sudo 运行：sudo bash scripts/setup-docker-env.sh"
fi

install_docker
setup_docker_group
ensure_env
start_services
print_summary
