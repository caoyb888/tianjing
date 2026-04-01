"""
MinIO Bucket 初始化脚本
依赖：pip install minio
"""
import os
import sys
from minio import Minio
from minio.error import S3Error

endpoint  = os.environ.get("MINIO_ENDPOINT", "minio:9000")
access    = os.environ.get("MINIO_ROOT_USER", "minioadmin")
secret    = os.environ.get("MINIO_ROOT_PASSWORD", "minioadmin123")

buckets = [
    "tianjing-frames-prod",
    "tianjing-frames-sandbox",
    "tianjing-models-prod",
    "tianjing-models-staging",
    "tianjing-datasets",
    "tianjing-exports",
    "tianjing-lab-video",
    "tianjing-sim-temp",
]

client = Minio(endpoint, access_key=access, secret_key=secret, secure=False)

for bucket in buckets:
    try:
        if not client.bucket_exists(bucket):
            client.make_bucket(bucket)
            print(f"  created : {bucket}")
        else:
            print(f"  exists  : {bucket}")
    except S3Error as e:
        print(f"  ERROR   : {bucket} — {e}", file=sys.stderr)
        sys.exit(1)

print(">>> MinIO Buckets 初始化完成 ✓")
