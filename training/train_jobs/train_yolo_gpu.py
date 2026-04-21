#!/usr/bin/env python3
"""
天柱·天镜 — YOLOv8 GPU 训练脚本（V100 本地数据集版）

用法：
  # 单数据集训练
  python3 train_yolo_gpu.py --datasets NEU_surface_defect_converted

  # 多数据集合并训练（推荐首次）
  python3 train_yolo_gpu.py --datasets NEU_surface_defect_converted,GC10-DET_converted,Severstal_converted

  # 全量合并（所有已转换数据集）
  python3 train_yolo_gpu.py --datasets all

  # 完整参数示例
  python3 train_yolo_gpu.py \\
    --datasets all \\
    --model yolov8s \\
    --epochs 100 \\
    --batch 32 \\
    --img-size 640 \\
    --job-id TJ-GPU-001

后台运行（推荐）：
  nohup python3 train_yolo_gpu.py --datasets all --epochs 100 \\
    > /tmp/tianjing-logs/train_gpu_$(date +%Y%m%d_%H%M%S).log 2>&1 &
  # 查看进度
  tail -f /tmp/tianjing-logs/train_gpu_*.log

进度查询：
  MLflow UI → http://localhost:5000（若 MLflow 已启动）
  日志文件  → /tmp/tianjing-logs/train_gpu_{job_id}.log

数据集目录：/home/tzai/tianjing/training/datasets_converted/
  每个数据集须含 images/ 和 annotations.json（COCO 格式）
"""

import argparse
import json
import logging
import os
import random
import shutil
import sys
import time
from pathlib import Path

import requests
import yaml

# ──────────────────────────────────────────
# 常量
# ──────────────────────────────────────────
DATASETS_ROOT    = Path("/home/tzai/tianjing/training/datasets_converted")
LOG_DIR          = Path("/tmp/tianjing-logs")
WORK_BASE        = Path("/tmp/train_gpu")
MINIO_ENDPOINT   = os.environ.get("TIANJING_MINIO_ENDPOINT", "http://localhost:9000") \
                       .removeprefix("https://").removeprefix("http://").rstrip("/")
MINIO_ACCESS_KEY = os.environ.get("TIANJING_MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY = os.environ.get("TIANJING_MINIO_SECRET_KEY", "minioadmin123")
MINIO_SECURE     = os.environ.get("TIANJING_MINIO_ENDPOINT", "").startswith("https://")
MLFLOW_URI       = os.environ.get("MLFLOW_TRACKING_URI", "http://localhost:5000")
PLATFORM_CB_URL  = os.environ.get("PLATFORM_CALLBACK_URL", "http://localhost:8089")
MODEL_REG_URL    = os.environ.get("MODEL_REGISTER_URL", "http://localhost:8086")
MODEL_STAGING_BUCKET = "tianjing-models-staging"

# 可用数据集列表（ls datasets_converted/ | grep _converted）
AVAILABLE_DATASETS = [
    "NEU_surface_defect_converted",
    "GC10-DET_converted",
    "Magnetic_tile_defect_converted",
    "Severstal_converted",
    "MVTec_AD_converted",
    "DAGM_2007_converted",
]

# ──────────────────────────────────────────
# 参数解析
# ──────────────────────────────────────────
def parse_args():
    p = argparse.ArgumentParser(description="YOLOv8 GPU 训练 — 天柱天镜")
    p.add_argument("--datasets", default="all",
                   help="数据集名（逗号分隔），或 'all' 合并全部。"
                        f"可选：{','.join(AVAILABLE_DATASETS)}")
    p.add_argument("--model",    default="yolov8s",
                   choices=["yolov8n", "yolov8s", "yolov8m", "yolov8l"],
                   help="基础模型规格（默认 yolov8s，V100 32GB 推荐 s/m）")
    p.add_argument("--epochs",   type=int, default=100)
    p.add_argument("--batch",    type=int, default=32,
                   help="batch size（V100 32GB：yolov8s@640 推荐 32）")
    p.add_argument("--img-size", type=int, default=640)
    p.add_argument("--device",   default="0",
                   help="GPU 编号，'0'=单卡，'0,1'=双卡，'cpu'=调试用")
    p.add_argument("--job-id",   default=None,
                   help="训练作业 ID（默认自动生成）")
    p.add_argument("--plugin-id", default="LOCAL-GPU-YOLO-V1",
                   help="算法插件 ID（写入 MLflow 和 MinIO 路径）")
    p.add_argument("--no-upload", action="store_true",
                   help="跳过 MinIO 上传（本地调试用）")
    p.add_argument("--no-mlflow", action="store_true",
                   help="跳过 MLflow 记录")
    return p.parse_args()


# ──────────────────────────────────────────
# 日志配置
# ──────────────────────────────────────────
def setup_logging(job_id: str) -> logging.Logger:
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    log_file = LOG_DIR / f"train_gpu_{job_id}.log"

    fmt = "%(asctime)s %(levelname)s %(message)s"
    handlers = [
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(log_file, encoding="utf-8"),
    ]
    logging.basicConfig(level=logging.INFO, format=fmt, handlers=handlers)
    log = logging.getLogger("train_yolo_gpu")
    log.info("日志文件: %s", log_file)
    log.info("查看进度: tail -f %s", log_file)
    return log


# ──────────────────────────────────────────
# 工具函数
# ──────────────────────────────────────────
def _callback(job_id: str, status: str, log: logging.Logger, **kwargs):
    url = f"{PLATFORM_CB_URL}/internal/training/jobs/{job_id}/callback"
    try:
        resp = requests.post(url, json={"status": status, **kwargs}, timeout=5)
        resp.raise_for_status()
    except Exception as exc:
        log.debug("drift-monitor 回调失败（忽略）: %s", exc)


def _register_model(job_id: str, plugin_id: str, mlflow_run_id: str,
                    model_url: str, version_tag: str, log: logging.Logger):
    url = f"{MODEL_REG_URL}/internal/models/register"
    try:
        resp = requests.post(url, json={
            "plugin_id": plugin_id, "version": version_tag,
            "mlflow_run_id": mlflow_run_id, "model_artifact_url": model_url,
            "training_job_id": job_id,
        }, timeout=15)
        resp.raise_for_status()
        return resp.json().get("data", {}).get("versionId", "unknown")
    except Exception as exc:
        log.warning("模型版本注册失败（不影响训练结果）: %s", exc)
        return None


# ──────────────────────────────────────────
# 步骤 1：解析并合并数据集 → YOLO 格式
# ──────────────────────────────────────────
def prepare_merged_dataset(dataset_names: list[str], work_dir: Path,
                            img_size: int, log: logging.Logger) -> tuple[Path, list[str]]:
    """
    将一个或多个 COCO 数据集合并，转换为 YOLO 格式。
    类别编号全局统一（跨数据集去重）。
    返回 (data_yaml_path, merged_class_names)
    """
    log.info("准备数据集: %s", dataset_names)

    # ── 汇总所有类别（跨数据集全局编号）
    global_cat_names: list[str] = []
    cat_name_to_idx: dict[str, int] = {}

    # ── 收集所有图片+标注
    all_items: list[dict] = []   # {img_path, width, height, anns_yolo}

    for ds_name in dataset_names:
        ds_dir  = DATASETS_ROOT / ds_name
        ann_path = ds_dir / "annotations.json"
        img_dir  = ds_dir / "images"

        if not ann_path.exists():
            log.warning("跳过 %s：找不到 annotations.json", ds_name)
            continue

        with open(ann_path, encoding="utf-8") as f:
            coco = json.load(f)

        # 局部 cat_id → 全局 idx 映射
        local_cat_map: dict[int, int] = {}
        for cat in coco.get("categories", []):
            name = cat["name"]
            if name not in cat_name_to_idx:
                cat_name_to_idx[name] = len(global_cat_names)
                global_cat_names.append(name)
            local_cat_map[cat["id"]] = cat_name_to_idx[name]

        # 按 image_id 分组标注
        anns_by_img: dict[int, list] = {}
        for ann in coco.get("annotations", []):
            anns_by_img.setdefault(ann["image_id"], []).append(ann)

        # 图片列表（去除磁盘上不存在的）
        for img_info in coco.get("images", []):
            fname   = Path(img_info["file_name"]).name
            src     = img_dir / fname
            if not src.exists():
                continue
            iw = img_info.get("width",  640)
            ih = img_info.get("height", 640)

            yolo_lines: list[str] = []
            for ann in anns_by_img.get(img_info["id"], []):
                g_idx = local_cat_map.get(ann["category_id"])
                if g_idx is None:
                    continue
                x, y, w, h = ann["bbox"]
                cx = max(0.0, min(1.0, (x + w / 2) / iw))
                cy = max(0.0, min(1.0, (y + h / 2) / ih))
                nw = max(0.0, min(1.0, w / iw))
                nh = max(0.0, min(1.0, h / ih))
                yolo_lines.append(f"{g_idx} {cx:.6f} {cy:.6f} {nw:.6f} {nh:.6f}")

            all_items.append({
                "src": src,
                "fname": f"{ds_name}__{fname}",   # 加前缀防跨数据集同名
                "yolo_lines": yolo_lines,
            })

        log.info("  %-40s %d 张图，%d 条标注，%d 类",
                 ds_name, len(coco.get("images", [])),
                 len(coco.get("annotations", [])), len(coco.get("categories", [])))

    if not all_items:
        raise RuntimeError("没有找到任何有效图片，请检查数据集路径")

    log.info("合并后：%d 张图，%d 个全局类别", len(all_items), len(global_cat_names))

    # ── 划分 train/val（8:2）
    random.seed(42)
    random.shuffle(all_items)
    split_idx = max(1, int(len(all_items) * 0.8))
    splits = {"train": all_items[:split_idx], "val": all_items[split_idx:]}
    if not splits["val"]:
        splits["val"] = all_items[:max(1, split_idx // 5)]

    # ── 写入文件
    yolo_dir = work_dir / "yolo_data"
    for sname, items in splits.items():
        img_out = yolo_dir / "images" / sname
        lbl_out = yolo_dir / "labels" / sname
        img_out.mkdir(parents=True, exist_ok=True)
        lbl_out.mkdir(parents=True, exist_ok=True)

        for item in items:
            shutil.copy2(item["src"], img_out / item["fname"])
            lbl_path = lbl_out / (Path(item["fname"]).stem + ".txt")
            lbl_path.write_text("\n".join(item["yolo_lines"]), encoding="utf-8")

    data_yaml_path = yolo_dir / "data.yaml"
    with open(data_yaml_path, "w", encoding="utf-8") as f:
        yaml.dump({
            "path":  str(yolo_dir),
            "train": "images/train",
            "val":   "images/val",
            "nc":    len(global_cat_names),
            "names": global_cat_names,
        }, f, allow_unicode=True)

    log.info("数据准备完成：train=%d val=%d nc=%d",
             len(splits["train"]), len(splits["val"]), len(global_cat_names))
    return data_yaml_path, global_cat_names


# ──────────────────────────────────────────
# 步骤 2：训练（含 per-epoch 日志回调）
# ──────────────────────────────────────────
class EpochLogger:
    def __init__(self, job_id: str, log: logging.Logger):
        self.job_id   = job_id
        self.log      = log
        self.best_map50 = 0.0
        self.best_epoch = 0
        self.start_ts   = time.time()

    def on_fit_epoch_end(self, trainer):
        metrics    = trainer.metrics
        map50      = float(metrics.get("metrics/mAP50(B)", 0.0))
        epoch      = trainer.epoch + 1
        elapsed    = (time.time() - self.start_ts) / 60

        if map50 > self.best_map50:
            self.best_map50 = map50
            self.best_epoch = epoch

        self.log.info(
            "Epoch %3d/%d  mAP50=%.4f  best=%.4f(@ep%d)  elapsed=%.1fmin",
            epoch, trainer.args.epochs, map50, self.best_map50, self.best_epoch, elapsed
        )
        _callback(self.job_id, "RUNNING", self.log,
                  current_epoch=epoch, best_map50=round(self.best_map50, 4))


def run_training(args, data_yaml_path: Path, work_dir: Path,
                 log: logging.Logger) -> tuple[Path, float, float, int]:
    from ultralytics import YOLO

    OUTPUT_DIR = work_dir / "output"
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    model   = YOLO(f"{args.model}.pt")
    handler = EpochLogger(args.job_id, log)
    model.add_callback("on_fit_epoch_end", handler.on_fit_epoch_end)

    log.info("开始训练 model=%s epochs=%d batch=%d img_size=%d device=%s",
             args.model, args.epochs, args.batch, args.img_size, args.device)
    log.info("查看实时进度: tail -f %s/train_gpu_%s.log",
             LOG_DIR, args.job_id)

    # 清除 ultralytics 自动 MLflow（步骤 4 手动记录）
    os.environ["MLFLOW_TRACKING_URI"] = ""

    results = model.train(
        data       = str(data_yaml_path),
        epochs     = args.epochs,
        imgsz      = args.img_size,
        batch      = args.batch,
        device     = args.device,
        project    = str(OUTPUT_DIR),
        name       = "train",
        exist_ok   = True,
        verbose    = False,
        plots      = True,    # 保存混淆矩阵等图表
        save_period = 10,     # 每 10 epoch 保存一次检查点
        patience   = 20,      # 早停：20 epoch 无改善则停止
        workers    = 4,
        amp        = True,    # 混合精度（V100 支持）
    )

    best_weights = Path(results.save_dir) / "weights" / "best.pt"
    if not best_weights.exists():
        best_weights = Path(results.save_dir) / "weights" / "last.pt"

    best_map50   = float(results.results_dict.get("metrics/mAP50(B)",    handler.best_map50))
    best_map5095 = float(results.results_dict.get("metrics/mAP50-95(B)", 0.0))

    log.info("训练完成 best_mAP50=%.4f  best_mAP50-95=%.4f  best_epoch=%d",
             best_map50, best_map5095, handler.best_epoch)
    return best_weights, best_map50, best_map5095, handler.best_epoch


# ──────────────────────────────────────────
# 步骤 3：导出 ONNX
# ──────────────────────────────────────────
def export_onnx(best_weights: Path, img_size: int, log: logging.Logger) -> Path:
    from ultralytics import YOLO
    log.info("导出 ONNX: %s", best_weights)
    model = YOLO(str(best_weights))
    onnx_path = Path(model.export(format="onnx", imgsz=img_size,
                                  dynamic=False, simplify=True))
    log.info("ONNX 导出完成: %s (%.1f MB)",
             onnx_path, onnx_path.stat().st_size / 1_048_576)
    return onnx_path


# ──────────────────────────────────────────
# 步骤 4：上传 MinIO
# ──────────────────────────────────────────
def upload_to_minio(onnx_path: Path, plugin_id: str, job_id: str,
                    log: logging.Logger) -> str:
    from minio import Minio
    client = Minio(MINIO_ENDPOINT, access_key=MINIO_ACCESS_KEY,
                   secret_key=MINIO_SECRET_KEY, secure=MINIO_SECURE)
    if not client.bucket_exists(MODEL_STAGING_BUCKET):
        client.make_bucket(MODEL_STAGING_BUCKET)
    obj = f"{plugin_id}/{job_id}/best.onnx"
    client.fput_object(MODEL_STAGING_BUCKET, obj, str(onnx_path),
                       content_type="application/octet-stream")
    url = f"minio://{MODEL_STAGING_BUCKET}/{obj}"
    log.info("模型上传完成: %s", url)
    return url


# ──────────────────────────────────────────
# 步骤 5：MLflow 记录
# ──────────────────────────────────────────
def log_to_mlflow(args, best_weights: Path, onnx_path: Path, class_names: list[str],
                  best_map50: float, best_map5095: float, dataset_names: list[str],
                  log: logging.Logger) -> tuple[str, str]:
    import mlflow
    import os as _os

    # 快速失败：连接超时 10 秒，避免 MLflow 不可达时阻塞数分钟
    _os.environ.setdefault("MLFLOW_HTTP_REQUEST_TIMEOUT", "10")
    _os.environ.setdefault("MLFLOW_HTTP_REQUEST_MAX_RETRIES", "1")

    mlflow.set_tracking_uri(MLFLOW_URI)
    mlflow.set_experiment(f"tianjing/{args.plugin_id}")

    with mlflow.start_run(run_name=args.job_id) as run:
        mlflow.log_params({
            "model":        args.model,
            "epochs":       args.epochs,
            "batch_size":   args.batch,
            "img_size":     args.img_size,
            "device":       args.device,
            "plugin_id":    args.plugin_id,
            "datasets":     "+".join(dataset_names),
            "num_classes":  len(class_names),
        })
        mlflow.log_metrics({
            "best_map50":    best_map50,
            "best_map50_95": best_map5095,
        })
        mlflow.log_artifact(str(best_weights), artifact_path="weights")
        mlflow.log_artifact(str(onnx_path),    artifact_path="onnx")

        run_id = run.info.run_id
        registered = mlflow.register_model(f"runs:/{run_id}/onnx", name=args.plugin_id)
        model_uri  = f"models:/{args.plugin_id}/{registered.version}"

    log.info("MLflow 记录完成: run_id=%s  model_uri=%s", run_id, model_uri)
    return run_id, model_uri


# ──────────────────────────────────────────
# 主流程
# ──────────────────────────────────────────
def main():
    args = parse_args()

    # 生成 job_id
    if not args.job_id:
        args.job_id = f"TJ-GPU-{int(time.time())}"

    log = setup_logging(args.job_id)
    log.info("=" * 60)
    log.info("天柱·天镜 YOLOv8 GPU 训练作业")
    log.info("Job ID  : %s", args.job_id)
    log.info("Plugin  : %s", args.plugin_id)
    log.info("Model   : %s", args.model)
    log.info("Epochs  : %d  Batch: %d  ImgSize: %d  Device: %s",
             args.epochs, args.batch, args.img_size, args.device)

    # 解析数据集列表
    if args.datasets.strip().lower() == "all":
        dataset_names = [d for d in AVAILABLE_DATASETS
                         if (DATASETS_ROOT / d).exists()]
    else:
        dataset_names = [d.strip() for d in args.datasets.split(",") if d.strip()]

    log.info("数据集  : %s", dataset_names)
    log.info("=" * 60)

    work_dir = WORK_BASE / args.job_id
    work_dir.mkdir(parents=True, exist_ok=True)

    _callback(args.job_id, "RUNNING", log)

    try:
        # 1. 数据准备
        data_yaml, class_names = prepare_merged_dataset(
            dataset_names, work_dir, args.img_size, log)

        # 2. 训练
        best_weights, best_map50, best_map5095, best_epoch = run_training(
            args, data_yaml, work_dir, log)

        # 3. 导出 ONNX
        onnx_path = export_onnx(best_weights, args.img_size, log)

        # 4. 上传 MinIO
        model_url = "local-only"
        if not args.no_upload:
            try:
                model_url = upload_to_minio(onnx_path, args.plugin_id, args.job_id, log)
            except Exception as e:
                log.warning("MinIO 上传失败（不影响训练结果）: %s", e)

        # 5. MLflow
        mlflow_run_id = "skipped"
        if not args.no_mlflow:
            try:
                mlflow_run_id, _ = log_to_mlflow(
                    args, best_weights, onnx_path, class_names,
                    best_map50, best_map5095, dataset_names, log)
            except Exception as e:
                log.warning("MLflow 记录失败（不影响训练结果）: %s", e)

        # 6. 注册模型版本
        version_tag = f"v{int(time.time())}"
        model_version_id = _register_model(
            args.job_id, args.plugin_id, mlflow_run_id,
            model_url, version_tag, log)

        # 7. 完成回调
        _callback(args.job_id, "COMPLETED", log,
                  best_map50=round(best_map50, 4),
                  best_map50_95=round(best_map5095, 4),
                  best_epoch=best_epoch,
                  model_version_id=model_version_id,
                  onnx_path=str(onnx_path))

        log.info("=" * 60)
        log.info("训练全部完成!")
        log.info("best_mAP50   = %.4f", best_map50)
        log.info("best_mAP5095 = %.4f", best_map5095)
        log.info("ONNX 模型    = %s", onnx_path)
        log.info("MinIO URL    = %s", model_url)
        log.info("=" * 60)

    except Exception as exc:
        log.exception("训练失败: %s", exc)
        _callback(args.job_id, "FAILED", log, error_msg=str(exc))
        sys.exit(1)

    finally:
        # 保留 ONNX 和权重，只清理中间数据
        yolo_data_dir = work_dir / "yolo_data"
        if yolo_data_dir.exists():
            try:
                shutil.rmtree(yolo_data_dir)
                log.info("已清理中间数据目录: %s", yolo_data_dir)
            except Exception:
                pass


if __name__ == "__main__":
    main()
