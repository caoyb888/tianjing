#!/usr/bin/env bash
# =============================================================================
# 天柱·天镜 后端服务一键启动脚本
# 用法：
#   ./start-backend.sh            # 启动所有未运行的服务
#   ./start-backend.sh --restart  # 先停止再全量重启
#   ./start-backend.sh --stop     # 停止所有服务
#   ./start-backend.sh --status   # 仅查看健康状态
#   ./start-backend.sh --build    # 先构建再启动（跳过测试）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SERVICES_DIR="$ROOT_DIR/services"
NGINX_CONF="$ROOT_DIR/deploy/docker/nginx/gateway.conf"
LOG_DIR="/tmp/tianjing-logs"

mkdir -p "$LOG_DIR"

# ─── 颜色 ─────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

# ─── 本地代理绕过（宿主机若配置了 http_proxy，排除 localhost 避免 curl/shell 工具走代理）
export no_proxy=localhost,127.0.0.1
export NO_PROXY=localhost,127.0.0.1

# ─── multipart 上传大小（覆盖 JAR 内 application.yml 默认 1MB 限制）─────────────
export SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=2GB
export SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=2GB

# ─── 本地开发环境变量（对应 deploy/docker/.env 中的实际值）──────────────────────
export TIANJING_REDIS_HOST=localhost
export TIANJING_REDIS_PORT=6379
export TIANJING_REDIS_PASSWORD=tianjing_dev_redis_2024

# Kafka 使用外部监听端口 9094（9092 为 Docker 内部，宿主机不可达）
export TIANJING_KAFKA_BOOTSTRAP_SERVERS=localhost:9094

# PostgreSQL — 生产库
export TIANJING_POSTGRES_PROD_URL=jdbc:postgresql://localhost:5432/tianjing_prod
export TIANJING_POSTGRES_PROD_USER=tianjing_prod_user
export TIANJING_POSTGRES_PROD_PASSWORD=prod_user_dev_2024

# PostgreSQL — Sandbox 库（compare-dashboard-service 使用）
export TIANJING_POSTGRES_SANDBOX_URL=jdbc:postgresql://localhost:5432/tianjing_sandbox
export TIANJING_POSTGRES_SANDBOX_USER=tianjing_sandbox_user
export TIANJING_POSTGRES_SANDBOX_PASSWORD=sandbox_user_dev_2024
export TIANJING_POSTGRES_TRAIN_URL=jdbc:postgresql://localhost:5432/tianjing_train
export TIANJING_POSTGRES_TRAIN_USER=tianjing_train_user
export TIANJING_POSTGRES_TRAIN_PASSWORD=train_user_dev_2024

# lowcode-workflow-service 使用不同的 PG 环境变量名
export TIANJING_POSTGRES_USERNAME=tianjing_prod_user
export TIANJING_POSTGRES_PASSWORD=prod_user_dev_2024

# TDengine（result-aggregate-service 使用）
export TIANJING_TDENGINE_URL=jdbc:TAOS-RS://localhost:6041/tianjing_ts?timezone=UTC
export TIANJING_TDENGINE_USER=root
export TIANJING_TDENGINE_PASSWORD=taosdata

# 仿真推理代理：指向 GPU 推理服务（LOCAL-GPU-YOLO-V1，端口 8102）
export TIANJING_INFERENCE_PROXY_URL=http://localhost:8102

# MinIO（history-replay-service 使用）
export TIANJING_MINIO_ENDPOINT=http://localhost:9000
export TIANJING_MINIO_PUBLIC_ENDPOINT=/minio-frames
export TIANJING_MINIO_ACCESS_KEY=minioadmin
export TIANJING_MINIO_SECRET_KEY=minioadmin123

# JWT RSA-2048 密钥对（本地开发用，由 openssl genrsa 生成）
export TIANJING_JWT_PRIVATE_KEY_PEM="-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCw/lofDIFigd6c
Fj0O60kOnsU5TS4Mx8/x6TSJmx+tO1ACrdb1QjSHYrLWclSIda+jzdv/yf/WJRKa
TS1MvfCfh5Gj4GZLDJ3BEn7oda4hRnhcD6yXtXxCoL0Pw/qxklhk19PLyJprDPq9
PJ0p5jVO3U0MmJAkvvSDq4Jg4eMWKvkL9PozmD8BdFEx+EaKiFyCYWZCz/gmQWHd
886OE8WWNK0ph2LMJB3CTM5K6188DvNHQd7kzyvF6gcYw5Sx6X2eTT9HVopUqWRA
o+lOLDXTU2W5IDHYA6ihcVSi+zZDbJin8IV7nHypSgieXeZrJij9O7jG2crftsOQ
xNKV+0MrAgMBAAECggEAA8BztJjIdu590FBmw7wx2w3huSptQXdMbYcd+ZTAm4sL
hSMEnXcYmfR6Cf9E0qevLQlRRbjIcLrUcoThFiSO+mXrRmgC6X0lxh2Lc+4BEbkZ
4zzja0vWvt4Dylb2WqRzHAaFkzPelUpVix4paEv2JISeyReRM2/Nl96FovwaAWXa
uSoriL2NRxIz8nNT1Wgkyy7b9rPGPDCxCukd8SabTwa0scN677NeJpeCDii0AzFZ
g0sGqA4hxJkmZXhG5ABh/WfWwWTn7k+Sd0en9dIrMS8JNMc3IfIQRjX1kPDhtjFQ
m7KacPAZQRcLdYEJgjrBW2UQwpCku5tLUM2GuoWTkQKBgQDsT+B4tjL2han8Qi83
hA/KeXYdb8gSbb8LYF5m7Ek8r7jJFGsmuJNb99Ni9PEET8RYqAfiqt6QTKHYX76q
q3pSiTPZAaNDUw1PcGxGf1KjywRlYKWFXGIzkA2dRvcv9v8SRUBDmzEUqCWnd1Pf
/5ird4+KuM0OqElAOAL8x8eLBwKBgQC/vVDk6ADif93TO0CQ/aE45PAoxFY6M1zi
6RlpkqckbO5XuRYPtKSJ/67jZkadbhFGGpgM6DIrXxe32/tWxaDZ9w515JxnXM8F
LUreBV9kJ+OIKAv5vWHNxBjK8r80ie9niPFRqWevbtfpyUKrhYYyA5FSBT2WgPyj
ZLDSCY6pvQKBgByA8gF9cJ49tyg/T3Sx0DnK9s1MK2TVqgKUSC6/94GDbJWMifQs
tVGVjgDUrLGpcXSEKBdWJuxe4fK4ccYiUunbtM5LHUdEoElhtR23w98P4OLdinmQ
kCPKD2pWIgC+GTw58Ct3b68fLROLGCnfqamdDUpdShang9ZkKzAUZORHAoGADSLh
Se2DMgFkpzYmBEj8JLO6qHBbH/2o3Xru3MbJYLt90NDDbCwKIdI8nyPArsR9tnnX
9PprhYEsVNJjc9xyWryMZxNUi9hV7prdyDxB/tCkzA1CVMfezYQj1rOu2eChYEyH
i/q5OdG0RTYetTWergqEiEQHpioMJP2fB6TT+1kCgYEAviIxDohUD+9rQaMuXeJx
OY4siH7GLrOGB+WScjO6oGoJBCOCbNhgFs+CwJcNEQWZ1S1Zu1gGAwkEEqSd8zbG
hQNxDAooOpBBY2Wu/rrc73YQnxQic3r72L8+T1P9tDp5H/cBTabUD2Auz0FlUpQl
lCYHfitV9758Zz3WyNRCmzA=
-----END PRIVATE KEY-----"

export TIANJING_JWT_PUBLIC_KEY_PEM="-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAsP5aHwyBYoHenBY9DutJ
Dp7FOU0uDMfP8ek0iZsfrTtQAq3W9UI0h2Ky1nJUiHWvo83b/8n/1iUSmk0tTL3w
n4eRo+BmSwydwRJ+6HWuIUZ4XA+sl7V8QqC9D8P6sZJYZNfTy8iaawz6vTydKeY1
Tt1NDJiQJL70g6uCYOHjFir5C/T6M5g/AXRRMfhGiohcgmFmQs/4JkFh3fPOjhPF
ljStKYdizCQdwkzOSutfPA7zR0He5M8rxeoHGMOUsel9nk0/R1aKVKlkQKPpTiw1
01NluSAx2AOooXFUovs2Q2yYp/CFe5x8qUoInl3mayYo/Tu4xtnK37bDkMTSlftD
KwIDAQAB
-----END PUBLIC KEY-----"

# ─── 服务列表：格式 "服务目录名:端口" ─────────────────────────────────────────
declare -a SERVICES=(
  "auth-service:8081"
  "scene-config-service:8082"
  "device-manage-service:8083"
  "calibration-service:8084"
  "alarm-judge-service:8085"
  "alarm-rule-service:8086"
  "compare-dashboard-service:8087"
  "drift-monitor-service:8089"
  "health-monitor-service:8090"
  "route-dispatch-service:8093"
  "lowcode-workflow-service:8094"
  "notification-service:8095"
  "traffic-mirror-service:8096"
  "stream-ingest-service:8097"
  "frame-extract-service:8098"
  "preprocess-service:8099"
  "result-aggregate-service:8100"
  "history-replay-service:8101"
)

# ─── 辅助函数 ─────────────────────────────────────────────────────────────────

is_port_listening() {
  ss -tlnp 2>/dev/null | grep -q ":$1 " && return 0 || return 1
}

get_pid_on_port() {
  # 非 root 用户下 ss -tlnp 不含 pid= 信息，改用 sudo lsof 获取 PID
  sudo lsof -ti :"$1" 2>/dev/null | head -1 || true
}

health_check() {
  local port=$1
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 --noproxy localhost "http://localhost:$port/actuator/health" 2>/dev/null)
  echo "$code"
}

stop_service() {
  local name=$1 port=$2
  local pid
  pid=$(get_pid_on_port "$port")
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null && echo -e "  ${YELLOW}停止${RESET} $name (PID=$pid)" || true
    # 最多等 5 秒
    for _ in $(seq 1 10); do
      is_port_listening "$port" || break
      sleep 0.5
    done
    # 仍未释放则强杀
    is_port_listening "$port" && kill -9 "$pid" 2>/dev/null || true
  fi
}

start_service() {
  local name=$1 port=$2
  local jar="$SERVICES_DIR/$name/target/$name-1.0.0-SNAPSHOT.jar"
  local log="$LOG_DIR/$name.log"

  # JAR 不存在时提示
  if [ ! -f "$jar" ]; then
    echo -e "  ${RED}跳过${RESET} $name — JAR 不存在，请先执行 --build"
    return 1
  fi

  # 已有进程在监听则跳过
  if is_port_listening "$port"; then
    echo -e "  ${CYAN}跳过${RESET} $name — 端口 $port 已被占用（已运行）"
    return 0
  fi

  nohup java -jar "$jar" > "$log" 2>&1 &
  local pid=$!
  echo -e "  ${GREEN}已启动${RESET} $name  (PID=$pid, port=$port, log=$log)"
}

build_all() {
  echo -e "\n${BOLD}▶ 构建所有服务（跳过测试）...${RESET}"
  cd "$SERVICES_DIR"
  mvn package -Dmaven.test.skip=true -q
  echo -e "${GREEN}构建完成${RESET}"
}

# ─── Nginx 网关 ───────────────────────────────────────────────────────────────

start_gateway() {
  if docker inspect tianjing-gateway &>/dev/null; then
    local state
    state=$(docker inspect -f '{{.State.Running}}' tianjing-gateway 2>/dev/null)
    if [ "$state" = "true" ]; then
      echo -e "  ${CYAN}跳过${RESET} tianjing-gateway — 已运行（port=8079）"
      return 0
    else
      docker rm tianjing-gateway &>/dev/null || true
    fi
  fi
  docker run -d \
    --name tianjing-gateway \
    --add-host=host.docker.internal:host-gateway \
    -p 8079:8079 \
    -v "$NGINX_CONF:/etc/nginx/conf.d/gateway.conf:ro" \
    nginx:1.25-alpine &>/dev/null
  echo -e "  ${GREEN}已启动${RESET} tianjing-gateway  (port=8079)"
}

stop_gateway() {
  if docker inspect tianjing-gateway &>/dev/null; then
    docker rm -f tianjing-gateway &>/dev/null && \
      echo -e "  ${YELLOW}停止${RESET} tianjing-gateway" || true
  fi
}

# ─── recording-replay-service（Python FastAPI，端口 8091）───────────────────

RECORDING_REPLAY_DIR="$ROOT_DIR/inference/recording-replay-service"
UV_BIN="/home/tzai/.local/bin/uv"

start_recording_replay_service() {
  if is_port_listening 8091; then
    echo -e "  ${CYAN}跳过${RESET} recording-replay-service — 端口 8091 已被占用（已运行）"
    return 0
  fi
  if [ ! -f "$UV_BIN" ]; then
    echo -e "  ${RED}跳过${RESET} recording-replay-service — uv 未找到 ($UV_BIN)"
    return 1
  fi
  local log="$LOG_DIR/recording-replay-service.log"
  (
    cd "$RECORDING_REPLAY_DIR"
    TIANJING_MINIO_ENDPOINT="http://localhost:9000" \
    MINIO_ACCESS_KEY="minioadmin" \
    MINIO_SECRET_KEY="minioadmin123" \
    TIANJING_KAFKA_BOOTSTRAP_SERVERS="localhost:9094" \
    env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY \
    nohup "$UV_BIN" run \
      --python 3.10 \
      --with "fastapi==0.110.0" \
      --with "uvicorn[standard]==0.29.0" \
      --with "pydantic==2.6.4" \
      --with "numpy==1.26.4" \
      --with "kafka-python==2.0.2" \
      --with "minio==7.2.5" \
      --with "opencv-python-headless==4.9.0.80" \
      --with "structlog==24.1.0" \
      --with "python-multipart==0.0.9" \
      python3 src/main.py > "$log" 2>&1 &
    echo -e "  ${GREEN}已启动${RESET} recording-replay-service  (PID=$!, port=8091, log=$log)"
  )
}

stop_recording_replay_service() {
  local pid
  pid=$(get_pid_on_port 8091)
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null && echo -e "  ${YELLOW}停止${RESET} recording-replay-service (PID=$pid)" || true
  fi
}

# ─── cloud-inference-proxy（Python FastAPI，端口 8092）────────────────────────

CLOUD_INFER_DIR="$ROOT_DIR/inference/cloud-inference-proxy"

start_cloud_inference_proxy() {
  if is_port_listening 8092; then
    echo -e "  ${CYAN}跳过${RESET} cloud-inference-proxy — 端口 8092 已被占用（已运行）"
    return 0
  fi
  if [ ! -f "$UV_BIN" ]; then
    echo -e "  ${RED}跳过${RESET} cloud-inference-proxy — uv 未找到 ($UV_BIN)"
    return 1
  fi
  local log="$LOG_DIR/cloud-inference-proxy.log"
  (
    cd "$CLOUD_INFER_DIR"
    # ONNX_MODEL_PATH：优先使用已下载的 PRODUCTION 模型；回退到默认占位模型
    local model_path="/tmp/tianjing-models/HEAD-SINTER-GRATE-V1/best.onnx"
    [ -f "$model_path" ] || model_path="/models/yolov8n.onnx"
    ONNX_MODEL_PATH="$model_path" \
    env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY \
    nohup "$UV_BIN" run \
      --python 3.10 \
      --with "fastapi==0.110.0" \
      --with "uvicorn[standard]==0.29.0" \
      --with "pydantic==2.6.4" \
      --with "numpy==1.26.4" \
      --with "opencv-python-headless==4.9.0.80" \
      --with "onnxruntime==1.19.2" \
      --with "structlog==24.1.0" \
      --with "httpx==0.27.0" \
      python3 src/main.py > "$log" 2>&1 &
    echo -e "  ${GREEN}已启动${RESET} cloud-inference-proxy  (PID=$!, port=8092, log=$log, model=$model_path)"
  )
}

stop_cloud_inference_proxy() {
  local pid
  pid=$(get_pid_on_port 8092)
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null && echo -e "  ${YELLOW}停止${RESET} cloud-inference-proxy (PID=$pid)" || true
  fi
}

# ─── infer-dispatcher（Python FastAPI + Kafka Consumer，端口 8103，GPU-05）────

INFER_DISPATCHER_DIR="$ROOT_DIR/inference/infer-dispatcher"

start_infer_dispatcher() {
  if is_port_listening 8103; then
    echo -e "  ${CYAN}跳过${RESET} infer-dispatcher — 端口 8103 已被占用（已运行）"
    return 0
  fi
  if [ ! -f "$UV_BIN" ]; then
    echo -e "  ${RED}跳过${RESET} infer-dispatcher — uv 未找到 ($UV_BIN)"
    return 1
  fi
  local log="$LOG_DIR/infer-dispatcher.log"
  (
    cd "$INFER_DISPATCHER_DIR"
    TIANJING_MINIO_ENDPOINT="http://localhost:9000" \
    MINIO_ACCESS_KEY="minioadmin" \
    MINIO_SECRET_KEY="minioadmin123" \
    TIANJING_KAFKA_BOOTSTRAP_SERVERS="localhost:9094" \
    GPU_INFER_URL="http://localhost:8102" \
    INFER_PROXY_URL="http://localhost:8092" \
    PORT=8103 \
    env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY \
    nohup "$UV_BIN" run \
      --python 3.10 \
      --with "fastapi==0.110.0" \
      --with "uvicorn[standard]==0.29.0" \
      --with "pydantic==2.6.4" \
      --with "kafka-python==2.0.2" \
      --with "lz4==4.3.3" \
      --with "minio==7.2.5" \
      --with "httpx==0.27.0" \
      --with "structlog==24.1.0" \
      python3 src/main.py > "$log" 2>&1 &
    echo -e "  ${GREEN}已启动${RESET} infer-dispatcher  (PID=$!, port=8103, log=$log)"
  )
}

stop_infer_dispatcher() {
  local pid
  pid=$(get_pid_on_port 8103)
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null && echo -e "  ${YELLOW}停止${RESET} infer-dispatcher (PID=$pid)" || true
  fi
}

# ─── gpu-infer-service（Python FastAPI，端口 8102，GPU-03/05/07）──────────────

GPU_INFER_DIR="$ROOT_DIR/inference/gpu-infer-service"

start_gpu_infer_service() {
  if is_port_listening 8102; then
    echo -e "  ${CYAN}跳过${RESET} gpu-infer-service — 端口 8102 已被占用（已运行）"
    return 0
  fi
  if [ ! -f "$UV_BIN" ]; then
    echo -e "  ${RED}跳过${RESET} gpu-infer-service — uv 未找到 ($UV_BIN)"
    return 1
  fi
  local log="$LOG_DIR/gpu-infer-service.log"
  # INFER_BACKEND：onnx（默认）| tensorrt（GPU-07：TensorRT FP16，需先执行 convert_to_tensorrt.py）
  local backend="${INFER_BACKEND:-onnx}"
  local model_path="${ONNX_MODEL_PATH:-/models/yolov8n.onnx}"
  local trt_path="${TRT_ENGINE_PATH:-/models/yolov8n_fp16.trt}"
  (
    cd "$GPU_INFER_DIR"
    INFER_BACKEND="$backend" \
    ONNX_MODEL_PATH="$model_path" \
    TRT_ENGINE_PATH="$trt_path" \
    PORT=8102 \
    env -u http_proxy -u https_proxy -u all_proxy -u HTTP_PROXY -u HTTPS_PROXY \
    nohup "$UV_BIN" run \
      --python 3.10 \
      --with "fastapi==0.110.0" \
      --with "uvicorn[standard]==0.29.0" \
      --with "pydantic==2.6.4" \
      --with "numpy==1.26.4" \
      --with "opencv-python-headless==4.9.0.80" \
      --with "onnxruntime-gpu==1.19.2" \
      --with "structlog==24.1.0" \
      --with "prometheus-client==0.20.0" \
      python3 src/main.py > "$log" 2>&1 &
    echo -e "  ${GREEN}已启动${RESET} gpu-infer-service  (PID=$!, port=8102, backend=$backend, log=$log)"
  )
}

stop_gpu_infer_service() {
  local pid
  pid=$(get_pid_on_port 8102)
  if [ -n "$pid" ]; then
    kill "$pid" 2>/dev/null && echo -e "  ${YELLOW}停止${RESET} gpu-infer-service (PID=$pid)" || true
  fi
}

# ─── 从 PostgreSQL 恢复 Redis 路由缓存（防 Redis 重启后路由缓存丢失导致丢帧）──────

restore_scene_route_cache() {
  echo -e "\n${BOLD}▶ 恢复活跃场景路由缓存到 Redis...${RESET}"
  if [ ! -f "$UV_BIN" ]; then
    echo -e "  ${YELLOW}跳过${RESET} Redis 路由缓存恢复 — uv 未找到 ($UV_BIN)"
    return 0
  fi
  local restore_script="$SCRIPT_DIR/restore_redis_routes.py"
  if [ ! -f "$restore_script" ]; then
    echo -e "  ${YELLOW}跳过${RESET} Redis 路由缓存恢复 — 脚本不存在 ($restore_script)"
    return 0
  fi
  if "$UV_BIN" run \
      --with "psycopg2-binary" \
      --with "redis" \
      python3 "$restore_script"; then
    echo -e "  ${GREEN}Redis 路由缓存恢复完成${RESET}"
  else
    echo -e "  ${RED}Redis 路由缓存恢复失败，请检查 PostgreSQL / Redis 连通性${RESET}"
    echo -e "  ${YELLOW}提示：可手动执行: $UV_BIN run --with psycopg2-binary --with redis python3 $restore_script${RESET}"
  fi
}

gateway_status() {
  local state
  state=$(docker inspect -f '{{.State.Running}}' tianjing-gateway 2>/dev/null || echo "missing")
  if [ "$state" = "true" ]; then
    printf "  ${GREEN}✅ UP${RESET}  %-35s port=%s\n" "tianjing-gateway (nginx)" "8079"
    return 0
  else
    printf "  ${RED}❌ DOWN   ${RESET} %-35s port=%s\n" "tianjing-gateway (nginx)" "8079"
    return 1
  fi
}

show_status() {
  echo -e "\n${BOLD}═══ 服务健康状态 ═══${RESET}"
  local ok=0 fail=0
  for entry in "${SERVICES[@]}"; do
    local name=${entry%%:*} port=${entry##*:}
    local code
    code=$(health_check "$port")
    if [ "$code" = "200" ]; then
      printf "  ${GREEN}✅ UP${RESET}  %-35s port=%s\n" "$name" "$port"
      ((ok++)) || true
    else
      local label="${code:-NO_CONN}"
      printf "  ${RED}❌ %-6s${RESET} %-35s port=%s\n" "$label" "$name" "$port"
      ((fail++)) || true
    fi
  done
  # 检查 Python 推理服务（不在 SERVICES 数组中）
  for entry in "gpu-infer-service:8102" "infer-dispatcher:8103" "recording-replay-service:8091" "cloud-inference-proxy:8092"; do
    local name=${entry%%:*} port=${entry##*:}
    local code
    code=$(health_check "$port")
    if [ "$code" = "200" ]; then
      printf "  ${GREEN}✅ UP${RESET}  %-35s port=%s\n" "$name" "$port"
      ((ok++)) || true
    else
      local label="${code:-NO_CONN}"
      printf "  ${RED}❌ %-6s${RESET} %-35s port=%s\n" "$label" "$name" "$port"
      ((fail++)) || true
    fi
  done
  echo ""
  local total=$((${#SERVICES[@]} + 4))
  if [ "$fail" -eq 0 ]; then
    echo -e "${GREEN}${BOLD}全部 $ok/$total 服务正常运行${RESET}"
  else
    echo -e "${YELLOW}${BOLD}$ok/$total UP，$fail 个异常${RESET}"
  fi
}

# ─── 入口 ─────────────────────────────────────────────────────────────────────

MODE="${1:-start}"

case "$MODE" in
  --stop)
    echo -e "\n${BOLD}▶ 停止所有后端服务...${RESET}"
    for entry in "${SERVICES[@]}"; do
      stop_service "${entry%%:*}" "${entry##*:}"
    done
    stop_gpu_infer_service
    stop_infer_dispatcher
    stop_recording_replay_service
    stop_cloud_inference_proxy
    stop_gateway
    echo -e "${GREEN}完成${RESET}"
    exit 0
    ;;

  --status)
    gateway_status || true
    show_status
    exit 0
    ;;

  --build)
    build_all
    MODE="start"
    ;;

  --restart)
    echo -e "\n${BOLD}▶ 停止现有服务...${RESET}"
    for entry in "${SERVICES[@]}"; do
      stop_service "${entry%%:*}" "${entry##*:}"
    done
    stop_gpu_infer_service
    stop_infer_dispatcher
    stop_recording_replay_service
    stop_cloud_inference_proxy
    stop_gateway
    sleep 2
    MODE="start"
    ;;

  start|"")
    MODE="start"
    ;;

  *)
    echo "用法: $0 [--start|--restart|--stop|--status|--build]"
    exit 1
    ;;
esac

# ─── 启动网关 + 推理代理 + 所有服务 ─────────────────────────────────────────
echo -e "\n${BOLD}▶ 启动 API 网关 + 推理代理 + 后端服务...${RESET}"
start_gateway
start_gpu_infer_service
start_infer_dispatcher
start_recording_replay_service
start_cloud_inference_proxy
for entry in "${SERVICES[@]}"; do
  start_service "${entry%%:*}" "${entry##*:}"
done

# ─── 等待启动并检查健康状态 ───────────────────────────────────────────────────
echo -e "\n${CYAN}等待服务启动（约 45 秒）...${RESET}"
sleep 45

gateway_status || true
show_status

# ─── 恢复 Redis 路由缓存（防 Redis 重启后 volatile key 丢失导致静默丢帧）────────
restore_scene_route_cache
