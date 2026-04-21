#!/usr/bin/env bash
# =============================================================================
# 天柱·天镜 — YOLOv8 GPU 训练启动脚本
#
# 用法：
#   ./start_training.sh                         # 默认：全量合并，100 epoch
#   ./start_training.sh --datasets NEU_surface_defect_converted
#   ./start_training.sh --datasets all --epochs 50 --model yolov8n
#   ./start_training.sh --datasets GC10-DET_converted,Severstal_converted
#   ./start_training.sh --status               # 查看当前训练进度
#   ./start_training.sh --list                 # 列出可用数据集
#
# 训练在 Docker 容器内运行，日志同时写入：
#   /tmp/tianjing-logs/train_gpu_{job_id}.log
#
# 实时进度：
#   tail -f /tmp/tianjing-logs/train_gpu_*.log
#   tail -f /tmp/tianjing-logs/train_gpu_*.log | grep -E "Epoch|mAP50|完成"
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="/tmp/tianjing-logs"
TRAIN_IMAGE="tianjing/train-yolo:cu121"
DATASETS_DIR="$ROOT_DIR/training/datasets_converted"
TRAIN_SCRIPT="$ROOT_DIR/training/train_jobs/train_yolo_gpu.py"

# UV 缓存中的 torch 2.2.2+cu121（Python 3.11）路径
UV_TORCH_SITE="/home/tzai/.cache/uv/archive-v0/W3NNJIFGRl9Wv1R9aaspL/lib/python3.11/site-packages"
UV_PY311="/home/tzai/.local/share/uv/python/cpython-3.11-linux-x86_64-gnu/bin/python3.11"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
BOLD='\033[1m'; RESET='\033[0m'

mkdir -p "$LOG_DIR"

# ─── 解析参数 ─────────────────────────────────────────────────────────────
DATASETS="all"
MODEL="yolov8s"
EPOCHS=100
BATCH=32
IMG_SIZE=640
DEVICE="0"
JOB_ID="TJ-GPU-$(date +%Y%m%d%H%M%S)"

while [[ $# -gt 0 ]]; do
  case $1 in
    --datasets)  DATASETS="$2";  shift 2 ;;
    --model)     MODEL="$2";     shift 2 ;;
    --epochs)    EPOCHS="$2";    shift 2 ;;
    --batch)     BATCH="$2";     shift 2 ;;
    --img-size)  IMG_SIZE="$2";  shift 2 ;;
    --device)    DEVICE="$2";    shift 2 ;;
    --job-id)    JOB_ID="$2";    shift 2 ;;
    --status)
      echo -e "\n${BOLD}═══ 当前训练任务 ═══${RESET}"
      docker ps --filter "name=tianjing-train" \
        --format "table {{.Names}}\t{{.Status}}\t{{.RunningFor}}" 2>/dev/null || true
      echo ""
      echo -e "${BOLD}最新日志文件：${RESET}"
      ls -t "$LOG_DIR"/train_gpu_*.log 2>/dev/null | head -3 | while read f; do
        echo "  $f"
        tail -3 "$f" 2>/dev/null | sed 's/^/    /'
      done
      exit 0
      ;;
    --list)
      echo -e "\n${BOLD}可用数据集：${RESET}"
      ls "$DATASETS_DIR" 2>/dev/null | grep -v README | while read d; do
        ann="$DATASETS_DIR/$d/annotations.json"
        if [ -f "$ann" ]; then
          imgs=$(python3 -c "import json; d=json.load(open('$ann')); print(len(d.get('images',[])))" 2>/dev/null)
          cats=$(python3 -c "import json; d=json.load(open('$ann')); print(len(d.get('categories',[])))" 2>/dev/null)
          printf "  %-45s %s 张图  %s 类\n" "$d" "$imgs" "$cats"
        fi
      done
      exit 0
      ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

# ─── 检查运行模式（Docker模式优先）──────────────────────────────────────
USE_UV=false

# Docker 镜像存在则优先使用 Docker 模式
if docker image inspect "$TRAIN_IMAGE" &>/dev/null; then
  echo -e "  ${GREEN}[Docker模式] 使用镜像 $TRAIN_IMAGE${RESET}"
  # 检查是否有训练在跑
  if docker ps --filter "name=tianjing-train" --format "{{.Names}}" | grep -q "tianjing-train"; then
    echo -e "${YELLOW}⚠  已有训练任务在运行，请先停止或等待完成：${RESET}"
    docker ps --filter "name=tianjing-train" --format "  {{.Names}}  {{.Status}}"
    echo ""
    echo "  停止命令：docker stop \$(docker ps -q --filter name=tianjing-train)"
    exit 1
  fi
else
  # 镜像不存在，尝试构建；构建失败则回退到 UV 模式
  echo -e "${YELLOW}训练镜像 $TRAIN_IMAGE 不存在，开始构建...${RESET}"
  if docker build -f /tmp/Dockerfile.train -t "$TRAIN_IMAGE" /tmp/ 2>/dev/null; then
    echo -e "  ${GREEN}镜像构建完成${RESET}"
  else
    # 回退到 UV 模式
    if [ -f "$UV_PY311" ] && [ -d "$UV_TORCH_SITE" ]; then
      if PYTHONPATH="$UV_TORCH_SITE" "$UV_PY311" -c "import torch; exit(0 if torch.cuda.is_available() else 1)" 2>/dev/null; then
        USE_UV=true
        echo -e "  ${YELLOW}[UV模式] Docker 构建失败，回退到本地 torch（部分依赖可能缺失）${RESET}"
      fi
    fi
    if [ "$USE_UV" = "false" ]; then
      echo -e "${RED}错误：无可用训练环境（Docker 镜像构建失败，UV 模式不可用）${RESET}"
      exit 1
    fi
  fi
fi

LOG_FILE="$LOG_DIR/train_gpu_${JOB_ID}.log"

echo -e "\n${BOLD}▶ 启动 YOLOv8 GPU 训练${RESET}"
echo -e "  Job ID   : ${CYAN}${JOB_ID}${RESET}"
echo -e "  数据集   : ${CYAN}${DATASETS}${RESET}"
echo -e "  模型     : ${CYAN}${MODEL}${RESET}"
echo -e "  Epochs   : ${CYAN}${EPOCHS}${RESET}  Batch: ${CYAN}${BATCH}${RESET}  ImgSize: ${CYAN}${IMG_SIZE}${RESET}"
echo -e "  GPU      : ${CYAN}${DEVICE}${RESET}"
echo -e "  日志文件 : ${CYAN}${LOG_FILE}${RESET}"

# ─── 启动训练 ─────────────────────────────────────────────────────────────
if [ "$USE_UV" = "true" ]; then
  # UV 直接模式：用本地 Python 3.11 + 缓存的 cu121 torch
  nohup env PYTHONPATH="$UV_TORCH_SITE" \
    TIANJING_MINIO_ENDPOINT="http://localhost:9000" \
    TIANJING_MINIO_ACCESS_KEY=minioadmin \
    TIANJING_MINIO_SECRET_KEY=minioadmin123 \
    MLFLOW_TRACKING_URI="http://localhost:5000" \
    PLATFORM_CALLBACK_URL="http://localhost:8089" \
    MODEL_REGISTER_URL="http://localhost:8086" \
    CUDA_VISIBLE_DEVICES="$DEVICE" \
    "$UV_PY311" "$TRAIN_SCRIPT" \
      --datasets "$DATASETS" \
      --model "$MODEL" \
      --epochs "$EPOCHS" \
      --batch "$BATCH" \
      --img-size "$IMG_SIZE" \
      --device 0 \
      --job-id "$JOB_ID" > "$LOG_FILE" 2>&1 &
  TRAIN_PID=$!
  echo ""
  echo -e "${GREEN}训练已在后台启动！(UV模式 PID: $TRAIN_PID)${RESET}"
  echo ""
  echo -e "  停止训练："
  echo -e "    ${BOLD}kill $TRAIN_PID${RESET}"
else
  # Docker 模式
  docker run -d \
    --name "tianjing-train-${JOB_ID}" \
    --gpus "\"device=${DEVICE}\"" \
    --shm-size=8g \
    -v "$DATASETS_DIR":/home/tzai/tianjing/training/datasets_converted:ro \
    -v "$ROOT_DIR/training/train_jobs":/scripts:ro \
    -v "$LOG_DIR":/tmp/tianjing-logs \
    -v /tmp/train_gpu:/tmp/train_gpu \
    -e TIANJING_MINIO_ENDPOINT="http://host.docker.internal:9000" \
    -e TIANJING_MINIO_ACCESS_KEY=minioadmin \
    -e TIANJING_MINIO_SECRET_KEY=minioadmin123 \
    -e MLFLOW_TRACKING_URI="http://host.docker.internal:5000" \
    -e PLATFORM_CALLBACK_URL="http://host.docker.internal:8089" \
    -e MODEL_REGISTER_URL="http://host.docker.internal:8086" \
    --add-host=host.docker.internal:host-gateway \
    "$TRAIN_IMAGE" \
    python3 /scripts/train_yolo_gpu.py \
      --datasets "$DATASETS" \
      --model "$MODEL" \
      --epochs "$EPOCHS" \
      --batch "$BATCH" \
      --img-size "$IMG_SIZE" \
      --device 0 \
      --job-id "$JOB_ID" > "$LOG_FILE" 2>&1
  echo ""
  echo -e "${GREEN}训练已在后台启动！(Docker模式)${RESET}"
  echo ""
  echo -e "  停止训练："
  echo -e "    ${BOLD}docker stop tianjing-train-${JOB_ID}${RESET}"
fi

echo ""
echo -e "  实时进度（所有输出）："
echo -e "    ${BOLD}tail -f $LOG_FILE${RESET}"
echo ""
echo -e "  只看 epoch 进度："
echo -e "    ${BOLD}tail -f $LOG_FILE | grep -E 'Epoch|mAP50|完成|失败'${RESET}"
