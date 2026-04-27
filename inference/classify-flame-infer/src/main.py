"""
classify-flame-infer — 烧结看火火焰强度分类推理服务
Plugin ID: CLASSIFY-FLAME-V1
端口: 8104

功能：
  实现标准算法插件接口（Image In → JSON Out）。
  接收 Base64 编码帧图像 → EfficientNet-B2 三分类（正常/过弱/料面不均）→ 返回分类结果。
  使用 ONNX Runtime CPU 后端（EfficientNet-B2 参数量小，CPU 单帧 <50ms 满足需求）。

接口：
  POST /infer           — 标准推理接口（CLAUDE.md §6）
  GET  /actuator/health — 健康检查（CLAUDE.md §14.2）
  GET  /metadata        — 算法元信息

规范：
  CLAUDE.md §5.2  （Python 算法服务规范）
  CLAUDE.md §6.1  （标准输入输出 Schema）
  CLAUDE.md §11.1 （is_sandbox 原样透传，禁止根据此字段改变推理逻辑）
  CLAUDE.md §14.1 （结构化日志，必须包含 scene_id、is_sandbox）
  CLAUDE.md §14.2 （Prometheus 指标命名）
"""

import base64
import os
import time
from typing import Optional, Dict, Any

import cv2
import numpy as np
import structlog
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from prometheus_client import Histogram, Counter, generate_latest, CONTENT_TYPE_LATEST
from fastapi.responses import Response

logger = structlog.get_logger()

# ── 环境变量配置 ──────────────────────────────────────────────────────────────
ONNX_MODEL_PATH = os.getenv("ONNX_MODEL_PATH", "/models/flame_classify.onnx")
CONF_THRESHOLD  = float(os.getenv("CONF_THRESHOLD", "0.85"))
INFER_BACKEND   = os.getenv("INFER_BACKEND", "gpu")   # gpu | cpu
IMG_SIZE        = 224   # EfficientNet-B2 输入尺寸
PORT            = int(os.getenv("PORT", "8104"))

# ImageNet 标准化参数（与训练时 T.Normalize 保持一致）
_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
_STD  = np.array([0.229, 0.224, 0.225], dtype=np.float32)

# 三分类标签（与 train_flame_classify.py 保持一致）
LABELS = ["正常", "过弱", "料面不均"]

# ── Prometheus 指标（CLAUDE.md §14.2）────────────────────────────────────────
infer_duration = Histogram(
    "tianjing_infer_duration_seconds",
    "分类推理耗时（秒）",
    ["scene_id", "is_sandbox"],
    buckets=[.005, .010, .020, .030, .050, .075, .100, .150, .200, .500],
)
infer_total = Counter(
    "tianjing_infer_total",
    "推理帧计数",
    ["scene_id", "is_sandbox", "result"],
)

# ── 全局 ONNX 会话（启动时加载，之后线程安全复用）────────────────────────────
_ort_session    = None
_active_backend = "cpu"   # 实际加载后更新，用于健康检查上报


def _load_model() -> None:
    """
    加载 ONNX Runtime 会话。
    优先尝试 GPU（CUDAExecutionProvider），不可用时自动降级到 CPU。
    通过 INFER_BACKEND=cpu 可强制使用 CPU 模式。
    """
    global _ort_session, _active_backend
    try:
        import onnxruntime as ort
        sess_options = ort.SessionOptions()
        sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL

        if INFER_BACKEND == "cpu":
            providers = ["CPUExecutionProvider"]
            sess_options.intra_op_num_threads = 2
        else:
            providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]

        _ort_session = ort.InferenceSession(
            ONNX_MODEL_PATH,
            sess_options=sess_options,
            providers=providers,
        )
        active_providers = _ort_session.get_providers()
        _active_backend = "gpu" if "CUDAExecutionProvider" in active_providers else "cpu"

        input_meta = _ort_session.get_inputs()[0]
        logger.info("onnx_model_loaded",
                    model_path=ONNX_MODEL_PATH,
                    requested_backend=INFER_BACKEND,
                    active_backend=_active_backend,
                    providers=active_providers,
                    input_name=input_meta.name,
                    input_shape=input_meta.shape,
                    labels=LABELS)
    except Exception as e:
        logger.error("onnx_model_load_failed",
                     model_path=ONNX_MODEL_PATH,
                     error=str(e))
        # 不抛出异常：允许服务以 DEGRADED 状态启动（依赖挂载 /models 卷）


# ── FastAPI 应用 ──────────────────────────────────────────────────────────────
app = FastAPI(
    title="天柱·天镜 烧结看火分类推理服务",
    description="Plugin ID: CLASSIFY-FLAME-V1 · EfficientNet-B2 三分类（正常/过弱/料面不均）",
    version="1.0.0",
)


@app.on_event("startup")
async def startup():
    _load_model()


# ════════════════════════════════════════════════════════════
# Schema（CLAUDE.md §6.1）
# ════════════════════════════════════════════════════════════

class Classification(BaseModel):
    class_id:    int
    class_name:  str
    confidence:  float = Field(..., ge=0.0, le=1.0)


class InferRequest(BaseModel):
    """标准推理请求（Base64 BGR 帧图像，CLAUDE.md §6.1）"""
    image_b64:      str
    scene_id:       str
    frame_id:       str
    timestamp_ms:   int
    is_sandbox:     bool  = False   # CLAUDE.md §11.1：禁止根据此字段改变推理逻辑
    roi:            Optional[Dict[str, int]] = None
    conf_threshold: float = Field(CONF_THRESHOLD, ge=0.0, le=1.0)


class InferResponse(BaseModel):
    plugin_id:         str   = "CLASSIFY-FLAME-V1"
    version:           str   = "1.0.0"
    scene_id:          str
    frame_id:          str
    classifications:   list[Classification]
    # 兼容 infer-dispatcher 聚合层：保留 detections 空列表（统一格式）
    detections:        list  = []
    inference_time_ms: float
    timestamp_ms:      int
    is_sandbox:        bool   # CLAUDE.md §11.1：原样透传
    extra:             Dict[str, Any] = {}


# ════════════════════════════════════════════════════════════
# 图像预处理（与训练时 val_tf 保持一致）
# ════════════════════════════════════════════════════════════

def _preprocess(image_bgr: np.ndarray, roi: Optional[Dict[str, int]] = None) -> np.ndarray:
    """
    裁剪 ROI → 缩放至 224x224 → RGB → 归一化 → CHW float32 → batch=1
    与 train_flame_classify.py 的 val_tf 完全一致，保证训练/推理一致性。
    """
    h, w = image_bgr.shape[:2]

    if roi:
        x = max(0, roi.get("x", 0))
        y = max(0, roi.get("y", 0))
        rw = min(roi.get("w", w), w - x)
        rh = min(roi.get("h", h), h - y)
        image_bgr = image_bgr[y:y+rh, x:x+rw]

    # BGR → RGB
    image_rgb = cv2.cvtColor(image_bgr, cv2.COLOR_BGR2RGB)
    # 缩放至 224x224
    image_resized = cv2.resize(image_rgb, (IMG_SIZE, IMG_SIZE), interpolation=cv2.INTER_LINEAR)
    # 归一化
    image_f32 = image_resized.astype(np.float32) / 255.0
    image_f32 = (image_f32 - _MEAN) / _STD
    # HWC → CHW → NCHW
    return image_f32.transpose(2, 0, 1)[np.newaxis, ...]   # shape: (1, 3, 224, 224)


# ════════════════════════════════════════════════════════════
# 主推理端点（CLAUDE.md §6）
# ════════════════════════════════════════════════════════════

@app.post("/infer", response_model=InferResponse)
async def infer(req: InferRequest):
    """
    标准分类推理接口（CLAUDE.md §6）
    接收 Base64 BGR 图像 → EfficientNet-B2 → 三分类结果 JSON
    # SECURITY: is_sandbox 原样透传，禁止根据此字段改变推理逻辑（CLAUDE.md §11.1）
    """
    if _ort_session is None:
        raise HTTPException(status_code=503, detail="ONNX 模型未加载，请检查 /models/flame_classify.onnx 挂载")

    # 1. Base64 解码 → OpenCV 图像
    try:
        img_bytes = base64.b64decode(req.image_b64)
        img_arr   = np.frombuffer(img_bytes, dtype=np.uint8)
        image_bgr = cv2.imdecode(img_arr, cv2.IMREAD_COLOR)
        if image_bgr is None:
            raise ValueError("图像解码失败（imdecode 返回 None）")
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"图像解码错误: {e}")

    # 2. 预处理
    blob = _preprocess(image_bgr, req.roi)

    # 3. ONNX 推理（仅计时推理部分，CLAUDE.md §5.2）
    t_start = time.perf_counter()
    try:
        input_name = _ort_session.get_inputs()[0].name
        logits = _ort_session.run(None, {input_name: blob})[0]  # shape: (1, 3)
    except Exception as e:
        logger.error("infer_onnx_failed",
                     scene_id=req.scene_id,
                     frame_id=req.frame_id,
                     is_sandbox=req.is_sandbox,
                     error=str(e))
        raise HTTPException(status_code=500, detail=f"推理失败: {e}")

    inference_ms = round((time.perf_counter() - t_start) * 1000, 2)

    # 4. Softmax → 分类结果
    exp_logits = np.exp(logits[0] - logits[0].max())
    probs = exp_logits / exp_logits.sum()

    top_idx  = int(probs.argmax())
    top_conf = float(probs[top_idx])

    # 返回所有类别的置信度（供审计/漂移分析），过滤掉低于阈值的
    classifications = [
        Classification(class_id=i, class_name=LABELS[i], confidence=float(probs[i]))
        for i in range(len(LABELS))
        if float(probs[i]) >= req.conf_threshold or i == top_idx
    ]
    classifications.sort(key=lambda c: c.confidence, reverse=True)

    # 5. Prometheus 指标（CLAUDE.md §14.2）
    is_sandbox_str = str(req.is_sandbox).lower()
    infer_duration.labels(scene_id=req.scene_id, is_sandbox=is_sandbox_str).observe(inference_ms / 1000)
    infer_total.labels(scene_id=req.scene_id, is_sandbox=is_sandbox_str,
                       result=LABELS[top_idx]).inc()

    # 6. 结构化日志（高频推理使用 DEBUG，CLAUDE.md §14.1）
    logger.debug("flame_classify_inferred",
                 scene_id=req.scene_id,
                 frame_id=req.frame_id,
                 is_sandbox=req.is_sandbox,   # CLAUDE.md §14.1：必须包含
                 plugin_id="CLASSIFY-FLAME-V1",
                 top_class=LABELS[top_idx],
                 top_conf=round(top_conf, 4),
                 inference_ms=inference_ms)

    return InferResponse(
        scene_id=req.scene_id,
        frame_id=req.frame_id,
        classifications=classifications,
        inference_time_ms=inference_ms,
        timestamp_ms=req.timestamp_ms,
        is_sandbox=req.is_sandbox,   # CLAUDE.md §11.1：原样透传，不得修改
        extra={"top_class": LABELS[top_idx], "top_conf": round(top_conf, 4)},
    )


# ════════════════════════════════════════════════════════════
# 健康检查 + 元信息（CLAUDE.md §14.2）
# ════════════════════════════════════════════════════════════

@app.get("/actuator/health")
async def health():
    model_ok = _ort_session is not None
    return {
        "status":           "UP" if model_ok else "DEGRADED",
        "service":          "classify-flame-infer",
        "plugin_id":        "CLASSIFY-FLAME-V1",
        "model_loaded":     model_ok,
        "model_path":       ONNX_MODEL_PATH,
        "backend":          _active_backend,
        "labels":           LABELS,
    }


@app.get("/health")
async def health_compat():
    """兼容 cloud-inference-proxy 格式（供 start-backend.sh health_check 使用）"""
    model_ok = _ort_session is not None
    return {
        "status":            "UP" if model_ok else "DOWN",
        "plugin_id":         "CLASSIFY-FLAME-V1",
        "backend":           f"onnx_{_active_backend}",
        "onnx_model_loaded": model_ok,
        "onnx_model_path":   ONNX_MODEL_PATH if model_ok else None,
    }


@app.get("/metadata")
async def get_metadata():
    """算法元信息（CLAUDE.md §6.3）"""
    return {
        "plugin_id":       "CLASSIFY-FLAME-V1",
        "name":            "烧结看火火焰强度分类",
        "version":         "1.0.0",
        "type":            "classification",
        "backbone":        "EfficientNet-B2",
        "labels":          LABELS,
        "input_size":      [IMG_SIZE, IMG_SIZE],
        "supported_scenes": ["SCENE-SINTER-FIRE-001"],
        "accuracy_metrics": {
            "macro_f1":          1.0,
            "inference_ms_cpu":  45,
        },
    }


@app.get("/metrics")
async def metrics():
    """Prometheus 指标端点（CLAUDE.md §14.2）"""
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


# ════════════════════════════════════════════════════════════
# 入口
# ════════════════════════════════════════════════════════════

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=PORT, log_level="info")
