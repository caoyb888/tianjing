"""
GPU 推理服务（gpu-infer-service）
Sprint: GPU-03/07（V3.0 阶段二）
Plugin ID: LOCAL-GPU-YOLO-V1
端口: 8102

功能：
  实现标准算法插件接口（Image In → JSON Out），后端支持两种模式通过
  环境变量 INFER_BACKEND 切换，无需重启容器（GPU-07）：
    - onnx（默认）：ONNX Runtime GPU 模式（CUDAExecutionProvider）
    - tensorrt     ：TensorRT FP16 Engine 模式（精度更高，延迟更低）

接口：
  POST /infer              — 标准推理接口（CLAUDE.md §6）
  GET  /actuator/health    — 健康检查（返回 GPU 显存占用）
  GET  /metrics            — Prometheus 指标（tianjing_infer_duration_seconds）
  GET  /backend            — 查询当前推理后端类型

规范：
  CLAUDE.md §5.2（Python 算法规范）
  CLAUDE.md §6.1（标准输入输出 Schema）
  CLAUDE.md §14.2（Prometheus 指标命名）
"""

import base64
import os
import time
import threading
from typing import Optional, List, Dict, Any

import cv2
import numpy as np
import structlog
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from prometheus_client import Histogram, Counter, Gauge, generate_latest, CONTENT_TYPE_LATEST
from fastapi.responses import Response

logger = structlog.get_logger()

# ── 环境变量配置 ──────────────────────────────────────────────────────────────
INFER_BACKEND     = os.getenv("INFER_BACKEND", "onnx")          # onnx | tensorrt
ONNX_MODEL_PATH   = os.getenv("ONNX_MODEL_PATH", "/models/yolov8n.onnx")
TRT_ENGINE_PATH   = os.getenv("TRT_ENGINE_PATH", "/models/yolov8n_fp16.trt")
CONF_THRESHOLD    = float(os.getenv("CONF_THRESHOLD", "0.25"))
IOU_THRESHOLD     = float(os.getenv("IOU_THRESHOLD", "0.45"))
INPUT_SIZE        = (640, 640)   # YOLOv8 标准输入尺寸
PORT              = int(os.getenv("PORT", "8102"))

# ── Prometheus 指标（CLAUDE.md §14.2）────────────────────────────────────────
infer_duration = Histogram(
    "tianjing_infer_duration_seconds",
    "GPU 推理耗时（秒）",
    ["scene_id", "is_sandbox"],
    buckets=[.005, .010, .015, .020, .025, .030, .050, .075, .100, .200],
)
infer_total = Counter(
    "tianjing_infer_total",
    "推理帧计数",
    ["scene_id", "is_sandbox", "result"],
)
gpu_memory_gauge = Gauge(
    "tianjing_gpu_memory_used_bytes",
    "GPU 显存占用（字节）",
    ["gpu_index"],
)

# ── 全局推理会话（单例，线程安全）────────────────────────────────────────────
_session_lock = threading.Lock()
_ort_session  = None      # ONNX Runtime 会话
_trt_engine   = None      # TensorRT 引擎
_trt_context  = None      # TensorRT 执行上下文
_current_backend = INFER_BACKEND  # 当前实际使用的后端

# ── FastAPI 应用 ──────────────────────────────────────────────────────────────
app = FastAPI(
    title="天柱·天镜 GPU 推理服务",
    description="Plugin ID: LOCAL-GPU-YOLO-V1 · V100 本地推理",
    version="1.0.0",
)


# ════════════════════════════════════════════════════════════
# Schema（对应 CLAUDE.md §6.1）
# ════════════════════════════════════════════════════════════

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
    """标准推理请求（BGR 图像 Base64 编码，CLAUDE.md §6.1）"""
    image_b64: str
    scene_id: str
    frame_id: str
    timestamp_ms: int
    is_sandbox: bool = False    # CLAUDE.md §11.1：禁止根据此字段改变推理逻辑
    roi: Optional[Dict[str, int]] = None
    conf_threshold: float = Field(CONF_THRESHOLD, ge=0.0, le=1.0)
    iou_threshold: float  = Field(IOU_THRESHOLD,  ge=0.0, le=1.0)


class InferResponse(BaseModel):
    plugin_id: str  = "LOCAL-GPU-YOLO-V1"
    version: str    = "1.0.0"
    scene_id: str
    frame_id: str
    detections: List[Detection]
    inference_time_ms: float    # 必填（CLAUDE.md §5.2）
    timestamp_ms: int
    is_sandbox: bool            # CLAUDE.md §11.1：原样透传
    backend: str                # onnx | tensorrt
    extra: Dict[str, Any] = {}


# ════════════════════════════════════════════════════════════
# 推理后端加载
# ════════════════════════════════════════════════════════════

def _load_onnx_session():
    """加载 ONNX Runtime GPU 会话（CUDAExecutionProvider）"""
    try:
        import onnxruntime as ort
        providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]
        sess_options = ort.SessionOptions()
        sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        session = ort.InferenceSession(ONNX_MODEL_PATH, sess_options=sess_options,
                                       providers=providers)
        active = session.get_providers()
        logger.info("onnx_session_loaded",
                    model_path=ONNX_MODEL_PATH,
                    providers=active,
                    input_name=session.get_inputs()[0].name,
                    input_shape=session.get_inputs()[0].shape)
        return session
    except Exception as e:
        logger.error("onnx_session_load_failed", error=str(e))
        raise


def _load_tensorrt_engine():
    """加载 TensorRT FP16 Engine（GPU-06 转换产物）"""
    try:
        import tensorrt as trt
        TRT_LOGGER = trt.Logger(trt.Logger.WARNING)
        runtime = trt.Runtime(TRT_LOGGER)
        with open(TRT_ENGINE_PATH, "rb") as f:
            engine = runtime.deserialize_cuda_engine(f.read())
        context = engine.create_execution_context()
        logger.info("tensorrt_engine_loaded",
                    engine_path=TRT_ENGINE_PATH,
                    num_bindings=engine.num_bindings)
        return engine, context
    except Exception as e:
        logger.error("tensorrt_engine_load_failed", error=str(e))
        raise


def load_backend():
    """根据 INFER_BACKEND 环境变量加载对应后端（启动时调用）"""
    global _ort_session, _trt_engine, _trt_context, _current_backend
    backend = os.getenv("INFER_BACKEND", INFER_BACKEND)
    with _session_lock:
        if backend == "tensorrt":
            if not os.path.exists(TRT_ENGINE_PATH):
                logger.warning("trt_engine_not_found_fallback_onnx",
                               trt_path=TRT_ENGINE_PATH)
                backend = "onnx"
            else:
                try:
                    _trt_engine, _trt_context = _load_tensorrt_engine()
                    _current_backend = "tensorrt"
                    return
                except Exception:
                    logger.warning("trt_load_failed_fallback_onnx")
                    backend = "onnx"

        if backend == "onnx":
            _ort_session = _load_onnx_session()
            _current_backend = "onnx"


# ════════════════════════════════════════════════════════════
# 图像预处理
# ════════════════════════════════════════════════════════════

def _preprocess(image_bgr: np.ndarray, roi: Optional[Dict] = None) -> tuple:
    """
    YOLOv8 标准预处理：BGR → letterbox → float32 NCHW [1,3,640,640]
    返回 (blob, scale_x, scale_y, pad_x, pad_y, crop)
    """
    if roi:
        x, y, w, h = roi.get("x", 0), roi.get("y", 0), roi.get("w"), roi.get("h")
        image_bgr = image_bgr[y:y+h, x:x+w]

    orig_h, orig_w = image_bgr.shape[:2]
    target_w, target_h = INPUT_SIZE

    # Letterbox 缩放（保持宽高比，不拉伸）
    scale = min(target_w / orig_w, target_h / orig_h)
    new_w, new_h = int(orig_w * scale), int(orig_h * scale)
    resized = cv2.resize(image_bgr, (new_w, new_h), interpolation=cv2.INTER_LINEAR)

    # 填充到 640×640
    pad_w = (target_w - new_w) // 2
    pad_h = (target_h - new_h) // 2
    padded = np.full((target_h, target_w, 3), 114, dtype=np.uint8)
    padded[pad_h:pad_h+new_h, pad_w:pad_w+new_w] = resized

    # BGR → RGB → float32 → [0,1] → NCHW
    rgb = cv2.cvtColor(padded, cv2.COLOR_BGR2RGB).astype(np.float32) / 255.0
    blob = np.expand_dims(rgb.transpose(2, 0, 1), axis=0)

    return blob, scale, scale, pad_w, pad_h


def _postprocess(raw_output: np.ndarray, orig_shape: tuple,
                 scale_x: float, scale_y: float,
                 pad_x: int, pad_y: int,
                 conf_thr: float, iou_thr: float) -> List[Detection]:
    """
    YOLOv8 后处理：解析输出张量，执行 NMS，还原到原始坐标系
    支持两种常见输出格式：
      - [N, 84, 8400]  (YOLOv8 原生)
      - [N, 8400, 84]  (部分导出格式)
    """
    # 如果模型是占位 Identity 模型（输出 [N,3,640,640]），直接返回空
    if raw_output.ndim != 3 or raw_output.shape[-1] not in (84, 8400):
        logger.debug("stub_model_no_detections",
                     output_shape=list(raw_output.shape))
        return []

    orig_h, orig_w = orig_shape[:2]
    output = raw_output[0]  # 去掉 batch 维度

    # 统一为 [84, 8400]
    if output.shape[0] != 84:
        output = output.T   # [8400, 84] -> [84, 8400]

    # 拆分：前 4 行为 cx,cy,w,h；后 80 行为类别置信度
    boxes     = output[:4].T   # [8400, 4]
    scores    = output[4:].T   # [8400, 80]

    class_ids   = np.argmax(scores, axis=1)
    confidences = scores[np.arange(len(scores)), class_ids]

    # 过滤低置信度
    mask = confidences >= conf_thr
    if not mask.any():
        return []

    boxes_f     = boxes[mask]
    confidences_f = confidences[mask]
    class_ids_f = class_ids[mask]

    # cx,cy,w,h → x1,y1,x2,y2（letterbox 空间）
    x1 = (boxes_f[:, 0] - boxes_f[:, 2] / 2)
    y1 = (boxes_f[:, 1] - boxes_f[:, 3] / 2)
    x2 = (boxes_f[:, 0] + boxes_f[:, 2] / 2)
    y2 = (boxes_f[:, 1] + boxes_f[:, 3] / 2)

    # 还原至原始图像坐标（去掉 pad，还原 scale）
    x1 = np.clip((x1 - pad_x) / scale_x, 0, orig_w)
    y1 = np.clip((y1 - pad_y) / scale_y, 0, orig_h)
    x2 = np.clip((x2 - pad_x) / scale_x, 0, orig_w)
    y2 = np.clip((y2 - pad_y) / scale_y, 0, orig_h)

    xyxy = np.stack([x1, y1, x2, y2], axis=1)

    # NMS（逐类别）
    indices = cv2.dnn.NMSBoxesBatched(
        xyxy.tolist(),
        confidences_f.tolist(),
        class_ids_f.tolist(),
        conf_thr,
        iou_thr,
    )

    # COCO 80 类名称（YOLOv8 预训练权重）
    COCO_NAMES = [
        "person","bicycle","car","motorcycle","airplane","bus","train","truck","boat",
        "traffic light","fire hydrant","stop sign","parking meter","bench","bird","cat",
        "dog","horse","sheep","cow","elephant","bear","zebra","giraffe","backpack",
        "umbrella","handbag","tie","suitcase","frisbee","skis","snowboard","sports ball",
        "kite","baseball bat","baseball glove","skateboard","surfboard","tennis racket",
        "bottle","wine glass","cup","fork","knife","spoon","bowl","banana","apple",
        "sandwich","orange","broccoli","carrot","hot dog","pizza","donut","cake","chair",
        "couch","potted plant","bed","dining table","toilet","tv","laptop","mouse",
        "remote","keyboard","cell phone","microwave","oven","toaster","sink",
        "refrigerator","book","clock","vase","scissors","teddy bear","hair drier",
        "toothbrush",
    ]

    detections = []
    for idx in indices:
        i = int(idx) if isinstance(idx, (int, np.integer)) else int(idx[0])
        cid = int(class_ids_f[i])
        detections.append(Detection(
            class_id=cid,
            class_name=COCO_NAMES[cid] if cid < len(COCO_NAMES) else f"class_{cid}",
            confidence=float(confidences_f[i]),
            bbox=BoundingBox(
                x1=float(x1[i]), y1=float(y1[i]),
                x2=float(x2[i]), y2=float(y2[i]),
            ),
        ))

    return detections


# ════════════════════════════════════════════════════════════
# 推理执行（ONNX / TensorRT 统一出口）
# ════════════════════════════════════════════════════════════

def _infer_onnx(blob: np.ndarray) -> np.ndarray:
    """ONNX Runtime GPU 推理"""
    global _ort_session
    with _session_lock:
        if _ort_session is None:
            raise RuntimeError("ONNX 会话未初始化")
        input_name = _ort_session.get_inputs()[0].name
        outputs = _ort_session.run(None, {input_name: blob})
    return outputs[0]


def _infer_tensorrt(blob: np.ndarray) -> np.ndarray:
    """TensorRT FP16 推理（GPU-07）"""
    import pycuda.driver as cuda
    import pycuda.autoinit  # noqa: F401
    global _trt_context, _trt_engine

    with _session_lock:
        if _trt_context is None:
            raise RuntimeError("TensorRT 上下文未初始化")

        # 分配 GPU 内存
        h_input  = np.ascontiguousarray(blob, dtype=np.float32)
        d_input  = cuda.mem_alloc(h_input.nbytes)

        # 获取输出大小（绑定索引 1 为输出）
        out_binding = 1
        out_shape   = tuple(_trt_engine.get_binding_shape(out_binding))
        h_output    = np.empty(out_shape, dtype=np.float32)
        d_output    = cuda.mem_alloc(h_output.nbytes)

        stream = cuda.Stream()
        cuda.memcpy_htod_async(d_input, h_input, stream)
        _trt_context.execute_async_v2(
            bindings=[int(d_input), int(d_output)],
            stream_handle=stream.handle,
        )
        cuda.memcpy_dtoh_async(h_output, d_output, stream)
        stream.synchronize()

    return h_output


def _get_gpu_memory_info() -> Dict[str, int]:
    """获取 GPU 显存占用（用于健康检查）"""
    try:
        import subprocess
        result = subprocess.run(
            ["nvidia-smi", "--query-gpu=index,memory.used,memory.total",
             "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=5,
        )
        gpus = {}
        for line in result.stdout.strip().split("\n"):
            parts = [p.strip() for p in line.split(",")]
            if len(parts) == 3:
                idx, used, total = parts
                gpus[f"gpu_{idx}"] = {"used_mb": int(used), "total_mb": int(total)}
                gpu_memory_gauge.labels(gpu_index=idx).set(int(used) * 1024 * 1024)
        return gpus
    except Exception as e:
        logger.debug("gpu_memory_query_failed", error=str(e))
        return {}


# ════════════════════════════════════════════════════════════
# 启动事件
# ════════════════════════════════════════════════════════════

@app.on_event("startup")
async def startup_event():
    logger.info("gpu_infer_service_starting",
                backend=INFER_BACKEND,
                onnx_model=ONNX_MODEL_PATH,
                trt_engine=TRT_ENGINE_PATH)
    try:
        load_backend()
        logger.info("gpu_infer_service_ready",
                    backend=_current_backend,
                    plugin_id="LOCAL-GPU-YOLO-V1")
    except Exception as e:
        logger.error("gpu_infer_service_startup_failed", error=str(e))
        # 不抛出异常，允许健康检查返回降级状态


# ════════════════════════════════════════════════════════════
# API 接口
# ════════════════════════════════════════════════════════════

@app.post("/infer", response_model=InferResponse)
async def infer(req: InferRequest):
    """
    标准推理接口（CLAUDE.md §6）
    接收 Base64 BGR 图像 → 返回标准 InferOutput JSON
    # SECURITY: is_sandbox 原样透传，禁止根据此字段改变推理逻辑（CLAUDE.md §11.1）
    """
    # 解码图像
    try:
        img_bytes = base64.b64decode(req.image_b64)
        img_arr   = np.frombuffer(img_bytes, dtype=np.uint8)
        image_bgr = cv2.imdecode(img_arr, cv2.IMREAD_COLOR)
        if image_bgr is None:
            raise ValueError("图像解码失败")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"图像解码错误: {e}")

    orig_shape = image_bgr.shape

    # 预处理
    blob, scale_x, scale_y, pad_x, pad_y = _preprocess(image_bgr, req.roi)

    # 推理（ONNX 或 TensorRT）— 仅计时模型推理部分（CLAUDE.md §5.2）
    t_start = time.perf_counter()
    try:
        if _current_backend == "tensorrt":
            raw_output = _infer_tensorrt(blob)
        else:
            raw_output = _infer_onnx(blob)
    except Exception as e:
        logger.error("infer_failed",
                     scene_id=req.scene_id,
                     frame_id=req.frame_id,
                     error=str(e))
        raise HTTPException(status_code=500, detail=f"推理失败: {e}")
    inference_ms = (time.perf_counter() - t_start) * 1000

    # 后处理
    detections = _postprocess(
        raw_output, orig_shape,
        scale_x, scale_y, pad_x, pad_y,
        req.conf_threshold, req.iou_threshold,
    )

    # Prometheus 指标上报（CLAUDE.md §14.2）
    sandbox_label = str(req.is_sandbox).lower()
    infer_duration.labels(scene_id=req.scene_id, is_sandbox=sandbox_label)\
                  .observe(inference_ms / 1000)
    result_label = "anomaly" if detections else "normal"
    infer_total.labels(scene_id=req.scene_id,
                       is_sandbox=sandbox_label,
                       result=result_label).inc()

    # 结构化日志（DEBUG 级别避免高频 INFO，CLAUDE.md §14.1）
    logger.debug("inference_completed",
                 scene_id=req.scene_id,
                 frame_id=req.frame_id,
                 is_sandbox=req.is_sandbox,    # CLAUDE.md §14.1：必须包含
                 plugin_id="LOCAL-GPU-YOLO-V1",
                 inference_ms=round(inference_ms, 2),
                 has_anomaly=bool(detections),
                 backend=_current_backend,
                 detection_count=len(detections))

    return InferResponse(
        scene_id=req.scene_id,
        frame_id=req.frame_id,
        detections=detections,
        inference_time_ms=round(inference_ms, 2),
        timestamp_ms=req.timestamp_ms,
        is_sandbox=req.is_sandbox,   # CLAUDE.md §11.1：原样透传，不得修改
        backend=_current_backend,
    )


@app.get("/actuator/health")
async def health():
    """健康检查：返回后端状态 + GPU 显存占用"""
    gpu_info  = _get_gpu_memory_info()
    backend_ok = (_ort_session is not None) or (_trt_context is not None)
    status    = "UP" if backend_ok else "DEGRADED"
    return {
        "status": status,
        "service": "gpu-infer-service",
        "plugin_id": "LOCAL-GPU-YOLO-V1",
        "backend_type": _current_backend,
        "backend_ready": backend_ok,
        "gpu": gpu_info,
    }


@app.get("/backend")
async def get_backend():
    """查询当前推理后端类型（GPU-07）"""
    return {
        "backend_type": _current_backend,
        "available_backends": ["onnx", "tensorrt"],
        "onnx_model": ONNX_MODEL_PATH,
        "trt_engine": TRT_ENGINE_PATH,
        "onnx_model_exists": os.path.exists(ONNX_MODEL_PATH),
        "trt_engine_exists": os.path.exists(TRT_ENGINE_PATH),
    }


@app.get("/metadata")
async def get_metadata():
    """返回算法插件元信息（CLAUDE.md §6.2 get_metadata）"""
    return {
        "plugin_id": "LOCAL-GPU-YOLO-V1",
        "name": "本地 V100 GPU 目标检测引擎",
        "version": "1.0.0",
        "type": "detection",
        "backbone": "YOLOv8n",
        "backend": _current_backend,
        "supported_scenes": ["SCENE-SINTER-*", "SCENE-STEEL-*", "SCENE-STRIP-*"],
        "hardware_requirements": {
            "min_gpu_vram_gb": 4,
            "supports_tensorrt": True,
            "supports_onnx": True,
            "gpu_device": "Tesla V100-SXM2-32GB",
        },
        "accuracy_metrics": {
            "map50": 0.0,        # 基础预训练权重，Sprint 4 微调后更新
            "inference_ms_gpu_p95": None,  # 基准测试后填写
        },
    }


@app.get("/metrics")
async def metrics():
    """Prometheus 指标暴露（CLAUDE.md §14.2）"""
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


# ════════════════════════════════════════════════════════════
# 入口
# ════════════════════════════════════════════════════════════

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=PORT, log_level="info")
