#!/usr/bin/env bash
# ============================================================
# 烧结看火录像导入脚本（MinIO）
# Sprint：S0-07
# 说明：将烧结看火录像文件上传至 MinIO tianjing-lab-video/ bucket
#       文件完整性由 SHA-256 校验保证
# 使用方法：
#   LOCAL_VIDEO_DIR=/path/to/videos \
#   MINIO_ENDPOINT=http://minio.tianjing.internal:9000 \
#   MINIO_ACCESS_KEY=xxx \
#   MINIO_SECRET_KEY=xxx \
#   bash minio-video-import.sh
# ============================================================

set -euo pipefail

# ==============================
# 配置项
# ==============================
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://tianjing-minio.middleware.svc.cluster.local:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-PLACEHOLDER}"
MINIO_BUCKET="tianjing-lab-video"
MINIO_PREFIX="sintering/sinter-fire-watch"

# 本地录像文件目录（支持 mp4/avi/mkv）
LOCAL_VIDEO_DIR="${LOCAL_VIDEO_DIR:-./lab-videos}"

# 最小文件大小（字节）：30分钟视频约 1GB，设置 100MB 下限作为完整性初步校验
MIN_FILE_SIZE_BYTES=104857600

# ==============================
# 颜色输出
# ==============================
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ==============================
# 前置检查
# ==============================
check_prerequisites() {
    log_info "检查依赖工具..."
    if ! command -v mc &>/dev/null; then
        log_error "未找到 MinIO Client (mc)，请先安装：https://min.io/docs/minio/linux/reference/minio-mc.html"
        exit 1
    fi
    if ! command -v sha256sum &>/dev/null; then
        log_error "未找到 sha256sum，请安装 coreutils"
        exit 1
    fi
    if ! command -v ffprobe &>/dev/null; then
        log_warn "未找到 ffprobe（可选），无法验证视频时长"
    fi
    log_info "依赖检查通过"
}

# ==============================
# 配置 MinIO Client
# ==============================
setup_minio_client() {
    log_info "配置 MinIO Client..."
    mc alias set tianjing \
        "${MINIO_ENDPOINT}" \
        "${MINIO_ACCESS_KEY}" \
        "${MINIO_SECRET_KEY}" \
        --api S3v4 \
        2>/dev/null

    # 检查 bucket 是否存在
    if ! mc ls "tianjing/${MINIO_BUCKET}" &>/dev/null; then
        log_info "创建 bucket: ${MINIO_BUCKET}"
        mc mb "tianjing/${MINIO_BUCKET}"
    fi
    log_info "MinIO 连接正常，bucket: ${MINIO_BUCKET}"
}

# ==============================
# 上传单个文件
# ==============================
upload_video() {
    local local_path="$1"
    local filename
    filename=$(basename "${local_path}")
    local remote_path="tianjing/${MINIO_BUCKET}/${MINIO_PREFIX}/${filename}"

    # 文件大小校验
    local file_size
    file_size=$(stat -c%s "${local_path}")
    if [ "${file_size}" -lt "${MIN_FILE_SIZE_BYTES}" ]; then
        log_warn "文件 ${filename} 大小 $(numfmt --to=iec ${file_size}) 偏小，请确认是否完整"
    fi

    # 计算本地 SHA-256
    log_info "计算校验值: ${filename}"
    local local_checksum
    local_checksum=$(sha256sum "${local_path}" | awk '{print $1}')
    echo "  SHA-256: ${local_checksum}"

    # 检查视频时长（若有 ffprobe）
    if command -v ffprobe &>/dev/null; then
        local duration
        duration=$(ffprobe -v quiet -show_entries format=duration \
            -of default=noprint_wrappers=1:nokey=1 "${local_path}" 2>/dev/null || echo "unknown")
        if [ "${duration}" != "unknown" ] && (( $(echo "${duration} < 1800" | bc -l) )); then
            log_warn "视频时长 ${duration}s 少于 30 分钟（要求：≥30 分钟含正常/异常片段）"
        else
            log_info "  视频时长: ${duration}s ($(echo "${duration}/60" | bc) 分钟)"
        fi
    fi

    # 检查是否已存在（避免重复上传）
    if mc stat "${remote_path}" &>/dev/null; then
        log_warn "文件已存在于 MinIO，跳过: ${filename}"
        return 0
    fi

    # 上传文件（附带 SHA-256 元数据）
    log_info "上传中: ${filename} ($(numfmt --to=iec ${file_size}))"
    mc cp \
        --attr "x-amz-meta-sha256=${local_checksum};x-amz-meta-scene=SCENE-SINTER-FIRE-001;x-amz-meta-source=lab-recording" \
        "${local_path}" \
        "${remote_path}"

    # 验证上传完整性
    log_info "验证完整性..."
    local remote_checksum
    remote_checksum=$(mc cat "${remote_path}" | sha256sum | awk '{print $1}')

    if [ "${local_checksum}" = "${remote_checksum}" ]; then
        log_info "  ✓ 完整性校验通过: ${filename}"
    else
        log_error "  ✗ 完整性校验失败: ${filename}"
        log_error "    本地:  ${local_checksum}"
        log_error "    远端: ${remote_checksum}"
        # 删除损坏的文件
        mc rm "${remote_path}"
        return 1
    fi
}

# ==============================
# 验证上传结果（打印文件列表）
# ==============================
verify_upload() {
    log_info "=== 已上传文件列表 ==="
    mc ls --recursive "tianjing/${MINIO_BUCKET}/${MINIO_PREFIX}/"
    log_info "=== 验证完成，平台服务可访问路径："
    echo "  minio://${MINIO_BUCKET}/${MINIO_PREFIX}/"
    echo "  HTTP:   ${MINIO_ENDPOINT}/${MINIO_BUCKET}/${MINIO_PREFIX}/"
}

# ==============================
# 主流程
# ==============================
main() {
    echo "=========================================="
    echo "  天柱·天镜 烧结看火录像 MinIO 导入工具"
    echo "  Sprint: S0-07"
    echo "=========================================="

    check_prerequisites
    setup_minio_client

    # 查找视频文件
    if [ ! -d "${LOCAL_VIDEO_DIR}" ]; then
        log_error "视频目录不存在: ${LOCAL_VIDEO_DIR}"
        log_error "请设置环境变量 LOCAL_VIDEO_DIR 指向录像文件目录"
        exit 1
    fi

    VIDEO_COUNT=0
    FAIL_COUNT=0

    while IFS= read -r video_file; do
        if upload_video "${video_file}"; then
            VIDEO_COUNT=$((VIDEO_COUNT + 1))
        else
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
    done < <(find "${LOCAL_VIDEO_DIR}" -type f \( -iname "*.mp4" -o -iname "*.avi" -o -iname "*.mkv" -o -iname "*.mov" \) | sort)

    if [ "${VIDEO_COUNT}" -eq 0 ] && [ "${FAIL_COUNT}" -eq 0 ]; then
        log_warn "未在 ${LOCAL_VIDEO_DIR} 找到视频文件（支持格式：mp4/avi/mkv/mov）"
        exit 1
    fi

    verify_upload

    echo ""
    log_info "=== 导入完成: 成功 ${VIDEO_COUNT} 个，失败 ${FAIL_COUNT} 个 ==="

    if [ "${FAIL_COUNT}" -gt 0 ]; then
        exit 1
    fi
}

main "$@"
