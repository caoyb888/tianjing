FROM python:3.11.8-slim-bookworm

# 非 root 用户运行（规范：CLAUDE.md §13.3）
RUN useradd -m -u 1000 tianjing
WORKDIR /app

# 安装系统依赖（OpenCV headless 需要 libglib2.0-0）
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 libsm6 libxext6 libxrender-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 模型目录（挂载点，不随镜像打包）
RUN mkdir -p /models && chown tianjing:tianjing /models

COPY src/ ./src/
RUN chown -R tianjing:tianjing /app

USER tianjing
EXPOSE 8092

CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8092", "--workers", "1"]
