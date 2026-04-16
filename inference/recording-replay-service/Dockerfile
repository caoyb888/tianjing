FROM python:3.11.8-slim-bookworm

# 非 root 用户运行（规范：CLAUDE.md §13.3）
RUN useradd -m -u 1000 tianjing
WORKDIR /app

# 安装系统依赖（OpenCV headless 需要 libgl1）
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 libsm6 libxext6 libxrender-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY src/ ./src/
RUN chown -R tianjing:tianjing /app

USER tianjing
EXPOSE 8091

CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8091", "--workers", "1"]
