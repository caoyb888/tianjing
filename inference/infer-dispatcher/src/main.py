"""
infer-dispatcher — 推理调度器
GPU-05（V3.0 阶段二）

功能：
  消费 tianjing.frame.production.dispatch（由 route-dispatch-service 注入 plugin_id 后写入）
  → 解析 minio:// image_url，从 MinIO 下载原始帧图像
  → 转 Base64，POST /infer 至目标推理服务（按 plugin_id 路由）
  → 将 InferResponse 写入 tianjing.infer.result.production

plugin_id 路由表：
  LOCAL-GPU-YOLO-V1  → http://localhost:8102  （本地 V100 GPU 推理服务，GPU-05）
  未知插件           → http://localhost:8092  （cloud-inference-proxy 兜底）

规范：
  CLAUDE.md §5.2  （Python 算法服务规范）
  CLAUDE.md §8.1  （Kafka Topic 命名与分区）
  CLAUDE.md §8.2  （消费者组命名规范）
  CLAUDE.md §11.1 （is_sandbox 原样透传，不得修改）
  CLAUDE.md §14.1 （结构化日志，必须包含 scene_id、is_sandbox、trace_id）

端口: 8103（CLAUDE.md §15 P1-8：从 8102 起续编）
"""

import base64
import json
import os
import signal
import threading
import time
from urllib.parse import urlparse

import httpx
import structlog
from fastapi import FastAPI
from kafka import KafkaConsumer, KafkaProducer
from minio import Minio

logger = structlog.get_logger()

# ── 环境变量配置 ──────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP  = os.getenv("TIANJING_KAFKA_BOOTSTRAP_SERVERS", "localhost:9094")
MINIO_ENDPOINT   = os.getenv("TIANJING_MINIO_ENDPOINT", "http://localhost:9000")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minioadmin123")
PORT             = int(os.getenv("PORT", "8103"))

DISPATCH_TOPIC = "tianjing.frame.production.dispatch"
RESULT_TOPIC   = "tianjing.infer.result.production"
CONSUMER_GROUP = "infer-dispatcher-prod-cg"   # CLAUDE.md §8.2：{service-name}-{env}-cg

# plugin_id → 推理服务 URL（LOCAL_GPU 插件有自己的推理地址）
PLUGIN_ENDPOINTS = {
    "LOCAL-GPU-YOLO-V1": os.getenv("GPU_INFER_URL", "http://localhost:8102"),
}
DEFAULT_INFER_URL = os.getenv("INFER_PROXY_URL", "http://localhost:8092")

# ── 全局健康状态 ──────────────────────────────────────────────────────────────
_consumer_alive  = False
_processed_total = 0
_error_total     = 0
_start_time      = time.time()
_shutdown_event  = threading.Event()

# ── MinIO 客户端 ──────────────────────────────────────────────────────────────
def _build_minio_client() -> Minio:
    parsed   = urlparse(MINIO_ENDPOINT)
    endpoint = parsed.netloc           # host:port
    secure   = (parsed.scheme == "https")
    return Minio(endpoint, access_key=MINIO_ACCESS_KEY,
                 secret_key=MINIO_SECRET_KEY, secure=secure)


_minio_client = _build_minio_client()

# ── Kafka Producer（acks=all 确保结果不丢失）────────────────────────────────
_producer = KafkaProducer(
    bootstrap_servers=KAFKA_BOOTSTRAP,
    value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode("utf-8"),
    key_serializer=lambda k: k.encode("utf-8") if k else None,
    acks="all",
    retries=3,
    linger_ms=5,
)

# ── HTTP 客户端（强制 HTTP/1.1，uvicorn 不支持 h2c 升级，CLAUDE.md PluginHealthChecker 同款约束）──
_http_client = httpx.Client(
    http1=True,
    http2=False,
    timeout=httpx.Timeout(connect=5.0, read=30.0, write=10.0, pool=5.0),
)


# ════════════════════════════════════════════════════════════
# 核心处理逻辑
# ════════════════════════════════════════════════════════════

def _download_image_b64(image_url: str) -> str:
    """
    从 MinIO 下载帧图像，返回 Base64 编码字节串（JPEG/PNG 原始字节）。
    支持格式：minio://{bucket}/{object_path}
    示例：minio://tianjing-frames-prod/sintering/SCENE-SINTER-FIRE-001/REALTIME/f00001.jpg
    """
    parsed = urlparse(image_url)
    if parsed.scheme != "minio":
        raise ValueError(f"不支持的图像 URL scheme：{image_url}")
    bucket      = parsed.netloc
    object_path = parsed.path.lstrip("/")

    response = _minio_client.get_object(bucket, object_path)
    try:
        data = response.read()
    finally:
        response.close()
        response.release_conn()

    return base64.b64encode(data).decode("utf-8")


def _call_infer(plugin_id: str, image_b64: str, frame: dict) -> dict:
    """
    向目标推理服务 POST /infer，返回解析后的响应 JSON。
    plugin_id 路由：LOCAL-GPU-YOLO-V1 → 8102；其余 → 代理 8092（兜底）。
    is_sandbox 原样透传至推理服务，禁止在此修改（CLAUDE.md §11.1）。
    """
    target_url = PLUGIN_ENDPOINTS.get(plugin_id, DEFAULT_INFER_URL)
    body = {
        "image_b64":    image_b64,
        "scene_id":     frame["scene_id"],
        "frame_id":     frame["frame_id"],
        "timestamp_ms": frame["timestamp_ms"],
        "is_sandbox":   frame.get("is_sandbox", False),   # CLAUDE.md §11.1：原样透传
        "conf_threshold": 0.25,
        "iou_threshold":  0.45,
    }
    if frame.get("roi"):
        body["roi"] = frame["roi"]

    resp = _http_client.post(f"{target_url}/infer", json=body)
    resp.raise_for_status()
    return resp.json()


def _process_frame(frame: dict) -> None:
    """单帧完整处理：MinIO 下载 → GPU 推理 → 结果发布"""
    global _processed_total, _error_total
    t_start    = time.perf_counter()
    scene_id   = frame.get("scene_id", "unknown")
    frame_id   = frame.get("frame_id", "unknown")
    plugin_id  = frame.get("plugin_id", "LOCAL-GPU-YOLO-V1")
    is_sandbox = frame.get("is_sandbox", False)

    try:
        # 1. 从 MinIO 下载原始帧图像并编码
        image_b64 = _download_image_b64(frame["image_url"])

        # 2. 调用推理服务
        infer_result = _call_infer(plugin_id, image_b64, frame)

        # 3. 构造结果消息（CLAUDE.md §8.3 推理结果消息格式）
        result_msg = {
            "frame_id":         frame_id,
            "scene_id":         scene_id,
            "plugin_id":        infer_result.get("plugin_id", plugin_id),
            "is_sandbox":       is_sandbox,   # CLAUDE.md §11.1：原样透传，不得修改
            "detections":       infer_result.get("detections", []),
            "inference_time_ms": infer_result.get("inference_time_ms", 0),
            "timestamp_ms":     infer_result.get("timestamp_ms", frame["timestamp_ms"]),
            "backend":          infer_result.get("backend", "unknown"),
            "image_url":        frame.get("image_url", ""),   # 透传原始帧 MinIO URL，供大屏实时渲染
            "factory":          frame.get("factory", ""),     # 透传厂部，供 result-aggregate-service 写 TDengine TAG
            "dispatcher_total_ms": round((time.perf_counter() - t_start) * 1000, 2),
        }

        # 4. 写入结果 Topic（key=sceneId，保证同一场景帧有序，CLAUDE.md §8.1）
        _producer.send(RESULT_TOPIC, key=scene_id, value=result_msg)

        total_ms = round((time.perf_counter() - t_start) * 1000, 2)
        _processed_total += 1

        # 高频推理日志使用 DEBUG，避免日志洪流（CLAUDE.md §14.1）
        logger.debug("frame_dispatched",
                     scene_id=scene_id,
                     frame_id=frame_id,
                     is_sandbox=is_sandbox,    # CLAUDE.md §14.1：必须包含
                     plugin_id=plugin_id,
                     inference_ms=infer_result.get("inference_time_ms"),
                     dispatcher_total_ms=total_ms,
                     detection_count=len(result_msg["detections"]))

    except Exception as e:
        _error_total += 1
        logger.error("frame_dispatch_failed",
                     scene_id=scene_id,
                     frame_id=frame_id,
                     is_sandbox=is_sandbox,
                     plugin_id=plugin_id,
                     error=str(e))


# ════════════════════════════════════════════════════════════
# Kafka 消费循环（后台线程）
# ════════════════════════════════════════════════════════════

def _consumer_loop() -> None:
    """
    Kafka 消费循环，独立后台线程运行。
    消费者组：infer-dispatcher-prod-cg（CLAUDE.md §8.2）
    仅处理已知 plugin_id；未知插件记录 debug 日志后跳过（未来扩展时加入路由表）。
    """
    global _consumer_alive
    logger.info("infer_dispatcher_consumer_starting",
                topic=DISPATCH_TOPIC,
                group=CONSUMER_GROUP,
                bootstrap=KAFKA_BOOTSTRAP)

    consumer = KafkaConsumer(
        DISPATCH_TOPIC,
        bootstrap_servers=KAFKA_BOOTSTRAP,
        group_id=CONSUMER_GROUP,
        auto_offset_reset="latest",
        enable_auto_commit=True,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
        consumer_timeout_ms=1000,   # 每秒 poll 超时，允许优雅退出检测
    )
    _consumer_alive = True
    logger.info("infer_dispatcher_consumer_ready",
                topic=DISPATCH_TOPIC,
                group=CONSUMER_GROUP)

    try:
        while not _shutdown_event.is_set():
            for record in consumer:
                if _shutdown_event.is_set():
                    break
                frame     = record.value
                plugin_id = frame.get("plugin_id", "")

                # 仅处理本 dispatcher 已知的插件；未知插件直接跳过（不报错）
                if plugin_id and plugin_id not in PLUGIN_ENDPOINTS:
                    logger.debug("skip_unsupported_plugin",
                                 plugin_id=plugin_id,
                                 frame_id=frame.get("frame_id"))
                    continue

                _process_frame(frame)
    finally:
        consumer.close()
        _consumer_alive = False
        logger.info("infer_dispatcher_consumer_stopped")


# ════════════════════════════════════════════════════════════
# FastAPI 健康端点（端口 8103）
# ════════════════════════════════════════════════════════════

app = FastAPI(
    title="天柱·天镜 推理调度器",
    description="GPU-05：tianjing.frame.production.dispatch → GPU infer → tianjing.infer.result.production",
    version="1.0.0",
)


@app.get("/actuator/health")
async def health():
    """健康检查：返回消费者状态、处理计数、配置信息"""
    uptime_s = round(time.time() - _start_time)
    return {
        "status":           "UP" if _consumer_alive else "STARTING",
        "service":          "infer-dispatcher",
        "consumer_alive":   _consumer_alive,
        "processed_total":  _processed_total,
        "error_total":      _error_total,
        "uptime_seconds":   uptime_s,
        "dispatch_topic":   DISPATCH_TOPIC,
        "result_topic":     RESULT_TOPIC,
        "consumer_group":   CONSUMER_GROUP,
        "plugin_endpoints": PLUGIN_ENDPOINTS,
    }


# ════════════════════════════════════════════════════════════
# 入口
# ════════════════════════════════════════════════════════════

if __name__ == "__main__":
    import uvicorn

    # 后台线程启动 Kafka 消费循环
    consumer_thread = threading.Thread(
        target=_consumer_loop, daemon=True, name="kafka-consumer"
    )
    consumer_thread.start()

    def _on_signal(sig, frame):
        logger.info("shutdown_signal_received", sig=sig)
        _shutdown_event.set()

    signal.signal(signal.SIGTERM, _on_signal)
    signal.signal(signal.SIGINT, _on_signal)

    uvicorn.run(app, host="0.0.0.0", port=PORT, log_level="info")
