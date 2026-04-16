#!/usr/bin/env python3
"""
视频帧注入 Kafka 工具 — 天柱·天镜

功能：读取本地视频文件（单文件或目录），按配置帧率抽帧，上传至 MinIO，
     再将帧消息发布至 Kafka tianjing.frame.production（或 sandbox）。
     支持循环播放，专为实验室录像端到端验证设计。

依赖（uv 自动安装）：
  opencv-python-headless, kafka-python, minio

用法：
  # 单文件，无限循环（默认）
  uv run --with opencv-python-headless --with kafka-python --with minio \
      scripts/video_to_kafka.py --video /tmp/sintering.mp4 --scene-id SCENE-SINTER-FIRE-001

  # 目录模式：循环播放 /tmp/mp4/ 下所有视频（按文件名排序），无限循环
  uv run --with opencv-python-headless --with kafka-python --with minio \
      scripts/video_to_kafka.py --video-dir /tmp/mp4 --scene-id SCENE-SINTER-FIRE-001

  # 目录模式，循环整目录 3 轮后退出
  uv run --with opencv-python-headless --with kafka-python --with minio \
      scripts/video_to_kafka.py --video-dir /tmp/mp4 --scene-id SCENE-SINTER-FIRE-001 \
      --loop-count 3 --fps 5

  # Sandbox 模式（帧标记 is_sandbox=true，告警会被拦截）
  uv run --with opencv-python-headless --with kafka-python --with minio \
      scripts/video_to_kafka.py --video-dir /tmp/mp4 --scene-id SCENE-SINTER-FIRE-001 \
      --sandbox

  停止：Ctrl+C
"""

import argparse
import base64
import json
import os
import sys
import time
import uuid
from datetime import datetime

import cv2
import numpy as np
from kafka import KafkaProducer
from minio import Minio
from minio.error import S3Error

# ─── 默认连接配置（与 start-backend.sh 保持一致）────────────────────────────

KAFKA_BOOTSTRAP   = os.getenv("TIANJING_KAFKA_BOOTSTRAP_SERVERS", "localhost:9094")
MINIO_ENDPOINT    = os.getenv("TIANJING_MINIO_ENDPOINT", "http://localhost:9000")
MINIO_ACCESS_KEY  = os.getenv("TIANJING_MINIO_ACCESS_KEY", "minioadmin")
MINIO_SECRET_KEY  = os.getenv("TIANJING_MINIO_SECRET_KEY", "minioadmin123")
FRAMES_BUCKET     = "tianjing-frames-prod"   # 生产帧桶（CLAUDE.md §7.3）

# ─── 解析参数 ────────────────────────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(description="视频帧注入 Kafka — 天柱·天镜")
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("--video",     help="单个本地视频文件路径（mp4/avi/mkv 等）")
    src.add_argument("--video-dir", help="视频目录，自动扫描并按文件名排序循环播放所有视频")
    p.add_argument("--scene-id",   required=True,   help="场景 ID，如 SCENE-SINTER-FIRE-001")
    p.add_argument("--fps",        type=float, default=5,   help="注入帧率（默认 5fps）")
    p.add_argument("--loop-count", type=int,   default=-1,
                   help="循环次数，-1 为无限循环（目录模式下 1 次 = 完整播放目录内所有文件一遍）")
    p.add_argument("--sandbox",    action="store_true",     help="Sandbox 模式：is_sandbox=true，告警不推送")
    p.add_argument("--factory",    default="sintering",     help="厂部代码（默认 sintering）")
    p.add_argument("--dry-run",    action="store_true",     help="试运行：只抽帧，不发 Kafka/MinIO")
    return p.parse_args()


# ─── 视频文件扫描 ─────────────────────────────────────────────────────────────

VIDEO_EXTS = {".mp4", ".avi", ".mkv", ".mov", ".flv", ".ts", ".wmv"}


def collect_video_files(args) -> list[str]:
    """根据参数返回有序视频文件列表（单文件或目录扫描）"""
    if args.video:
        if not os.path.exists(args.video):
            print(f"[错误] 视频文件不存在: {args.video}", file=sys.stderr)
            sys.exit(1)
        return [args.video]

    video_dir = args.video_dir
    if not os.path.isdir(video_dir):
        print(f"[错误] 视频目录不存在: {video_dir}", file=sys.stderr)
        sys.exit(1)

    files = sorted(
        os.path.join(video_dir, f)
        for f in os.listdir(video_dir)
        if os.path.splitext(f)[1].lower() in VIDEO_EXTS
    )
    if not files:
        print(f"[错误] 目录中未找到视频文件（支持格式：{', '.join(sorted(VIDEO_EXTS))}）: {video_dir}",
              file=sys.stderr)
        sys.exit(1)
    return files

# ─── MinIO 工具 ──────────────────────────────────────────────────────────────

def make_minio_client():
    endpoint = MINIO_ENDPOINT.replace("http://", "").replace("https://", "")
    secure   = MINIO_ENDPOINT.startswith("https://")
    return Minio(endpoint, access_key=MINIO_ACCESS_KEY,
                 secret_key=MINIO_SECRET_KEY, secure=secure)


def ensure_bucket(client: Minio, bucket: str):
    if not client.bucket_exists(bucket):
        client.make_bucket(bucket)
        print(f"[MinIO] 创建 bucket: {bucket}")


def upload_frame(client: Minio, bucket: str, object_path: str,
                 jpeg_bytes: bytes) -> str:
    """上传帧图像，返回 MinIO 内部 URL"""
    import io
    client.put_object(
        bucket, object_path,
        data=io.BytesIO(jpeg_bytes),
        length=len(jpeg_bytes),
        content_type="image/jpeg"
    )
    # 内部 URL 格式（CLAUDE.md §7.3）
    return f"minio://{bucket}/{object_path}"

# ─── Kafka 工具 ──────────────────────────────────────────────────────────────

def make_kafka_producer():
    return KafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP,
        value_serializer=lambda v: json.dumps(v, ensure_ascii=False).encode("utf-8"),
        # 按 scene_id 哈希分区（CLAUDE.md §8.1）
        key_serializer=lambda k: k.encode("utf-8") if k else None,
        acks="all",
        retries=3,
        linger_ms=10,
    )


def get_topic(is_sandbox: bool) -> str:
    # CLAUDE.md §8.1：生产/sandbox topic 严格分开
    return "tianjing.frame.sandbox" if is_sandbox else "tianjing.frame.production"

# ─── 帧消息构造（CLAUDE.md §8.3）────────────────────────────────────────────

def make_frame_message(scene_id: str, frame_id: str, image_url: str,
                       width: int, height: int, timestamp_ms: int,
                       is_sandbox: bool) -> dict:
    return {
        "frame_id":      frame_id,
        "scene_id":      scene_id,
        "timestamp_ms":  timestamp_ms,
        "is_sandbox":    is_sandbox,            # CLAUDE.md §11.1：必须原样透传
        "image_url":     image_url,
        "image_width":   width,
        "image_height":  height,
        "roi": {"x": 0, "y": 0, "w": width, "h": height},
    }

# ─── 主循环 ──────────────────────────────────────────────────────────────────

def run(args):
    video_files = collect_video_files(args)
    scene_id    = args.scene_id
    target_fps  = args.fps
    loop_count  = args.loop_count   # -1 = 无限
    is_sandbox  = args.sandbox
    factory     = args.factory
    dry_run     = args.dry_run

    topic      = get_topic(is_sandbox)
    mode_label = "SANDBOX" if is_sandbox else "PRODUCTION"
    loop_label = "无限循环" if loop_count == -1 else f"循环 {loop_count} 轮"
    src_label  = video_files[0] if len(video_files) == 1 else \
                 f"{args.video_dir}  （共 {len(video_files)} 个文件）"

    print(f"""
╔══════════════════════════════════════════════════════════╗
║         天柱·天镜 — 视频帧注入 Kafka 工具                   ║
╚══════════════════════════════════════════════════════════╝
  视频来源  : {src_label}
  场景 ID   : {scene_id}
  注入帧率  : {target_fps} fps
  循环模式  : {loop_label}
  推理模式  : {mode_label}  (is_sandbox={is_sandbox})
  Kafka 主题: {topic}
  MinIO 桶  : {FRAMES_BUCKET}
  试运行    : {'是（不发 Kafka/MinIO）' if dry_run else '否'}
""")

    if len(video_files) > 1:
        print("  [文件列表]")
        for i, f in enumerate(video_files, 1):
            print(f"    {i:>3}. {os.path.basename(f)}")
        print()

    if is_sandbox:
        print("  [安全提示] is_sandbox=true：告警判定引擎将拦截所有告警，不触发外部推送")
    print("  按 Ctrl+C 停止\n")

    # 初始化连接
    minio_client = None
    producer     = None
    if not dry_run:
        print("[初始化] 连接 MinIO ...", end="", flush=True)
        minio_client = make_minio_client()
        ensure_bucket(minio_client, FRAMES_BUCKET)
        print(" OK")

        print("[初始化] 连接 Kafka ...", end="", flush=True)
        producer = make_kafka_producer()
        print(" OK\n")

    # 每帧注入后等待的时间（秒），控制实际注入速率
    inject_interval = 1.0 / target_fps

    date_str       = datetime.utcnow().strftime("%Y-%m")
    run_id         = uuid.uuid4().hex[:8]   # 本次运行唯一标识，避免帧ID跨循环冲突
    loop_index     = 0
    total_injected = 0

    try:
        while True:
            if loop_count != -1 and loop_index >= loop_count:
                print(f"\n[完成] 已循环 {loop_index} 轮，共注入 {total_injected} 帧，退出。")
                break

            loop_index += 1
            loop_label_str = f"第 {loop_index} 轮" + ("" if loop_count == -1 else f"/{loop_count}")
            print(f"[循环 {loop_label_str}] 开始 ─── {datetime.now().strftime('%H:%M:%S')}")

            round_injected = 0

            # ── 遍历本轮所有视频文件 ───────────────────────────────────────────
            for file_idx, video_path in enumerate(video_files, 1):
                file_label = os.path.basename(video_path)
                print(f"  [文件 {file_idx}/{len(video_files)}] {file_label}")

                cap = cv2.VideoCapture(video_path)
                if not cap.isOpened():
                    print(f"    [警告] 无法打开视频，跳过: {video_path}")
                    continue

                # 每个视频单独读取元信息（分辨率/fps 可能不同）
                source_fps   = cap.get(cv2.CAP_PROP_FPS) or 25
                total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
                width        = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
                height       = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
                duration_sec = total_frames / source_fps if source_fps > 0 else 0
                frame_step   = max(1, round(source_fps / target_fps))

                print(f"    {width}×{height}  原始 {source_fps:.1f}fps  "
                      f"总帧数 {total_frames}  时长 {duration_sec:.1f}s  "
                      f"抽帧步长 {frame_step}")

                frame_num        = 0
                file_injected    = 0

                while True:
                    ret, frame = cap.read()
                    if not ret:
                        break   # 当前视频读完，继续下一个文件

                    if frame_num % frame_step != 0:
                        frame_num += 1
                        continue

                    t_start = time.perf_counter()

                    # 帧 ID：包含运行ID+循环轮次+文件索引+帧序号，全局唯一
                    frame_id     = f"{run_id}_L{loop_index:04d}_V{file_idx:03d}_F{frame_num:06d}"
                    timestamp_ms = int(time.time() * 1000)

                    # JPEG 编码
                    success, buf = cv2.imencode(".jpg", frame,
                                                [cv2.IMWRITE_JPEG_QUALITY, 85])
                    if not success:
                        frame_num += 1
                        continue
                    jpeg_bytes = buf.tobytes()

                    image_url = f"minio://{FRAMES_BUCKET}/{factory}/{scene_id}/REALTIME/{frame_id}.jpg"

                    if not dry_run:
                        # 上传帧到 MinIO（格式：CLAUDE.md §7.3）
                        object_path = f"{factory}/{scene_id}/{date_str}/{frame_id}.jpg"
                        try:
                            image_url = upload_frame(minio_client, FRAMES_BUCKET,
                                                     object_path, jpeg_bytes)
                        except S3Error as e:
                            print(f"    [警告] MinIO 上传失败 frame_id={frame_id}: {e}")

                        # 发布 Kafka 消息
                        msg = make_frame_message(
                            scene_id=scene_id,
                            frame_id=frame_id,
                            image_url=image_url,
                            width=width,
                            height=height,
                            timestamp_ms=timestamp_ms,
                            is_sandbox=is_sandbox,
                        )
                        producer.send(topic, key=scene_id, value=msg)

                    file_injected  += 1
                    round_injected += 1
                    total_injected += 1

                    # 进度每 50 帧打印一次
                    if file_injected % 50 == 0:
                        elapsed = time.perf_counter() - t_start
                        print(f"    → 本文件 {file_injected} 帧  "
                              f"本轮 {round_injected} 帧  "
                              f"累计 {total_injected} 帧  "
                              f"当前帧耗时 {elapsed*1000:.0f}ms")

                    frame_num += 1

                    # 限速：按目标帧率控制注入速度（避免 Kafka 过载）
                    elapsed = time.perf_counter() - t_start
                    wait    = inject_interval - elapsed
                    if wait > 0:
                        time.sleep(wait)

                cap.release()
                print(f"    [完成] 本文件注入 {file_injected} 帧")

            print(f"  [本轮完成] 本轮注入 {round_injected} 帧  累计 {total_injected} 帧")

    except KeyboardInterrupt:
        print(f"\n[停止] 用户中断  累计注入 {total_injected} 帧")
    finally:
        if producer:
            producer.flush()
            producer.close()
            print("[Kafka] 已关闭")

# ─── 入口 ────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    args = parse_args()
    run(args)
