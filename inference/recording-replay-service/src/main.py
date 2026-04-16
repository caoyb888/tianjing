"""
录像回放服务（Recording Replay Service）
Sprint: S1-01（V2.0 使能组件①）
规范：API 接口规范 V3.1 §6.15（3 个内部接口：/start / /stop / /status）

功能：
  读取 MinIO 存储的录像文件，按配置帧率逐帧解码，
  帧数据 + 元数据封装后写入 Kafka tianjing.frame.production，
  与实时摄像头推送完全相同格式，平台下游无感知。

速率控制：0.5x / 1x / 2x，默认 10fps
"""

import asyncio
import base64
import json
import os
import threading
import time
import uuid
from typing import Optional, Dict

import cv2
import structlog
from fastapi import FastAPI, HTTPException
from kafka import KafkaProducer
from minio import Minio
from pydantic import BaseModel

logger = structlog.get_logger()

app = FastAPI(title="录像回放服务", version="1.0.0")

# ==============================
# 配置
# ==============================
MINIO_ENDPOINT = os.getenv("TIANJING_MINIO_ENDPOINT", "localhost:9000").replace("http://", "")
MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "minioadmin")
KAFKA_BOOTSTRAP = os.getenv("TIANJING_KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
LAB_VIDEO_BUCKET = "tianjing-lab-video"

# ==============================
# 全局状态（单机单任务设计）
# ==============================
_replay_sessions: Dict[str, dict] = {}  # session_id -> session_info
_replay_threads: Dict[str, threading.Thread] = {}

# ==============================
# Pydantic 请求/响应模型
# ==============================

class ReplayStartRequest(BaseModel):
    scene_id: str
    video_object_path: str  # MinIO 对象路径，如 sintering/sinter-fire-watch/xxx.mp4
    fps: float = 10.0       # 回放帧率（0.5x = 5fps，1x = 10fps，2x = 20fps）
    is_sandbox: bool = False
    loop: bool = False      # 是否循环回放


class ReplaySession(BaseModel):
    session_id: str
    scene_id: str
    status: str             # RUNNING / STOPPED / COMPLETED / FAILED
    video_object_path: str
    fps: float
    is_sandbox: bool
    frames_sent: int
    started_at: str
    stopped_at: Optional[str] = None
    error: Optional[str] = None


# ==============================
# 工厂函数
# ==============================

def _make_minio_client() -> Minio:
    return Minio(
        MINIO_ENDPOINT,
        access_key=MINIO_ACCESS_KEY,
        secret_key=MINIO_SECRET_KEY,
        secure=False
    )


def _make_kafka_producer() -> KafkaProducer:
    return KafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP,
        key_serializer=str.encode,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        acks="all",
        retries=3
    )


# ==============================
# 核心回放线程
# ==============================

def _replay_worker(session_id: str, request: ReplayStartRequest):
    """
    从 MinIO 下载视频，逐帧解码，推送至 Kafka tianjing.frame.production（或 sandbox）
    帧消息格式与实时摄像头推送完全一致（平台下游无感知）
    """
    session = _replay_sessions[session_id]
    kafka_topic = "tianjing.frame.sandbox" if request.is_sandbox else "tianjing.frame.production"

    try:
        # 从 MinIO 下载视频到临时文件
        minio = _make_minio_client()
        local_path = f"/tmp/replay_{session_id}.mp4"

        bucket, obj_path = _parse_minio_path(request.video_object_path)
        logger.info("下载录像文件", bucket=bucket, object_path=obj_path, local=local_path)
        minio.fget_object(bucket, obj_path, local_path)

        producer = _make_kafka_producer()
        cap = cv2.VideoCapture(local_path)

        if not cap.isOpened():
            raise RuntimeError(f"无法打开视频文件: {local_path}")

        video_fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
        frame_interval_sec = 1.0 / request.fps
        frame_id_counter = 0

        logger.info("开始回放", session_id=session_id, video_fps=video_fps, replay_fps=request.fps,
                    scene_id=request.scene_id, topic=kafka_topic)

        while session["status"] == "RUNNING":
            ret, frame = cap.read()
            if not ret:
                if request.loop:
                    cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                    continue
                else:
                    break  # 视频结束

            # 将帧编码为 JPEG，Base64 写入 Kafka
            _, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
            frame_b64 = base64.b64encode(buf.tobytes()).decode()

            frame_id = f"replay-{session_id}-{frame_id_counter:06d}"
            timestamp_ms = int(time.time() * 1000)

            message = {
                "frame_id": frame_id,
                "scene_id": request.scene_id,
                "timestamp_ms": timestamp_ms,
                "is_sandbox": request.is_sandbox,
                "image_data": frame_b64,       # Base64 JPEG（生产环境用 MinIO URL 替代）
                "image_width": int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)),
                "image_height": int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)),
                "roi": {"x": 0, "y": 0,
                        "w": int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)),
                        "h": int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))},
                "source": "replay"
            }

            producer.send(kafka_topic, key=request.scene_id, value=message)
            session["frames_sent"] = frame_id_counter + 1
            frame_id_counter += 1

            time.sleep(frame_interval_sec)

        cap.release()
        producer.flush()
        producer.close()

        # 清理临时文件
        try:
            os.remove(local_path)
        except OSError:
            pass

        session["status"] = "COMPLETED"
        session["stopped_at"] = _now_iso()
        logger.info("回放完成", session_id=session_id, frames_sent=session["frames_sent"])

    except Exception as e:
        session["status"] = "FAILED"
        session["error"] = str(e)
        session["stopped_at"] = _now_iso()
        logger.error("回放失败", session_id=session_id, error=str(e))


def _parse_minio_path(object_path: str):
    """解析 MinIO 路径，支持 bucket/prefix/file 格式"""
    parts = object_path.lstrip("/").split("/", 1)
    if len(parts) == 2:
        return parts[0], parts[1]
    return LAB_VIDEO_BUCKET, object_path


def _now_iso() -> str:
    from datetime import datetime, timezone
    return datetime.now(timezone.utc).isoformat()


# ==============================
# REST 接口（内部服务接口，NetworkPolicy 限制外部访问）
# ==============================

@app.post("/internal/replay/start", response_model=ReplaySession)
async def start_replay(request: ReplayStartRequest):
    """
    POST /internal/replay/start — 启动录像回放
    同一场景只允许一个回放任务运行（错误码 2016）
    """
    # 检查同一场景是否已有运行中的任务（规范：错误码 2016）
    for sid, session in _replay_sessions.items():
        if session["scene_id"] == request.scene_id and session["status"] == "RUNNING":
            raise HTTPException(status_code=400, detail={
                "code": 2016,
                "message": f"同一场景已有录像回放任务在运行，请先停止: session_id={sid}"
            })

    session_id = f"RPL-{uuid.uuid4().hex[:8].upper()}"
    _replay_sessions[session_id] = {
        "session_id": session_id,
        "scene_id": request.scene_id,
        "status": "RUNNING",
        "video_object_path": request.video_object_path,
        "fps": request.fps,
        "is_sandbox": request.is_sandbox,
        "frames_sent": 0,
        "started_at": _now_iso(),
        "stopped_at": None,
        "error": None
    }

    thread = threading.Thread(
        target=_replay_worker,
        args=(session_id, request),
        daemon=True,
        name=f"replay-{session_id}"
    )
    _replay_threads[session_id] = thread
    thread.start()

    logger.info("启动录像回放", session_id=session_id, scene_id=request.scene_id,
                video=request.video_object_path, fps=request.fps)
    return ReplaySession(**_replay_sessions[session_id])


@app.post("/internal/replay/stop")
async def stop_replay(body: dict):
    """
    POST /internal/replay/stop — 停止录像回放
    请求体：{"session_id": "RPL-XXXXXXXX"}
    """
    session_id = body.get("session_id")
    if not session_id or session_id not in _replay_sessions:
        raise HTTPException(status_code=400, detail={
            "code": 2015, "message": "录像回放任务不存在或已结束"
        })

    session = _replay_sessions[session_id]
    if session["status"] != "RUNNING":
        raise HTTPException(status_code=400, detail={
            "code": 2015, "message": f"任务状态为 {session['status']}，无法停止"
        })

    session["status"] = "STOPPED"
    session["stopped_at"] = _now_iso()
    logger.info("停止录像回放", session_id=session_id)
    return {"code": 0, "message": "success", "data": session}


@app.get("/internal/replay/status")
async def replay_status(session_id: Optional[str] = None):
    """
    GET /internal/replay/status — 查询回放状态
    session_id 为空时返回全部会话
    """
    if session_id:
        if session_id not in _replay_sessions:
            raise HTTPException(status_code=404, detail={
                "code": 3012, "message": "录像回放会话不存在"
            })
        return {"code": 0, "message": "success", "data": _replay_sessions[session_id]}

    return {"code": 0, "message": "success", "data": list(_replay_sessions.values())}


@app.get("/actuator/health")
async def health():
    return {"status": "UP", "service": "recording-replay-service"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8091)
