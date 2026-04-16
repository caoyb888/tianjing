#!/usr/bin/env python3
"""
YOLOv8 ONNX → TensorRT FP16 Engine 转换工具
Sprint: GPU-06（V3.0 阶段二）

功能：
  将 ONNX 模型转换为 TensorRT FP16 Engine，
  转换完成后上传至 MinIO tianjing-models-prod/ 存储。

验收标准（GPU-06）：
  - 使用 trtexec 将 ONNX 转换为 TensorRT FP16 Engine
  - 转换后模型保存至 MinIO tianjing-models-prod/
  - 单帧推理延迟 P95 ≤20ms（比 ONNX Runtime 再提速 20%+）

前置条件：
  - TensorRT 已安装（nvcr.io/nvidia/tensorrt:23.12-py3 镜像内置）
  - ONNX 模型文件存在于 ONNX_MODEL_PATH

用法：
  # 默认配置（输入 /models/yolov8n.onnx，输出 /models/yolov8n_fp16.trt）
  python3 scripts/convert_to_tensorrt.py

  # 指定路径
  python3 scripts/convert_to_tensorrt.py \
    --onnx /models/yolov8n.onnx \
    --output /models/yolov8n_fp16.trt \
    --upload

  # 同时上传至 MinIO（需设置 TIANJING_MINIO_ENDPOINT 等环境变量）
  TIANJING_MINIO_ENDPOINT=http://localhost:9000 \
  python3 scripts/convert_to_tensorrt.py --upload
"""

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

# ─── 颜色输出 ─────────────────────────────────────────────────────────────────
GREEN = "\033[0;32m"; RED = "\033[0;31m"; YELLOW = "\033[1;33m"; RESET = "\033[0m"

def log_info(msg):  print(f"{GREEN}[INFO]{RESET}  {msg}")
def log_warn(msg):  print(f"{YELLOW}[WARN]{RESET}  {msg}")
def log_error(msg): print(f"{RED}[ERROR]{RESET} {msg}")


# ─── 参数解析 ─────────────────────────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(description="ONNX → TensorRT FP16 转换工具 — 天柱·天镜")
    p.add_argument("--onnx",      default="/models/yolov8n.onnx",
                   help="ONNX 模型输入路径（默认 /models/yolov8n.onnx）")
    p.add_argument("--output",    default="/models/yolov8n_fp16.trt",
                   help="TensorRT Engine 输出路径（默认 /models/yolov8n_fp16.trt）")
    p.add_argument("--workspace", type=int, default=4096,
                   help="TensorRT 最大工作空间 MiB（默认 4096 = 4GB）")
    p.add_argument("--batch",     type=int, default=1,
                   help="最大 batch size（默认 1）")
    p.add_argument("--upload",    action="store_true",
                   help="转换后上传至 MinIO tianjing-models-prod/")
    p.add_argument("--benchmark", action="store_true",
                   help="转换后执行延迟基准测试（1000 次推理）")
    return p.parse_args()


# ─── TensorRT 转换 ────────────────────────────────────────────────────────────

def convert_with_trtexec(onnx_path: str, output_path: str, workspace_mb: int, max_batch: int) -> bool:
    """
    使用 trtexec 执行 ONNX → TensorRT FP16 转换（CLAUDE.md §2.3）
    FP16 量化：--fp16
    """
    log_info(f"开始转换: {onnx_path} → {output_path}")
    log_info(f"配置: FP16 量化, 最大显存 {workspace_mb}MiB, batch={max_batch}")

    # trtexec 命令（CLAUDE.md §2.3：推理引擎 TensorRT 8.6.x，生产必须量化为 FP16 或 INT8）
    cmd = [
        "trtexec",
        f"--onnx={onnx_path}",
        f"--saveEngine={output_path}",
        "--fp16",                           # FP16 量化（CLAUDE.md §2.3）
        f"--workspace={workspace_mb}",
        f"--maxBatch={max_batch}",
        "--buildOnly",                       # 仅构建，不运行推理
        "--verbose",
        "--inputIOFormats=fp16:chw",
        "--outputIOFormats=fp16:chw",
    ]

    log_info(f"执行命令: {' '.join(cmd)}")

    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=600,   # 最多 10 分钟
        )
        if result.returncode == 0:
            size_mb = os.path.getsize(output_path) / (1024 * 1024)
            log_info(f"✓ 转换成功！Engine 文件大小: {size_mb:.1f}MiB")
            return True
        else:
            log_error(f"trtexec 返回非零退出码: {result.returncode}")
            log_error(f"stderr:\n{result.stderr[-3000:]}")
            return False
    except FileNotFoundError:
        log_error("trtexec 未找到，请确认 TensorRT 已安装（nvcr.io/nvidia/tensorrt:23.12-py3）")
        log_warn("在 Docker 容器内运行此脚本：")
        log_warn("  docker run --gpus all -v /models:/models \\")
        log_warn("    nvcr.io/nvidia/tensorrt:23.12-py3 \\")
        log_warn(f"    trtexec --onnx={onnx_path} --saveEngine={output_path} --fp16")
        return False
    except subprocess.TimeoutExpired:
        log_error("转换超时（>10分钟），请检查 GPU 状态")
        return False


def convert_with_python_api(onnx_path: str, output_path: str, workspace_mb: int) -> bool:
    """
    使用 TensorRT Python API 执行转换（备选方案，trtexec 不可用时使用）
    """
    log_info("尝试使用 TensorRT Python API 转换...")
    try:
        import tensorrt as trt
        TRT_LOGGER = trt.Logger(trt.Logger.INFO)

        builder = trt.Builder(TRT_LOGGER)
        network = builder.create_network(1 << int(trt.NetworkDefinitionCreationFlag.EXPLICIT_BATCH))
        parser  = trt.OnnxParser(network, TRT_LOGGER)

        with open(onnx_path, "rb") as f:
            if not parser.parse(f.read()):
                for i in range(parser.num_errors):
                    log_error(parser.get_error(i))
                return False

        config = builder.create_builder_config()
        config.set_memory_pool_limit(trt.MemoryPoolType.WORKSPACE,
                                     workspace_mb * 1024 * 1024)
        config.set_flag(trt.BuilderFlag.FP16)   # CLAUDE.md §2.3：FP16 量化

        log_info("正在构建 TensorRT Engine（FP16 模式，V100 约需 3-10 分钟）...")
        t_start = time.perf_counter()
        engine_bytes = builder.build_serialized_network(network, config)

        if engine_bytes is None:
            log_error("Engine 构建失败")
            return False

        with open(output_path, "wb") as f:
            f.write(engine_bytes)

        elapsed = time.perf_counter() - t_start
        size_mb = os.path.getsize(output_path) / (1024 * 1024)
        log_info(f"✓ 转换成功！耗时 {elapsed:.1f}s，Engine 大小 {size_mb:.1f}MiB")
        return True

    except ImportError:
        log_error("TensorRT Python 包未安装，无法使用 Python API")
        return False


# ─── MinIO 上传 ───────────────────────────────────────────────────────────────

def upload_to_minio(engine_path: str, model_name: str = "yolov8n_fp16"):
    """上传 TensorRT Engine 至 MinIO tianjing-models-prod/（CLAUDE.md §7.3）"""
    try:
        from minio import Minio
        import io

        endpoint    = os.getenv("TIANJING_MINIO_ENDPOINT", "http://localhost:9000")
        access_key  = os.getenv("TIANJING_MINIO_ACCESS_KEY", "minioadmin")
        secret_key  = os.getenv("TIANJING_MINIO_SECRET_KEY", "minioadmin123")
        bucket      = "tianjing-models-prod"
        object_name = f"LOCAL-GPU-YOLO-V1/{model_name}.trt"

        ep = endpoint.replace("http://", "").replace("https://", "")
        client = Minio(ep, access_key=access_key, secret_key=secret_key,
                       secure=endpoint.startswith("https://"))

        if not client.bucket_exists(bucket):
            client.make_bucket(bucket)
            log_info(f"已创建 bucket: {bucket}")

        with open(engine_path, "rb") as f:
            data = f.read()

        client.put_object(
            bucket, object_name,
            data=io.BytesIO(data),
            length=len(data),
            content_type="application/octet-stream",
            metadata={
                "x-amz-meta-plugin-id":  "LOCAL-GPU-YOLO-V1",
                "x-amz-meta-precision":  "fp16",
                "x-amz-meta-framework":  "tensorrt",
            },
        )
        minio_url = f"minio://{bucket}/{object_name}"
        log_info(f"✓ 上传成功: {minio_url}")
        return minio_url
    except ImportError:
        log_error("minio 包未安装，无法上传")
        return None
    except Exception as e:
        log_error(f"上传失败: {e}")
        return None


# ─── 基准测试 ─────────────────────────────────────────────────────────────────

def benchmark_engine(engine_path: str, warmup: int = 10, iterations: int = 1000):
    """执行 TensorRT Engine 推理延迟基准测试（GPU-06 验收：P95 ≤20ms）"""
    try:
        import tensorrt as trt
        import pycuda.driver as cuda
        import pycuda.autoinit  # noqa
        import numpy as np

        TRT_LOGGER = trt.Logger(trt.Logger.WARNING)
        runtime    = trt.Runtime(TRT_LOGGER)

        with open(engine_path, "rb") as f:
            engine  = runtime.deserialize_cuda_engine(f.read())
        context = engine.create_execution_context()

        # 分配 IO 内存
        binding_shapes = [tuple(engine.get_binding_shape(i))
                          for i in range(engine.num_bindings)]
        d_inputs  = [cuda.mem_alloc(int(np.prod(s)) * 2)
                     for s in binding_shapes[:1]]   # FP16 = 2 bytes
        d_outputs = [cuda.mem_alloc(int(np.prod(s)) * 2)
                     for s in binding_shapes[1:]]
        bindings  = [int(b) for b in d_inputs + d_outputs]
        stream    = cuda.Stream()

        dummy_input = np.random.rand(*binding_shapes[0]).astype(np.float16)
        cuda.memcpy_htod_async(d_inputs[0], dummy_input, stream)

        # 预热
        log_info(f"预热 {warmup} 次...")
        for _ in range(warmup):
            context.execute_async_v2(bindings=bindings, stream_handle=stream.handle)
        stream.synchronize()

        # 正式测试
        log_info(f"基准测试 {iterations} 次推理...")
        latencies = []
        for _ in range(iterations):
            t0 = time.perf_counter()
            context.execute_async_v2(bindings=bindings, stream_handle=stream.handle)
            stream.synchronize()
            latencies.append((time.perf_counter() - t0) * 1000)

        latencies.sort()
        p50  = latencies[int(iterations * 0.50)]
        p95  = latencies[int(iterations * 0.95)]
        p99  = latencies[int(iterations * 0.99)]
        mean = sum(latencies) / len(latencies)

        print("\n" + "="*50)
        print("  TensorRT FP16 推理延迟基准（V100）")
        print("="*50)
        print(f"  迭代次数: {iterations}")
        print(f"  平均延迟: {mean:.2f}ms")
        print(f"  P50:      {p50:.2f}ms")
        print(f"  P95:      {p95:.2f}ms   {'✓ ≤20ms' if p95 <= 20 else '✗ >20ms（未达标）'}")
        print(f"  P99:      {p99:.2f}ms")
        print("="*50)

        if p95 <= 20:
            log_info("✓ GPU-06 验收通过：TensorRT FP16 P95 ≤20ms")
        else:
            log_warn(f"✗ GPU-06 验收未通过：P95={p95:.2f}ms >20ms，请检查模型大小或显存配置")

    except ImportError as e:
        log_warn(f"基准测试依赖未满足: {e}，跳过")


# ─── 主流程 ───────────────────────────────────────────────────────────────────

def main():
    args = parse_args()

    print("""
╔══════════════════════════════════════════════════════════╗
║    天柱·天镜 — ONNX → TensorRT FP16 转换工具              ║
╚══════════════════════════════════════════════════════════╝""")

    # 检查输入文件
    if not os.path.exists(args.onnx):
        log_error(f"ONNX 模型不存在: {args.onnx}")
        log_warn("请先下载模型：python3 -c \"from ultralytics import YOLO; YOLO('yolov8n.pt').export(format='onnx')\"")
        sys.exit(1)

    # 确保输出目录存在
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)

    log_info(f"ONNX 输入: {args.onnx} ({os.path.getsize(args.onnx) / 1024 / 1024:.1f}MiB)")
    log_info(f"TRT 输出:  {args.output}")
    log_info(f"精度:      FP16（CLAUDE.md §2.3 生产要求）")
    log_info(f"显存配额:  {args.workspace}MiB")

    # 执行转换：先尝试 trtexec，失败则回退 Python API
    success = convert_with_trtexec(args.onnx, args.output, args.workspace, args.batch)
    if not success:
        log_warn("trtexec 转换失败，尝试 TensorRT Python API...")
        success = convert_with_python_api(args.onnx, args.output, args.workspace)

    if not success:
        log_error("两种转换方式均失败，请在 TensorRT 容器内执行本脚本")
        log_warn("参考命令：")
        log_warn("  docker run --gpus all --rm -v /models:/models \\")
        log_warn("    nvcr.io/nvidia/tensorrt:23.12-py3 \\")
        log_warn(f"    trtexec --onnx={args.onnx} --saveEngine={args.output} --fp16 --workspace={args.workspace}")
        sys.exit(1)

    # MinIO 上传
    if args.upload:
        log_info("上传 TensorRT Engine 至 MinIO...")
        minio_url = upload_to_minio(args.output)
        if minio_url:
            log_info(f"MinIO 路径: {minio_url}")

    # 基准测试
    if args.benchmark:
        benchmark_engine(args.output)

    log_info(f"\n完成！TensorRT Engine 保存至: {args.output}")
    log_info("更新 gpu-infer-service 环境变量 INFER_BACKEND=tensorrt 即可切换后端（GPU-07）")


if __name__ == "__main__":
    main()
