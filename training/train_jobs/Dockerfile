# 天柱·天镜 — YOLOv8n CPU 训练镜像
# 用途：Sprint 3 测试环境 docker run 方式启动训练作业
# 构建：docker build -t tianjing/yolo-trainer:cpu-latest training/train_jobs/
# 运行：由 DockerTrainJobLauncher 自动调用，无需手动执行

FROM python:3.11-slim

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 系统依赖（OpenCV 无头模式依赖的最小 so 库）
RUN apt-get update && apt-get install -y --no-install-recommends \
        libglib2.0-0 \
        libgomp1 \
        libgl1 \
    && rm -rf /var/lib/apt/lists/*

# 非 root 用户运行
RUN useradd -m -u 1000 tianjing
WORKDIR /app

# 安装 Python 依赖（分两层：依赖层 + 脚本层，加速重复构建）
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir --timeout 120 -r requirements.txt

# 预下载 YOLOv8n 预训练权重（构建时缓存，避免训练时联网）
RUN python -c "from ultralytics import YOLO; YOLO('yolov8n.pt')"

# 预置 Ultralytics 字体（从构建上下文 COPY，避免训练时从 ultralytics.com 下载阻塞）
COPY Arial.Unicode.ttf /home/tianjing/.config/Ultralytics/Arial.Unicode.ttf

# 复制训练脚本
COPY train_yolo_cpu.py /app/train_yolo_cpu.py
RUN chown -R tianjing:tianjing /app

# 切换到非 root 用户
USER tianjing

# 环境变量默认值（真实值由 DockerTrainJobLauncher 通过 -e 注入）
ENV JOB_ID=""
ENV PLUGIN_ID=""
ENV DATASET_VERSION_ID=""
ENV TRAIN_CONFIG_JSON="{}"
ENV MINIO_ENDPOINT="localhost:9000"
ENV MINIO_ACCESS_KEY="minioadmin"
ENV MINIO_SECRET_KEY="minioadmin"
ENV MLFLOW_TRACKING_URI="http://localhost:5000"
ENV PLATFORM_CALLBACK_URL="http://host.docker.internal:8089"
ENV MODEL_REGISTER_URL="http://host.docker.internal:8086"

CMD ["python", "/app/train_yolo_cpu.py"]
