#!/usr/bin/env python3
"""
天柱·天镜 — YOLOv8n CPU 模式训练脚本
用途：Sprint 3 测试环境单场景端到端验证，替代 K8s Job

环境变量（全部由 DockerTrainJobLauncher 注入）：
  JOB_ID                训练作业 ID，如 TJ-PLUGIN-001-1743500000000
  PLUGIN_ID             算法插件 ID，如 ATOM-DETECT-YOLO-V1
  DATASET_VERSION_ID    数据集版本 ID，MinIO 路径前缀
  TRAIN_CONFIG_JSON     训练超参 JSON，如 {"epochs":10,"img_size":640,"batch_size":8}
  MINIO_ENDPOINT        MinIO 地址，如 localhost:9000
  MINIO_ACCESS_KEY      MinIO 访问密钥
  MINIO_SECRET_KEY      MinIO 私钥
  MLFLOW_TRACKING_URI   MLflow 服务地址，如 http://localhost:5000
  PLATFORM_CALLBACK_URL drift-monitor-service 回调基址，如 http://host.docker.internal:8089
  MODEL_REGISTER_URL    alarm-rule-service 基址，如 http://host.docker.internal:8086

MinIO 数据集约定：
  Bucket: tianjing-datasets
  路径:   {DATASET_VERSION_ID}/dataset.zip
  Zip 内容:
    images/           — 训练图像 (*.jpg / *.png)
    annotations.json  — COCO 格式标注文件

MinIO 模型输出约定：
  Bucket: tianjing-models-staging
  路径:   {PLUGIN_ID}/{JOB_ID}/best.onnx
"""

import json
import logging
import os
import shutil
import sys
import time
import zipfile
from pathlib import Path

import requests
import yaml
from minio import Minio
from minio.error import S3Error
from ultralytics import YOLO
from ultralytics.utils.callbacks.base import default_callbacks

# ──────────────────────────────────────────
# 日志配置
# ──────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger("train_yolo_cpu")


# ──────────────────────────────────────────
# 读取环境变量
# ──────────────────────────────────────────
def _require_env(key: str) -> str:
    val = os.environ.get(key, "").strip()
    if not val:
        raise EnvironmentError(f"必需环境变量未设置: {key}")
    return val


JOB_ID               = _require_env("JOB_ID")
PLUGIN_ID            = _require_env("PLUGIN_ID")
DATASET_VERSION_ID   = _require_env("DATASET_VERSION_ID")
PLATFORM_CALLBACK_URL = os.environ.get("PLATFORM_CALLBACK_URL", "http://host.docker.internal:8089")
MODEL_REGISTER_URL   = os.environ.get("MODEL_REGISTER_URL", "http://host.docker.internal:8086")
MINIO_ENDPOINT       = os.environ.get("MINIO_ENDPOINT", "localhost:9000")
MINIO_ACCESS_KEY     = os.environ.get("MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY     = os.environ.get("MINIO_SECRET_KEY", "minioadmin")
MLFLOW_TRACKING_URI  = os.environ.get("MLFLOW_TRACKING_URI", "http://localhost:5000")

# 解析训练超参
_cfg_raw = os.environ.get("TRAIN_CONFIG_JSON", "{}")
try:
    TRAIN_CONFIG: dict = json.loads(_cfg_raw)
except json.JSONDecodeError:
    log.warning("TRAIN_CONFIG_JSON 解析失败，使用默认值: %s", _cfg_raw)
    TRAIN_CONFIG = {}

EPOCHS     = int(TRAIN_CONFIG.get("epochs",     10))
IMG_SIZE   = int(TRAIN_CONFIG.get("img_size",  640))
BATCH_SIZE = int(TRAIN_CONFIG.get("batch_size",  8))

# 工作目录
WORK_DIR   = Path("/tmp/train") / JOB_ID
DATASET_DIR = WORK_DIR / "dataset"
YOLO_DIR   = WORK_DIR / "yolo_data"
OUTPUT_DIR = WORK_DIR / "output"

DATASET_BUCKET        = "tianjing-datasets"
MODEL_STAGING_BUCKET  = "tianjing-models-staging"

log.info("训练作业启动 job_id=%s plugin_id=%s dataset_version_id=%s epochs=%d",
         JOB_ID, PLUGIN_ID, DATASET_VERSION_ID, EPOCHS)


# ──────────────────────────────────────────
# 工具函数：回调接口
# ──────────────────────────────────────────
def _callback(status: str, **kwargs):
    """向 drift-monitor-service 汇报训练状态。失败不抛异常，仅记录日志。"""
    url = f"{PLATFORM_CALLBACK_URL}/internal/training/jobs/{JOB_ID}/callback"
    payload = {"status": status, **kwargs}
    try:
        resp = requests.post(url, json=payload, timeout=10)
        resp.raise_for_status()
        log.info("回调成功 status=%s", status)
    except Exception as exc:
        log.warning("回调失败（忽略）status=%s error=%s", status, exc)


def _register_model(mlflow_run_id: str, model_artifact_url: str, version_tag: str):
    """向 alarm-rule-service 内部接口注册模型版本（无 JWT）。"""
    url = f"{MODEL_REGISTER_URL}/internal/models/register"
    payload = {
        "plugin_id":          PLUGIN_ID,
        "version":            version_tag,
        "mlflow_run_id":      mlflow_run_id,
        "model_artifact_url": model_artifact_url,
        "training_job_id":    JOB_ID,
    }
    try:
        resp = requests.post(url, json=payload, timeout=15)
        resp.raise_for_status()
        version_id = resp.json().get("data", {}).get("versionId", "unknown")
        log.info("模型版本注册成功 version_id=%s", version_id)
        return version_id
    except Exception as exc:
        log.warning("模型版本注册失败（不影响训练结果）error=%s", exc)
        return None


# ──────────────────────────────────────────
# 步骤 1：从 MinIO 下载数据集
# ──────────────────────────────────────────
def download_dataset():
    log.info("正在从 MinIO 下载数据集 %s/%s/dataset.zip", DATASET_BUCKET, DATASET_VERSION_ID)
    minio_secure = not MINIO_ENDPOINT.startswith("localhost") and not MINIO_ENDPOINT.startswith("127.")
    client = Minio(MINIO_ENDPOINT, access_key=MINIO_ACCESS_KEY,
                   secret_key=MINIO_SECRET_KEY, secure=minio_secure)

    DATASET_DIR.mkdir(parents=True, exist_ok=True)
    zip_path = DATASET_DIR / "dataset.zip"

    try:
        client.fget_object(DATASET_BUCKET, f"{DATASET_VERSION_ID}/dataset.zip", str(zip_path))
    except S3Error as e:
        raise RuntimeError(f"MinIO 下载失败: {e}") from e

    log.info("数据集下载完成，开始解压")
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(DATASET_DIR)
    zip_path.unlink()
    log.info("解压完成，数据集目录: %s", DATASET_DIR)


# ──────────────────────────────────────────
# 步骤 2：COCO 格式 → YOLO 格式转换
# ──────────────────────────────────────────
def convert_coco_to_yolo():
    """
    将 Label Studio COCO 导出格式转换为 YOLO txt 标注格式。

    输入 (DATASET_DIR 内):
      images/              — 图像文件
      annotations.json     — COCO instances 格式

    输出 (YOLO_DIR 内):
      images/train/        — 训练图像（按 8:2 分割）
      images/val/
      labels/train/        — YOLO txt 标注
      labels/val/
      data.yaml            — YOLOv8 数据集配置
    """
    import json as _json
    import random

    log.info("开始 COCO→YOLO 格式转换")

    anno_path = DATASET_DIR / "annotations.json"
    if not anno_path.exists():
        raise FileNotFoundError(f"标注文件不存在: {anno_path}")

    with open(anno_path, encoding="utf-8") as f:
        coco = _json.load(f)

    categories  = {c["id"]: c["name"] for c in coco.get("categories", [])}
    cat_ids     = sorted(categories.keys())
    cat_id_map  = {cid: idx for idx, cid in enumerate(cat_ids)}
    class_names = [categories[cid] for cid in cat_ids]

    # 按图像 ID 分组标注
    annotations_by_image: dict[int, list] = {}
    for ann in coco.get("annotations", []):
        annotations_by_image.setdefault(ann["image_id"], []).append(ann)

    images = coco.get("images", [])
    random.seed(42)
    random.shuffle(images)
    split = max(1, int(len(images) * 0.8))
    splits = {"train": images[:split], "val": images[split:]}
    if not splits["val"]:
        splits["val"] = images[:max(1, split // 5)]

    src_images_dir = DATASET_DIR / "images"

    for split_name, split_images in splits.items():
        (YOLO_DIR / "images" / split_name).mkdir(parents=True, exist_ok=True)
        (YOLO_DIR / "labels" / split_name).mkdir(parents=True, exist_ok=True)

        for img_info in split_images:
            img_w = img_info["width"]
            img_h = img_info["height"]
            fname = Path(img_info["file_name"]).name

            # 复制图像
            src = src_images_dir / fname
            dst = YOLO_DIR / "images" / split_name / fname
            if src.exists():
                shutil.copy2(src, dst)
            else:
                log.warning("图像文件不存在，跳过: %s", src)
                continue

            # 生成 YOLO txt 标注
            label_lines = []
            for ann in annotations_by_image.get(img_info["id"], []):
                cls_idx = cat_id_map[ann["category_id"]]
                x, y, w, h = ann["bbox"]  # COCO: left-top-width-height
                cx = (x + w / 2) / img_w
                cy = (y + h / 2) / img_h
                nw = w / img_w
                nh = h / img_h
                # 边界裁剪
                cx = max(0.0, min(1.0, cx))
                cy = max(0.0, min(1.0, cy))
                nw = max(0.0, min(1.0, nw))
                nh = max(0.0, min(1.0, nh))
                label_lines.append(f"{cls_idx} {cx:.6f} {cy:.6f} {nw:.6f} {nh:.6f}")

            label_path = YOLO_DIR / "labels" / split_name / (Path(fname).stem + ".txt")
            label_path.write_text("\n".join(label_lines), encoding="utf-8")

    # 写入 data.yaml
    data_yaml = {
        "path":  str(YOLO_DIR),
        "train": "images/train",
        "val":   "images/val",
        "nc":    len(class_names),
        "names": class_names,
    }
    data_yaml_path = YOLO_DIR / "data.yaml"
    with open(data_yaml_path, "w", encoding="utf-8") as f:
        yaml.dump(data_yaml, f, allow_unicode=True)

    log.info("格式转换完成：%d 类别，训练集 %d 张，验证集 %d 张",
             len(class_names), len(splits["train"]), len(splits["val"]))
    return data_yaml_path, class_names


# ──────────────────────────────────────────
# 步骤 3：YOLOv8n 训练（含 per-epoch 回调）
# ──────────────────────────────────────────
class EpochCallbackHandler:
    """每个 epoch 结束时向平台汇报训练进度。"""

    def __init__(self):
        self.best_map50 = 0.0
        self.best_epoch = 0

    def on_fit_epoch_end(self, trainer):
        metrics = trainer.metrics
        current_map50 = float(metrics.get("metrics/mAP50(B)", 0.0))
        epoch = trainer.epoch + 1  # ultralytics epoch 从 0 开始

        if current_map50 > self.best_map50:
            self.best_map50 = current_map50
            self.best_epoch = epoch

        log.info("Epoch %d/%d 完成 mAP50=%.4f best_mAP50=%.4f",
                 epoch, trainer.args.epochs, current_map50, self.best_map50)

        _callback(
            status="RUNNING",
            best_epoch=self.best_epoch,
            best_map50=round(self.best_map50, 4),
        )


def run_training(data_yaml_path: Path) -> tuple[Path, float, float, int]:
    """执行 YOLOv8n 训练，返回 (最佳权重路径, best_map50, best_map5095, best_epoch)。"""
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    model = YOLO("yolov8n.pt")  # 下载预训练权重（首次运行联网）

    handler = EpochCallbackHandler()
    model.add_callback("on_fit_epoch_end", handler.on_fit_epoch_end)

    log.info("开始训练 epochs=%d img_size=%d batch=%d device=cpu", EPOCHS, IMG_SIZE, BATCH_SIZE)

    results = model.train(
        data=str(data_yaml_path),
        epochs=EPOCHS,
        imgsz=IMG_SIZE,
        batch=BATCH_SIZE,
        device="cpu",
        project=str(OUTPUT_DIR),
        name="train",
        exist_ok=True,
        verbose=False,
        # 关闭不必要的 UI
        plots=False,
        save_period=-1,
    )

    best_weights = Path(results.save_dir) / "weights" / "best.pt"
    if not best_weights.exists():
        best_weights = Path(results.save_dir) / "weights" / "last.pt"

    best_map50   = float(results.results_dict.get("metrics/mAP50(B)",    handler.best_map50))
    best_map5095 = float(results.results_dict.get("metrics/mAP50-95(B)", 0.0))
    best_epoch   = handler.best_epoch

    log.info("训练完成 best_map50=%.4f best_map50_95=%.4f best_epoch=%d",
             best_map50, best_map5095, best_epoch)
    return best_weights, best_map50, best_map5095, best_epoch


# ──────────────────────────────────────────
# 步骤 4：导出 ONNX
# ──────────────────────────────────────────
def export_onnx(best_weights: Path) -> Path:
    log.info("导出 ONNX 模型: %s", best_weights)
    model = YOLO(str(best_weights))
    onnx_path_str = model.export(format="onnx", imgsz=IMG_SIZE, dynamic=False, simplify=True)
    onnx_path = Path(onnx_path_str)
    log.info("ONNX 导出完成: %s (%.1f MB)", onnx_path, onnx_path.stat().st_size / 1_048_576)
    return onnx_path


# ──────────────────────────────────────────
# 步骤 5：上传模型到 MinIO
# ──────────────────────────────────────────
def upload_model_to_minio(onnx_path: Path) -> str:
    minio_secure = not MINIO_ENDPOINT.startswith("localhost") and not MINIO_ENDPOINT.startswith("127.")
    client = Minio(MINIO_ENDPOINT, access_key=MINIO_ACCESS_KEY,
                   secret_key=MINIO_SECRET_KEY, secure=minio_secure)

    # 确保 Bucket 存在
    if not client.bucket_exists(MODEL_STAGING_BUCKET):
        client.make_bucket(MODEL_STAGING_BUCKET)

    object_name = f"{PLUGIN_ID}/{JOB_ID}/best.onnx"
    client.fput_object(MODEL_STAGING_BUCKET, object_name, str(onnx_path),
                       content_type="application/octet-stream")

    model_url = f"minio://{MODEL_STAGING_BUCKET}/{object_name}"
    log.info("模型上传完成: %s", model_url)
    return model_url


# ──────────────────────────────────────────
# 步骤 6：记录到 MLflow
# ──────────────────────────────────────────
def log_to_mlflow(best_weights: Path, onnx_path: Path,
                  best_map50: float, best_map5095: float,
                  class_names: list[str]) -> tuple[str, str]:
    """向 MLflow 记录实验并注册模型，返回 (run_id, model_uri)。"""
    import mlflow

    mlflow.set_tracking_uri(MLFLOW_TRACKING_URI)
    experiment_name = f"tianjing/{PLUGIN_ID}"
    mlflow.set_experiment(experiment_name)

    with mlflow.start_run(run_name=JOB_ID) as run:
        # 记录超参
        mlflow.log_params({
            "epochs":         EPOCHS,
            "img_size":       IMG_SIZE,
            "batch_size":     BATCH_SIZE,
            "plugin_id":      PLUGIN_ID,
            "dataset_version": DATASET_VERSION_ID,
            "device":         "cpu",
        })

        # 记录指标
        mlflow.log_metrics({
            "best_map50":    best_map50,
            "best_map50_95": best_map5095,
        })

        # 记录模型文件（PyTorch 权重 + ONNX）
        mlflow.log_artifact(str(best_weights), artifact_path="weights")
        mlflow.log_artifact(str(onnx_path),    artifact_path="onnx")

        run_id = run.info.run_id

        # 注册到 MLflow Model Registry
        model_uri = f"runs:/{run_id}/onnx"
        registered = mlflow.register_model(model_uri, name=PLUGIN_ID)
        mlflow_model_uri = f"models:/{PLUGIN_ID}/{registered.version}"

        log.info("MLflow 记录完成 run_id=%s model_uri=%s", run_id, mlflow_model_uri)
        return run_id, mlflow_model_uri


# ──────────────────────────────────────────
# 主流程
# ──────────────────────────────────────────
def main():
    # 发送 RUNNING 回调（训练启动）
    _callback(status="RUNNING")

    try:
        # 1. 下载数据集
        download_dataset()

        # 2. 格式转换
        data_yaml_path, class_names = convert_coco_to_yolo()

        # 3. 训练
        best_weights, best_map50, best_map5095, best_epoch = run_training(data_yaml_path)

        # 4. 导出 ONNX
        onnx_path = export_onnx(best_weights)

        # 5. 上传到 MinIO
        model_artifact_url = upload_model_to_minio(onnx_path)

        # 6. 记录到 MLflow
        mlflow_run_id = "unknown"
        mlflow_model_uri = model_artifact_url
        try:
            mlflow_run_id, mlflow_model_uri = log_to_mlflow(
                best_weights, onnx_path, best_map50, best_map5095, class_names
            )
        except Exception as mlflow_err:
            log.warning("MLflow 记录失败（不影响训练结果）error=%s", mlflow_err)

        # 7. T-05：先注册模型版本（STAGING），拿到 version_id 后再回调
        # 顺序：注册 → 回调 COMPLETED（携带 model_version_id）
        # 这样 train_job.model_version_id 在 COMPLETED 时就已写入，前端可直接跳转
        version_tag = f"v{int(time.time())}"
        model_version_id = _register_model(
            mlflow_run_id=mlflow_run_id,
            model_artifact_url=model_artifact_url,
            version_tag=version_tag,
        )

        # 8. 回调 COMPLETED（含 model_version_id，供前端"跳转到模型审核"使用）
        _callback(
            status="COMPLETED",
            mlflow_run_id=mlflow_run_id,
            best_map50=round(best_map50, 4),
            best_map50_95=round(best_map5095, 4),
            best_epoch=best_epoch,
            model_version_id=model_version_id,
        )

        log.info("训练作业全部完成 job_id=%s", JOB_ID)

    except Exception as exc:
        log.exception("训练失败: %s", exc)
        _callback(status="FAILED", error_msg=str(exc))
        sys.exit(1)

    finally:
        # 清理工作目录（节省磁盘）
        if WORK_DIR.exists():
            try:
                shutil.rmtree(WORK_DIR)
            except Exception:
                pass


if __name__ == "__main__":
    main()
