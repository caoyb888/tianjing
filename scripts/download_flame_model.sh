#!/bin/bash
# ============================================================
# 从 MinIO 下载 CLASSIFY-FLAME-V1 ONNX 模型到本地
# 供 classify-flame-infer 服务启动时挂载使用
#
# 用法：bash scripts/download_flame_model.sh [job_id]
# 示例：bash scripts/download_flame_model.sh FLAME-20260423014138
# ============================================================
set -euo pipefail

JOB_ID="${1:-FLAME-20260423014138}"
DEST_DIR="/tmp/tianjing-models/CLASSIFY-FLAME-V1"
DEST_FILE="$DEST_DIR/best.onnx"
MINIO_ENDPOINT="${TIANJING_MINIO_ENDPOINT:-http://localhost:9000}"
MINIO_AK="${TIANJING_MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SK="${TIANJING_MINIO_SECRET_KEY:-minioadmin123}"
BUCKET="tianjing-models-staging"
OBJ_PATH="CLASSIFY-FLAME-V1/${JOB_ID}/best.onnx"

mkdir -p "$DEST_DIR"

echo "从 MinIO 下载 CLASSIFY-FLAME-V1 模型..."
echo "  源: ${MINIO_ENDPOINT}/${BUCKET}/${OBJ_PATH}"
echo "  目标: ${DEST_FILE}"

sg docker -c "docker exec tianjing-minio \
  mc alias set local ${MINIO_ENDPOINT} ${MINIO_AK} ${MINIO_SK} --api S3v4 2>/dev/null; \
  mc cp local/${BUCKET}/${OBJ_PATH} /tmp/flame_best.onnx" 2>/dev/null \
  || docker run --rm --network host \
       -v "$DEST_DIR:/output" \
       python:3.11-slim \
       bash -c "pip install minio -q && python3 -c \"
from minio import Minio
import urllib.parse
ep = '${MINIO_ENDPOINT}'.replace('http://','').replace('https://','')
m = Minio(ep, access_key='${MINIO_AK}', secret_key='${MINIO_SK}', secure=False)
m.fget_object('${BUCKET}', '${OBJ_PATH}', '/output/best.onnx')
print('下载完成')
\""

if [ -f "$DEST_FILE" ]; then
    SIZE=$(du -sh "$DEST_FILE" | cut -f1)
    echo "模型下载成功: $DEST_FILE ($SIZE)"
    echo "启动 classify-flame-infer 时设置环境变量："
    echo "  export FLAME_ONNX_MODEL_PATH=$DEST_FILE"
else
    echo "下载失败，请手动从 MinIO 下载："
    echo "  ${MINIO_ENDPOINT}/${BUCKET}/${OBJ_PATH}"
    exit 1
fi
