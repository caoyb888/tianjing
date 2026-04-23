#!/usr/bin/env python3
"""
天柱·天镜 — 烧结看火场景 EfficientNet-B2 三分类训练
方法三阶段二：EfficientNet-B2 图像级分类（正常 / 过弱 / 料面不均）

输入：MinIO tianjing-lab-video/sintering/ 中的三段录像
输出：CLASSIFY-FLAME-V1 ONNX 模型 → tianjing-models-staging

用法：
  python3 train_flame_classify.py [--epochs 30] [--batch 32] [--job-id JOB-ID]
"""
import argparse
import logging
import os
import time
import shutil
import subprocess
import sys
from pathlib import Path

# ── 运行时安装依赖（若镜像中未预装）──────────────────────────────────────────
def _pip_install(*pkgs):
    subprocess.check_call([sys.executable, "-m", "pip", "install", "--quiet", *pkgs])

try:
    from pytorch_grad_cam import GradCAM  # noqa
except ImportError:
    print("安装 grad-cam...", flush=True)
    _pip_install("grad-cam==1.4.8")

import cv2
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import Dataset, DataLoader, WeightedRandomSampler
import torchvision.transforms as T
import torchvision.models as tv_models
from pytorch_grad_cam import GradCAM
from pytorch_grad_cam.utils.image import show_cam_on_image
from minio import Minio
import mlflow

# ── 常量 ─────────────────────────────────────────────────────────────────────
LABELS       = ["正常", "过弱", "料面不均"]
PLUGIN_ID    = "CLASSIFY-FLAME-V1"
IMG_SIZE     = 224          # EfficientNet-B2 推荐输入尺寸
MINIO_EP     = os.environ.get("TIANJING_MINIO_ENDPOINT", "http://localhost:9000").replace("http://", "")
MINIO_AK     = os.environ.get("TIANJING_MINIO_ACCESS_KEY", "minioadmin")
MINIO_SK     = os.environ.get("TIANJING_MINIO_SECRET_KEY", "minioadmin123")
MLFLOW_URI   = os.environ.get("MLFLOW_TRACKING_URI", "http://localhost:5000")
WORK_DIR     = Path("/tmp/flame_classify")
LOG_DIR      = Path("/tmp/tianjing-logs")
SRC_BUCKET   = "tianjing-lab-video"
DST_BUCKET   = "tianjing-models-staging"

# 视频文件 → 类别索引
VIDEO_CLASS_MAP = {
    "sintering/normal.mp4": 0,   # 正常
    "sintering/weak.mp4":   1,   # 过弱
    "sintering/uneven.mp4": 2,   # 料面不均
}

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    handlers=[logging.StreamHandler()]
)
log = logging.getLogger("flame-classify")


# ══════════════════════════════════════════════════════════════════════════════
# 步骤 1：从 MinIO 下载录像并抽帧
# ══════════════════════════════════════════════════════════════════════════════

def extract_frames(job_id: str) -> dict[int, list[Path]]:
    """下载录像 → 抽取全部帧 → 返回 {class_idx: [frame_path, ...]}}"""
    minio = Minio(MINIO_EP, access_key=MINIO_AK, secret_key=MINIO_SK, secure=False)
    frames_by_class: dict[int, list[Path]] = {i: [] for i in range(len(LABELS))}

    for obj_path, class_idx in VIDEO_CLASS_MAP.items():
        video_path = WORK_DIR / "videos" / Path(obj_path).name
        video_path.parent.mkdir(parents=True, exist_ok=True)

        # 下载录像
        log.info("下载 %s → %s", obj_path, video_path)
        minio.fget_object(SRC_BUCKET, obj_path, str(video_path))

        # 抽帧（全帧，不降频；连续帧在不同 epoch 的数据增强下差异显著）
        frame_dir = WORK_DIR / "frames" / LABELS[class_idx]
        frame_dir.mkdir(parents=True, exist_ok=True)

        cap = cv2.VideoCapture(str(video_path))
        total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        idx = 0
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            # 统一尺寸：正常录像是竖屏，旋转到横屏
            if frame.shape[0] > frame.shape[1]:
                frame = cv2.rotate(frame, cv2.ROTATE_90_CLOCKWISE)
            out_path = frame_dir / f"{idx:05d}.jpg"
            cv2.imwrite(str(out_path), frame, [cv2.IMWRITE_JPEG_QUALITY, 90])
            frames_by_class[class_idx].append(out_path)
            idx += 1
        cap.release()
        log.info("  %s → %d 帧 (类别: %s)", Path(obj_path).name, idx, LABELS[class_idx])

    for i, paths in frames_by_class.items():
        log.info("类别 %d [%s]: %d 帧", i, LABELS[i], len(paths))

    return frames_by_class


# ══════════════════════════════════════════════════════════════════════════════
# 步骤 2：Dataset / DataLoader
# ══════════════════════════════════════════════════════════════════════════════

class FlameDataset(Dataset):
    def __init__(self, samples: list[tuple[Path, int]], transform=None):
        self.samples = samples
        self.transform = transform

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        path, label = self.samples[idx]
        img = cv2.imread(str(path))
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
        if self.transform:
            from PIL import Image
            img = self.transform(Image.fromarray(img))
        return img, label


def build_loaders(frames_by_class: dict, batch_size: int):
    train_tf = T.Compose([
        T.Resize((IMG_SIZE + 32, IMG_SIZE + 32)),
        T.RandomCrop(IMG_SIZE),
        T.RandomHorizontalFlip(),
        T.RandomVerticalFlip(),
        T.ColorJitter(brightness=0.4, contrast=0.4, saturation=0.3, hue=0.05),
        T.RandomRotation(15),
        T.ToTensor(),
        T.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ])
    val_tf = T.Compose([
        T.Resize((IMG_SIZE, IMG_SIZE)),
        T.ToTensor(),
        T.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ])

    train_samples, val_samples = [], []
    class_counts = []

    for cls_idx, paths in frames_by_class.items():
        np.random.shuffle(paths := list(paths))
        split = max(1, int(len(paths) * 0.8))
        train_samples += [(p, cls_idx) for p in paths[:split]]
        val_samples   += [(p, cls_idx) for p in paths[split:]]
        class_counts.append(len(paths[:split]))

    # WeightedRandomSampler 平衡类别
    total = sum(class_counts)
    class_weights = [total / (len(LABELS) * c) for c in class_counts]
    sample_weights = [class_weights[label] for _, label in train_samples]
    sampler = WeightedRandomSampler(sample_weights, len(train_samples), replacement=True)

    train_ds = FlameDataset(train_samples, train_tf)
    val_ds   = FlameDataset(val_samples,   val_tf)

    train_loader = DataLoader(train_ds, batch_size=batch_size, sampler=sampler,
                              num_workers=4, pin_memory=True)
    val_loader   = DataLoader(val_ds,   batch_size=batch_size, shuffle=False,
                              num_workers=4, pin_memory=True)

    log.info("数据集：train=%d  val=%d  class_weights=%s",
             len(train_samples), len(val_samples),
             [f"{w:.2f}" for w in class_weights])
    return train_loader, val_loader, class_weights


# ══════════════════════════════════════════════════════════════════════════════
# 步骤 3：模型训练
# ══════════════════════════════════════════════════════════════════════════════

def build_model(num_classes: int, device: torch.device) -> nn.Module:
    # 使用 torchvision EfficientNet-B2（无需 HuggingFace/外网）
    # 权重通过挂载 /root/.cache/torch/hub/checkpoints/ 本地缓存加载
    weights = tv_models.EfficientNet_B2_Weights.IMAGENET1K_V1
    model = tv_models.efficientnet_b2(weights=weights)
    # 替换分类头
    in_features = model.classifier[1].in_features
    model.classifier[1] = nn.Linear(in_features, num_classes)
    # 冻结主干前 6 个 features block（features[0..5]），只训练 features[6/7/8] + classifier
    for i in range(6):
        for p in model.features[i].parameters():
            p.requires_grad = False
    trainable = sum(p.numel() for p in model.parameters() if p.requires_grad)
    total     = sum(p.numel() for p in model.parameters())
    log.info("EfficientNet-B2 加载完成（torchvision，冻结 features[0-5]，可训参数 %d/%d）",
             trainable, total)
    return model.to(device)


def train_one_epoch(model, loader, criterion, optimizer, device):
    model.train()
    total_loss, correct, total = 0.0, 0, 0
    for imgs, labels in loader:
        imgs, labels = imgs.to(device), labels.to(device)
        optimizer.zero_grad()
        logits = model(imgs)
        loss = criterion(logits, labels)
        loss.backward()
        optimizer.step()
        total_loss += loss.item() * imgs.size(0)
        correct += (logits.argmax(1) == labels).sum().item()
        total += imgs.size(0)
    return total_loss / total, correct / total


@torch.no_grad()
def evaluate(model, loader, criterion, device):
    model.eval()
    total_loss, correct, total = 0.0, 0, 0
    preds_all, labels_all = [], []
    for imgs, labels in loader:
        imgs, labels = imgs.to(device), labels.to(device)
        logits = model(imgs)
        loss = criterion(logits, labels)
        total_loss += loss.item() * imgs.size(0)
        preds = logits.argmax(1)
        correct += (preds == labels).sum().item()
        total += imgs.size(0)
        preds_all.extend(preds.cpu().tolist())
        labels_all.extend(labels.cpu().tolist())
    # Per-class F1
    from collections import defaultdict
    tp = defaultdict(int); fp = defaultdict(int); fn = defaultdict(int)
    for p, t in zip(preds_all, labels_all):
        if p == t:
            tp[t] += 1
        else:
            fp[p] += 1
            fn[t] += 1
    f1s = []
    for c in range(len(LABELS)):
        prec = tp[c] / (tp[c] + fp[c] + 1e-8)
        rec  = tp[c] / (tp[c] + fn[c] + 1e-8)
        f1s.append(2 * prec * rec / (prec + rec + 1e-8))
    macro_f1 = float(np.mean(f1s))
    return total_loss / total, correct / total, macro_f1, f1s


# ══════════════════════════════════════════════════════════════════════════════
# 步骤 4：Grad-CAM 热力图生成（验证集误分类帧）
# ══════════════════════════════════════════════════════════════════════════════

def generate_gradcam_report(model, val_samples, device, out_dir: Path, max_per_class=5):
    """对验证集中误分类帧生成 Grad-CAM 热力图"""
    out_dir.mkdir(parents=True, exist_ok=True)
    val_tf = T.Compose([
        T.Resize((IMG_SIZE, IMG_SIZE)),
        T.ToTensor(),
        T.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ])

    # torchvision EfficientNet-B2 的最后一个卷积块（features[-1] 是 Conv2dNormActivation）
    target_layers = [model.features[-1]]
    cam = GradCAM(model=model, target_layers=target_layers)

    model.eval()
    counts = {i: 0 for i in range(len(LABELS))}
    generated = 0

    for path, true_label in val_samples:
        img_bgr = cv2.imread(str(path))
        if img_bgr is None:
            continue
        img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
        img_resized = cv2.resize(img_rgb, (IMG_SIZE, IMG_SIZE))

        from PIL import Image
        tensor = val_tf(Image.fromarray(img_resized)).unsqueeze(0).to(device)

        with torch.no_grad():
            pred = model(tensor).argmax(1).item()

        # 只对误分类帧生成热力图
        if pred == true_label:
            continue
        if counts[true_label] >= max_per_class:
            continue

        grayscale_cam = cam(input_tensor=tensor)[0]
        visualization = show_cam_on_image(img_resized / 255.0, grayscale_cam, use_rgb=True)

        fname = f"true_{LABELS[true_label]}_pred_{LABELS[pred]}_{path.stem}.jpg"
        save_path = out_dir / f"class_{true_label}" / fname
        save_path.parent.mkdir(exist_ok=True)
        cv2.imwrite(str(save_path), cv2.cvtColor(visualization, cv2.COLOR_RGB2BGR))

        counts[true_label] += 1
        generated += 1
        if all(v >= max_per_class for v in counts.values()):
            break

    log.info("Grad-CAM 热力图生成完成: %d 张误分类帧", generated)
    return generated


# ══════════════════════════════════════════════════════════════════════════════
# 步骤 5：ONNX 导出
# ══════════════════════════════════════════════════════════════════════════════

def export_onnx(model, out_path: Path, device: torch.device) -> Path:
    model.eval()
    dummy = torch.randn(1, 3, IMG_SIZE, IMG_SIZE, device=device)
    torch.onnx.export(
        model.cpu(), dummy.cpu(), str(out_path),
        input_names=["image"],
        output_names=["logits"],
        opset_version=17,
        dynamic_axes={"image": {0: "batch"}, "logits": {0: "batch"}},
    )
    sz_mb = out_path.stat().st_size / 1024 / 1024
    log.info("ONNX 导出完成: %s (%.1f MB)", out_path, sz_mb)
    return out_path


# ══════════════════════════════════════════════════════════════════════════════
# 步骤 6：上传 MinIO
# ══════════════════════════════════════════════════════════════════════════════

def upload_to_minio(local_path: Path, job_id: str) -> str:
    minio = Minio(MINIO_EP, access_key=MINIO_AK, secret_key=MINIO_SK, secure=False)
    if not minio.bucket_exists(DST_BUCKET):
        minio.make_bucket(DST_BUCKET)
    obj_path = f"{PLUGIN_ID}/{job_id}/best.onnx"
    minio.fput_object(DST_BUCKET, obj_path, str(local_path))
    url = f"minio://{DST_BUCKET}/{obj_path}"
    log.info("模型上传完成: %s", url)
    return url


# ══════════════════════════════════════════════════════════════════════════════
# 主流程
# ══════════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs",   type=int,   default=30)
    parser.add_argument("--batch",    type=int,   default=32)
    parser.add_argument("--lr",       type=float, default=1e-4)
    parser.add_argument("--job-id",   default=f"FLAME-{int(time.time())}")
    parser.add_argument("--no-mlflow", action="store_true")
    args = parser.parse_args()

    LOG_DIR.mkdir(parents=True, exist_ok=True)
    WORK_DIR.mkdir(parents=True, exist_ok=True)

    log_file = LOG_DIR / f"train_flame_{args.job_id}.log"
    fh = logging.FileHandler(log_file)
    fh.setFormatter(logging.Formatter("%(asctime)s %(levelname)s %(message)s"))
    logging.getLogger().addHandler(fh)

    log.info("=" * 60)
    log.info("天柱·天镜 烧结看火三分类训练")
    log.info("Job ID  : %s", args.job_id)
    log.info("Plugin  : %s", PLUGIN_ID)
    log.info("类别    : %s", LABELS)
    log.info("Epochs  : %d  Batch: %d  LR: %g", args.epochs, args.batch, args.lr)
    log.info("=" * 60)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    log.info("设备: %s", device)

    # 1. 抽帧
    log.info("── 步骤 1：下载录像并抽帧 ──")
    frames_by_class = extract_frames(args.job_id)

    # 2. DataLoader
    log.info("── 步骤 2：构建数据集 ──")
    train_loader, val_loader, class_weights = build_loaders(frames_by_class, args.batch)
    val_samples = val_loader.dataset.samples  # 用于 Grad-CAM

    # 3. 模型
    log.info("── 步骤 3：模型训练 ──")
    model = build_model(len(LABELS), device)
    weight_tensor = torch.tensor(class_weights, dtype=torch.float, device=device)
    criterion = nn.CrossEntropyLoss(weight=weight_tensor)
    optimizer = torch.optim.Adam(
        filter(lambda p: p.requires_grad, model.parameters()), lr=args.lr
    )
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)

    best_f1, best_epoch = 0.0, 0
    ckpt_dir = WORK_DIR / args.job_id
    ckpt_dir.mkdir(parents=True, exist_ok=True)
    best_ckpt = ckpt_dir / "best.pt"

    for epoch in range(1, args.epochs + 1):
        t0 = time.time()
        train_loss, train_acc = train_one_epoch(model, train_loader, criterion, optimizer, device)
        val_loss, val_acc, macro_f1, f1s = evaluate(model, val_loader, criterion, device)
        scheduler.step()
        elapsed = (time.time() - t0) / 60

        is_best = macro_f1 > best_f1
        if is_best:
            best_f1, best_epoch = macro_f1, epoch
            torch.save(model.state_dict(), best_ckpt)

        per_class = "  ".join(f"{LABELS[i]}={f1s[i]:.3f}" for i in range(len(LABELS)))
        log.info("Epoch %3d/%d  loss=%.4f  acc=%.4f  macroF1=%.4f  best=%.4f(@ep%d)  %.1fmin%s",
                 epoch, args.epochs, val_loss, val_acc, macro_f1, best_f1, best_epoch, elapsed,
                 "  ★" if is_best else "")
        log.info("         per-class: %s", per_class)

    log.info("训练完成: best_macroF1=%.4f  best_epoch=%d", best_f1, best_epoch)

    # 4. Grad-CAM 报告
    log.info("── 步骤 4：Grad-CAM 热力图生成 ──")
    model.load_state_dict(torch.load(best_ckpt, map_location=device))
    gradcam_dir = ckpt_dir / "gradcam"
    generate_gradcam_report(model, val_samples, device, gradcam_dir)

    # 5. ONNX 导出
    log.info("── 步骤 5：ONNX 导出 ──")
    onnx_path = ckpt_dir / "best.onnx"
    export_onnx(model, onnx_path, device)

    # 6. 上传 MinIO
    log.info("── 步骤 6：上传 MinIO ──")
    minio_url = upload_to_minio(onnx_path, args.job_id)

    # 7. MLflow（可选）
    if not args.no_mlflow:
        try:
            import os as _os
            _os.environ.setdefault("MLFLOW_HTTP_REQUEST_TIMEOUT", "10")
            _os.environ.setdefault("MLFLOW_HTTP_REQUEST_MAX_RETRIES", "1")
            mlflow.set_tracking_uri(MLFLOW_URI)
            mlflow.set_experiment(f"tianjing/{PLUGIN_ID}")
            with mlflow.start_run(run_name=args.job_id):
                mlflow.log_params({
                    "model": "efficientnet_b2", "epochs": args.epochs,
                    "batch": args.batch, "lr": args.lr,
                    "classes": str(LABELS), "img_size": IMG_SIZE,
                })
                mlflow.log_metric("best_macro_f1", best_f1)
                mlflow.log_metric("best_epoch", best_epoch)
                for i, label in enumerate(LABELS):
                    _, _, _, f1s = evaluate(model, val_loader, criterion, device)
                    mlflow.log_metric(f"f1_{label}", f1s[i])
                mlflow.log_artifact(str(onnx_path), artifact_path="onnx")
                if gradcam_dir.exists():
                    mlflow.log_artifacts(str(gradcam_dir), artifact_path="gradcam")
            log.info("MLflow 记录完成")
        except Exception as e:
            log.warning("MLflow 记录失败（不影响结果）: %s", e)

    log.info("=" * 60)
    log.info("训练全部完成!")
    log.info("best_macroF1 = %.4f  best_epoch = %d", best_f1, best_epoch)
    log.info("ONNX 模型    = %s", onnx_path)
    log.info("MinIO URL    = %s", minio_url)
    log.info("Grad-CAM 报告= %s", gradcam_dir)
    log.info("日志文件     = %s", log_file)
    log.info("=" * 60)

    # 输出是否达到上线标准
    if best_f1 >= 0.85:
        log.info("✅ 精度达标（macroF1 %.4f ≥ 0.85），可进入阶段三接入推理链路", best_f1)
    else:
        log.warning("⚠️  精度未达标（macroF1 %.4f < 0.85），建议补充数据后重训", best_f1)


if __name__ == "__main__":
    main()
