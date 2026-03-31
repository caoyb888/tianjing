"""
云端推理代理服务（Cloud Inference Proxy）
Sprint: S1-04（V2.0 使能组件②）
Plugin ID: CLOUD-PROXY-V1

规范：CLAUDE.md §6（算法插件接口规范），API 接口规范 V3.1

功能：
  对平台上层完全透明，实现标准插件协议（Image In → JSON Out）。
  后端支持两种推理模式（通过环境变量 INFER_BACKEND 切换）：
    - onnx_cpu：使用 YOLOv8n ONNX 模型在 CPU 上推理（默认，无需 API Key）
    - cloud_api：对接公共云视觉 API（需配置 API Key）

GPU 到货后（Sprint 5），只需将场景绑定的插件切换至本地 GPU 推理容器，无需修改平台任何代码。
"""

import base64
import os
import time
from typing import Optional, List, Dict, Any

import cv2
import numpy as np
import structlog
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

logger = structlog.get_logger()

app = FastAPI(title="云端推理代理", description="CLOUD-PROXY-V1", version="1.0.0")

INFER_BACKEND = os.getenv("INFER_BACKEND", "onnx_cpu")
ONNX_MODEL_PATH = os.getenv("ONNX_MODEL_PATH", "/models/yolov8n.onnx")
CLOUD_API_ENDPOINT = os.getenv("CLOUD_API_ENDPOINT", "")
CLOUD_API_KEY = os.getenv("CLOUD_API_KEY", "")
CONF_THRESHOLD = float(os.getenv("CONF_THRESHOLD", "0.25"))

# ==============================
# Schema（对应 CLAUDE.md §6.1）
# ==============================

class BoundingBox(BaseModel):
    x1: float = Field(..., ge=0)
    y1: float = Field(..., ge=0)
    x2: float = Field(..., gt=0)
    y2: float = Field(..., gt=0)


class Detection(BaseModel):
    class_id: int
    class_name: str
    confidence: float = Field(..., ge=0.0, le=1.0)
    bbox: BoundingBox
    measurement: Optional[Dict[str, Any]] = None


class InferRequest(BaseModel):
    """标准推理请求（BGR 图像 Base64 编码）"""
    image_b64: str           # Base64 编码的 BGR JPEG/PNG 图像
    scene_id: str
    frame_id: str
    timestamp_ms: int
    is_sandbox: bool = False  # 算法内部禁止根据此字段改变推理逻辑（规范：CLAUDE.md §6.2）
    roi: Optional[Dict[str, int]] = None
    conf_threshold: float = Field(CONF_THRESHOLD, ge=0.0, le=1.0)


class InferResponse(BaseModel):
    plugin_id: str = "CLOUD-PROXY-V1"
    version: str = "1.0.0"
    scene_id: str
    frame_id: str
    detections: List[Detection]
    inference_time_ms: float  # 必填（规范：CLAUDE.md §5.2）
    timestamp_ms: int
    backend: str
    extra: Dict[str, Any] = {}


# ==============================
# ONNX CPU 推理后端（默认）
# ==============================

_onnx_session = None


def _load_onnx_session():
    """懒加载 ONNX 模型（ModelLoader 模式，规范：CLAUDE.md §5.2）"""
    global _onnx_session
    if _onnx_session is not None:
        return _onnx_session

    if not os.path.exists(ONNX_MODEL_PATH):
        logger.warning("ONNX 模型文件不存在，将返回空检测结果", path=ONNX_MODEL_PATH)
        return None

    import onnxruntime as ort
    _onnx_session = ort.InferenceSession(
        ONNX_MODEL_PATH,
        providers=["CPUExecutionProvider"]
    )
    logger.info("ONNX 模型加载完成", path=ONNX_MODEL_PATH)
    return _onnx_session


def _infer_onnx_cpu(image_bgr: np.ndarray, conf_threshold: float) -> List[Detection]:
    """YOLOv8n ONNX CPU 推理（生产环境替换为 GPU TensorRT）"""
    session = _load_onnx_session()
    if session is None:
        # 模型未就绪时返回空检测（不影响流水线运行）
        return []

    # YOLOv8 预处理
    h, w = image_bgr.shape[:2]
    input_size = 640
    img = cv2.resize(image_bgr, (input_size, input_size))
    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    img = np.transpose(img, (2, 0, 1))[np.newaxis, :]  # NCHW

    input_name = session.get_inputs()[0].name
    outputs = session.run(None, {input_name: img})

    # YOLOv8 输出解析（shape: [1, 84, 8400]）
    detections = []
    pred = outputs[0][0].T  # (8400, 84)

    for row in pred:
        scores = row[4:]
        class_id = int(np.argmax(scores))
        confidence = float(scores[class_id])

        if confidence < conf_threshold:
            continue

        cx, cy, bw, bh = row[:4]
        x1 = max(0.0, (cx - bw / 2) / input_size * w)
        y1 = max(0.0, (cy - bh / 2) / input_size * h)
        x2 = min(float(w), (cx + bw / 2) / input_size * w)
        y2 = min(float(h), (cy + bh / 2) / input_size * h)

        if x2 <= x1 or y2 <= y1:
            continue

        detections.append(Detection(
            class_id=class_id,
            class_name=f"class_{class_id}",  # 实际场景替换为类别名称映射
            confidence=round(confidence, 4),
            bbox=BoundingBox(x1=x1, y1=y1, x2=x2, y2=y2)
        ))

    return detections


async def _infer_cloud_api(image_bgr: np.ndarray, conf_threshold: float) -> List[Detection]:
    """云端 API 推理（阿里云视觉智能 / Baidu EasyDL）"""
    if not CLOUD_API_ENDPOINT or not CLOUD_API_KEY:
        logger.warning("云端 API 未配置，降级至空检测结果")
        return []

    import httpx
    _, buf = cv2.imencode(".jpg", image_bgr)
    img_b64 = base64.b64encode(buf.tobytes()).decode()

    async with httpx.AsyncClient(timeout=30.0) as client:
        resp = await client.post(
            CLOUD_API_ENDPOINT,
            headers={"Authorization": f"Bearer {CLOUD_API_KEY}"},
            json={"image": img_b64, "threshold": conf_threshold}
        )
        resp.raise_for_status()
        data = resp.json()

    # 解析通用云 API 响应（各平台响应结构不同，此处为简化示例）
    detections = []
    for item in data.get("detections", []):
        detections.append(Detection(
            class_id=item.get("class_id", 0),
            class_name=item.get("class_name", "unknown"),
            confidence=float(item.get("confidence", 0)),
            bbox=BoundingBox(
                x1=item["bbox"]["x1"], y1=item["bbox"]["y1"],
                x2=item["bbox"]["x2"], y2=item["bbox"]["y2"]
            )
        ))
    return detections


# ==============================
# REST 接口
# ==============================

@app.post("/infer", response_model=InferResponse)
async def infer(request: InferRequest):
    """
    POST /infer — 标准推理接口（Image In → JSON Out）
    接收 Base64 BGR 图像，返回检测结果 JSON。
    规范：CLAUDE.md §6（算法插件接口规范）

    注意：is_sandbox 字段必须原样透传至 InferResponse，
    算法逻辑禁止根据 is_sandbox 改变推理行为（规范：CLAUDE.md §6.2）
    """
    start = time.perf_counter()

    try:
        # 解码图像
        img_bytes = base64.b64decode(request.image_b64)
        nparr = np.frombuffer(img_bytes, np.uint8)
        image_bgr = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if image_bgr is None:
            raise HTTPException(status_code=400, detail={"code": 1001, "message": "图像解码失败"})

        # ROI 裁剪
        if request.roi:
            roi = request.roi
            image_bgr = image_bgr[roi["y"]:roi["y"]+roi["h"], roi["x"]:roi["x"]+roi["w"]]

        # 执行推理（后端透明切换）
        if INFER_BACKEND == "cloud_api":
            detections = await _infer_cloud_api(image_bgr, request.conf_threshold)
        else:
            detections = _infer_onnx_cpu(image_bgr, request.conf_threshold)

    except HTTPException:
        raise
    except Exception as e:
        logger.error("推理失败", error=str(e), scene_id=request.scene_id, frame_id=request.frame_id)
        raise HTTPException(status_code=500, detail={"code": 5006, "message": f"推理失败: {e}"})

    inference_ms = (time.perf_counter() - start) * 1000

    logger.debug("推理完成",
                 scene_id=request.scene_id,
                 frame_id=request.frame_id,
                 is_sandbox=request.is_sandbox,
                 detections=len(detections),
                 inference_ms=round(inference_ms, 2),
                 backend=INFER_BACKEND)

    return InferResponse(
        scene_id=request.scene_id,
        frame_id=request.frame_id,
        detections=detections,
        inference_time_ms=round(inference_ms, 2),
        timestamp_ms=int(time.time() * 1000),
        backend=INFER_BACKEND,
        extra={"is_sandbox": request.is_sandbox}  # 原样透传，不改变推理逻辑
    )


@app.get("/health")
async def health():
    """
    GET /health — 健康检查
    返回当前推理后端类型和响应延迟基准
    """
    return {
        "status": "UP",
        "plugin_id": "CLOUD-PROXY-V1",
        "backend": INFER_BACKEND,
        "onnx_model_loaded": _onnx_session is not None,
        "onnx_model_path": ONNX_MODEL_PATH if INFER_BACKEND == "onnx_cpu" else None,
        "cloud_api_configured": bool(CLOUD_API_ENDPOINT) if INFER_BACKEND == "cloud_api" else None
    }


@app.get("/actuator/health")
async def actuator_health():
    return {"status": "UP"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8092)
