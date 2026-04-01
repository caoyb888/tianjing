#!/usr/bin/env bash
# ============================================================
# Kafka Topic 预建脚本
# Sprint：S0-02
# 说明：在 Kafka 集群就绪后创建平台所需的全部 Topic
#       按 CLAUDE.md §8.1 命名规范执行
# 使用方法：
#   KAFKA_BOOTSTRAP=kafka.middleware.svc.cluster.local:9092 bash kafka-topics-init.sh
# ============================================================

set -euo pipefail

KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-kafka.middleware.svc.cluster.local:9092}"
REPLICATION_FACTOR="${REPLICATION_FACTOR:-2}"

echo "=== 天柱·天镜 Kafka Topic 初始化 ==="
echo "Bootstrap: ${KAFKA_BOOTSTRAP}"
echo "Replication Factor: ${REPLICATION_FACTOR}"
echo ""

# Kafka topic 创建函数
create_topic() {
    local topic_name="$1"
    local partitions="$2"
    local retention_ms="$3"
    local extra_config="${4:-}"

    echo "Creating topic: ${topic_name} (partitions=${partitions}, retention=${retention_ms}ms)"

    CONFIG_ARGS="retention.ms=${retention_ms}"
    if [ -n "${extra_config}" ]; then
        CONFIG_ARGS="${CONFIG_ARGS} --config ${extra_config}"
    fi

    kafka-topics.sh \
        --bootstrap-server "${KAFKA_BOOTSTRAP}" \
        --create \
        --if-not-exists \
        --topic "${topic_name}" \
        --partitions "${partitions}" \
        --replication-factor "${REPLICATION_FACTOR}" \
        --config "retention.ms=${retention_ms}" \
        --config "compression.type=lz4" \
        --config "min.insync.replicas=1"

    echo "  ✓ ${topic_name} created"
}

# ==============================
# 视频帧 Topics
# ==============================
echo "--- 视频帧 Topics ---"
# 生产推理帧（25fps，高优先级，按 scene_id 哈希分区）
# 4分区 × 3节点，支持 16 路场景并发
create_topic "tianjing.frame.production" 16 "604800000"    # 保留 7 天

# Sandbox 推理帧（5fps 降频镜像，由 traffic-mirror-service 写入）
create_topic "tianjing.frame.sandbox"    8  "604800000"

# ==============================
# 推理结果 Topics
# ==============================
echo "--- 推理结果 Topics ---"
create_topic "tianjing.infer.result.production" 16 "604800000"
create_topic "tianjing.infer.result.sandbox"    8  "604800000"

# ==============================
# 告警 Topics（按级别分离，消费者优先级不同）
# ==============================
echo "--- 告警 Topics ---"
# CRITICAL 告警（消费者：notification-service，不丢失）
# 注：acks=all 是生产者参数，不能作为 topic config
#     min.insync.replicas 在单节点环境下设为 1，生产多节点集群改回 2
kafka-topics.sh \
    --bootstrap-server "${KAFKA_BOOTSTRAP}" \
    --create \
    --if-not-exists \
    --topic "tianjing.alarm.critical" \
    --partitions 4 \
    --replication-factor "${REPLICATION_FACTOR}" \
    --config "retention.ms=2592000000" \
    --config "compression.type=lz4" \
    --config "min.insync.replicas=1"
echo "  ✓ tianjing.alarm.critical created"

# WARNING 告警
create_topic "tianjing.alarm.warning" 4 "2592000000"   # 保留 30 天

# INFO 仅持久化，不推送
create_topic "tianjing.alarm.info"    4 "604800000"    # 保留 7 天

# ==============================
# 感知健康 Topics
# ==============================
echo "--- 感知健康 Topics ---"
create_topic "tianjing.health.camera" 4 "86400000"     # 保留 1 天

# ==============================
# 模型漂移 Topics
# ==============================
echo "--- 模型漂移 Topics ---"
create_topic "tianjing.drift.feedback" 4 "2592000000"  # 保留 30 天

# ==============================
# 审计 Topics
# ==============================
echo "--- 审计 Topics ---"
create_topic "tianjing.audit.data_transfer" 2 "31536000000"  # 审计日志保留 1 年

# ==============================
# 验证
# ==============================
echo ""
echo "=== Topic 列表（验证） ==="
kafka-topics.sh \
    --bootstrap-server "${KAFKA_BOOTSTRAP}" \
    --list | grep "tianjing\." | sort

echo ""
echo "=== 初始化完成 ==="
