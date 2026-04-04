# MLflow 使用说明

> **适用范围**：天柱·天镜工业视觉 AI 推理平台 — 训练环境与模型管理流程
> **版本要求**：MLflow 2.11.x（见 CLAUDE.md §2.3）
> **编写日期**：2026-04-04

---

## 1. MLflow 是什么

MLflow 是一个开源的机器学习生命周期管理平台，核心功能有四块：

| 模块 | 作用 |
|---|---|
| **Tracking** | 记录每次训练实验的超参数、指标（loss、mAP）、代码版本 |
| **Model Registry** | 模型版本注册中心，管理 Staging → Production 状态 |
| **Projects** | 打包训练代码，保证可重现 |
| **Artifacts** | 存储模型文件、评估图表等产物 |

---

## 2. 天镜平台中的使用方式

### 2.1 训练阶段（`training/` 目录）

训练脚本在训练环境运行，向 MLflow Tracking Server 汇报实验数据：

```python
import mlflow

with mlflow.start_run() as run:
    # 记录超参数
    mlflow.log_params({"epochs": 100, "lr": 0.001, "batch_size": 16})

    # 训练中记录指标
    for epoch in range(epochs):
        mlflow.log_metrics({"map50": map50, "loss": loss}, step=epoch)

    # 训练完成后注册模型到 MLflow Model Registry
    mlflow.pytorch.log_model(model, "model", registered_model_name="ATOM-DETECT-YOLO-V1")

    # 关键：保存 run_id，后续平台接口需要用
    print(f"MLflow Run ID: {run.info.run_id}")
```

### 2.2 模型注册到天镜平台

训练完成后，通过 API 将 MLflow Run 与天镜模型版本关联，创建 `model_version` 记录：

```json
POST /api/v1/models
{
  "plugin_id": "ATOM-DETECT-YOLO-V1",
  "mlflow_run_id": "abc123def456",
  "model_artifact_url": "models:/ATOM-DETECT-YOLO-V1/1",
  "training_job_id": "JOB-20260401-001"
}
```

这两个字段存入 `model_version` 表，作为训练溯源依据：

| 字段 | DB 列名 | 说明 |
|---|---|---|
| `mlflowRunId` | `mlflow_run_id` | MLflow 实验运行 ID，用于追溯训练过程 |
| `mlflowModelUri` | `mlflow_model_uri` | MLflow 模型 URI，推理服务加载模型时使用 |

### 2.3 审核流转链路

```
MLflow Model Registry（训练环境）
  ↓ POST /api/v1/models
model_version 表（状态：STAGING）
  ↓ POST /api/v1/models/{version_id}/submit
状态变更为 REVIEWING
  ↓ POST /api/v1/models/{version_id}/approve（四眼原则：审核人 ≠ 提交人）
状态变更为 PRODUCTION，旧版本自动 DEPRECATED（蓝绿切换）
  ↓
推理服务通过 mlflow_model_uri 加载模型文件
```

### 2.4 推理服务加载模型

`inference/model-loader/` 服务读取 `mlflow_model_uri`，从 MLflow Artifacts 或 Harbor 拉取模型文件：

```python
import mlflow.pytorch

# mlflow_model_uri 形如 "models:/ATOM-DETECT-YOLO-V1/Production"
model = mlflow.pytorch.load_model(model_version.mlflow_model_uri)
```

---

## 3. 模型版本管理 UI

平台管理后台的"模型版本管理"页面（路径：`/models`）展示的是从 MLflow 注册进来、经过天镜审核流水线的模型版本记录。

主要操作：

| 操作 | 入口 | 所需角色 |
|---|---|---|
| 提交新模型 | 列表页"提交新模型"按钮 | ADMIN / SCENE_EDITOR |
| 提交审核 | 详情页"提交审核"按钮（状态为 STAGING 或 SANDBOX_VALIDATING 时可用） | 任意登录用户 |
| 审批通过 / 拒绝 | 详情页审批按钮（状态为 REVIEWING 时可用，且当前用户不能是提交人） | ADMIN / MODEL_REVIEWER |
| 查看 Sandbox 报告 | 详情页"查看 Sandbox 报告"链接 | 任意登录用户 |

---

## 4. 关键约束

- **训推分离**：MLflow Tracking Server 和训练 GPU 仅部署在 `training` 命名空间，生产推理服务不得直接访问 MLflow API
- **禁止手动复制模型文件**：模型从 MLflow 到生产必须经过 Harbor staging → Sandbox 验证 → 人工审核 → CI/CD 流水线，任何绕过均为 P0 安全红线（见 CLAUDE.md §15）
- **可追溯性**：每个生产模型都能通过 `mlflow_run_id` 追溯到对应的训练实验（数据集版本、超参数、评估指标）
- **MLflow 版本**：强制使用 2.11.x，禁止升级到不兼容版本

---

## 5. 环境变量配置

```bash
# MLflow Tracking Server 地址（训练环境）
TIANJING_MLFLOW_TRACKING_URI=http://mlflow:5000
```

该变量仅在训练环境服务中使用，生产环境服务不得配置此变量。
