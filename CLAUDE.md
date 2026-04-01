# CLAUDE.md — 天柱·天镜 工业视觉 AI 推理平台
## 项目开发行为规范

> **版本**：V1.1 · **日期**：2026-04-01
> **适用范围**：本规范约束所有参与本项目开发的 AI 辅助编码行为（Claude Code 及同类工具），
> 所有代码生成、重构、调试均须严格遵守本文件中的技术选型、架构约束与安全边界。

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈强制约束](#2-技术栈强制约束)
3. [工程目录结构](#3-工程目录结构)
4. [微服务命名与职责边界](#4-微服务命名与职责边界)
5. [核心开发规范](#5-核心开发规范)
6. [算法插件接口规范](#6-算法插件接口规范)
7. [数据库与存储规范](#7-数据库与存储规范)
8. [消息总线规范](#8-消息总线规范)
9. [API 接口规范](#9-api-接口规范)
10. [安全与隔离规范](#10-安全与隔离规范)
11. [Sandbox 实验室规范](#11-sandbox-实验室规范)
12. [测试规范](#12-测试规范)
13. [CI/CD 与部署规范](#13-cicd-与部署规范)
14. [日志与可观测性规范](#14-日志与可观测性规范)
15. [禁止行为清单](#15-禁止行为清单)

---

## 1. 项目概述

**项目全称**：天柱·天镜 · 工业视觉 AI 推理平台（TianJing Industrial Vision Intelligence Platform）

**核心定位**：面向河北天柱钢铁五大厂部（球团、烧结、炼钢、型钢、带钢）的统一视觉 AI 推理平台，覆盖 16 个工业视觉检测场景，具备低代码编排、原子算法复用、算法实验室（Sandbox）、感知健康监控、模型漂移自愈等核心能力。

**关键约束原则**：
- **生产优先**：任何代码变更不得降低生产推理服务的稳定性与实时性
- **训推分离**：训练环境与生产环境在网络、存储、计算资源上严格隔离
- **Sandbox 隔离**：实验室推理结果绝不触发生产告警与 PLC/DCS 控制指令
- **配置驱动**：业务逻辑变更通过配置中心下发，不依赖代码重部署
- **插件标准化**：所有算法组件必须遵循 `Image In → JSON Out` 标准接口协议

---

## 2. 技术栈强制约束

> 以下技术选型为项目强制指定，AI 辅助编码时不得擅自替换为其他技术。
> 如评估有更优替代方案，须提出书面建议并经技术评审后方可变更。

### 2.1 后端服务

| 层次 | 技术选型 | 版本要求 | 说明 |
|---|---|---|---|
| Web 框架 | **Spring Boot** | 3.2.x | 微服务后端，禁止使用 Quarkus / Micronaut 替代 |
| 编程语言（后端） | **Java** | 21 LTS | 使用虚拟线程（Virtual Threads）特性 |
| 编程语言（算法） | **Python** | 3.11+ | 仅用于算法服务，禁止混用于后端业务逻辑 |
| 服务间通信 | **gRPC** | 1.62+ | 高性能内部服务调用，REST 仅用于外部接口 |
| 配置中心 | **Nacos** | 2.3.x | 动态配置与服务注册，禁止使用 Consul / etcd |
| 消息总线 | **Apache Kafka** | 3.6.x | 视频帧分发与结果传递，禁止使用 RabbitMQ / RocketMQ |

### 2.2 前端

| 层次 | 技术选型 | 版本要求 | 说明 |
|---|---|---|---|
| 框架 | **Vue 3** | 3.4.x | Composition API，禁止使用 Options API 新增代码 |
| 构建工具 | **Vite** | 5.x | 禁止使用 Webpack |
| 状态管理 | **Pinia** | 2.x | 禁止使用 Vuex |
| UI 组件库 | **Element Plus** | 2.6.x | 禁止引入其他 UI 库 |
| 低代码编排器 | **Vue Flow** | 1.x | 流程节点画布，禁止使用 AntV X6 / jsPlumb |
| 图表 | **ECharts** | 5.x | 禁止使用 Chart.js / Highcharts |
| HTTP 客户端 | **Axios** | 1.x | 统一封装，禁止裸调 fetch |

### 2.3 AI / 算法层

| 层次 | 技术选型 | 版本要求 | 说明 |
|---|---|---|---|
| 训练框架 | **PyTorch** | 2.2.x | 禁止使用 TensorFlow / JAX |
| 目标检测 | **MMDetection** | 3.x | 基于 OpenMMLab 生态 |
| 推理引擎（GPU） | **TensorRT** | 8.6.x | 生产 GPU 推理，必须量化为 FP16 或 INT8 |
| 推理引擎（通用） | **ONNX Runtime** | 1.17.x | CPU/边缘端推理，模型必须先导出为 ONNX 格式 |
| 边缘推理 | **NVIDIA Jetson** | JetPack 6.x | 或海思 3559，使用厂商 SDK 适配层 |
| 图像处理 | **OpenCV** | 4.9.x | 禁止使用 PIL/Pillow 处理大批量图像 |
| 模型管理 | **MLflow** | 2.11.x | 实验记录与模型注册，禁止手动管理模型文件 |
| 镜像仓库 | **Harbor** | 2.10.x | 模型镜像与容器镜像统一管理 |

### 2.4 数据存储

| 数据类型 | 技术选型 | 版本要求 | 说明 |
|---|---|---|---|
| 关系型数据 | **PostgreSQL** | 16.x | 配置数据、元数据、业务数据，禁止使用 MySQL |
| 时序数据 | **TDengine** | 3.x | 推理结果、告警记录，高频写入，禁止写入 PostgreSQL |
| 对象存储 | **MinIO** | RELEASE.2024+ | 原始图像、视频、模型文件，S3 兼容协议 |
| 缓存 | **Redis** | 7.x | 会话、热点配置缓存，禁止用作持久化存储 |
| 搜索 | **Elasticsearch** | 8.x | 告警日志检索，仅在有全文检索需求时使用 |

### 2.5 基础设施

| 层次 | 技术选型 | 版本要求 | 说明 |
|---|---|---|---|
| 容器编排 | **Kubernetes** | 1.29.x | 生产/训练/Sandbox 三命名空间隔离 |
| GPU 调度 | **GPU Operator** | 23.9.x | NVIDIA 官方 Operator |
| 服务网格 | **Istio** | 1.20.x | 服务间流量管理与可观测性 |
| 容器运行时 | **containerd** | 1.7.x | 禁止使用 Docker 作为生产运行时 |
| 基础设施即代码 | **Helm** | 3.14.x | 所有服务部署必须有 Helm Chart |
| 监控 | **Prometheus + Grafana** | 最新稳定版 | 禁止使用其他监控方案 |
| 日志 | **Loki + Promtail** | 最新稳定版 | 禁止使用 ELK 全量日志 |
| 链路追踪 | **Jaeger** | 1.55.x | 分布式链路追踪 |
| 报警推送 | **MQTT（EMQX）** | 5.x | 工业互联网平台对接，禁止使用其他 MQTT Broker |

---

## 3. 工程目录结构

```
tianjing-platform/
├── apps/                          # 应用层（前端）
│   ├── admin-web/                 # 管理后台 Vue3 应用
│   │   ├── src/
│   │   │   ├── components/        # 通用组件
│   │   │   ├── pages/             # 页面
│   │   │   ├── stores/            # Pinia 状态
│   │   │   ├── composables/       # 组合式函数
│   │   │   └── api/               # Axios 封装
│   │   └── vite.config.ts
│   └── monitor-screen/            # 可视化大屏应用
│
├── services/                      # 后端微服务（Java/Spring Boot）
│   ├── scene-config-service/      # 场景配置服务
│   ├── device-manage-service/     # 设备管理服务
│   ├── auth-service/              # 用户权限服务
│   ├── alarm-rule-service/        # 告警规则服务
│   ├── lowcode-workflow-service/  # 低代码编排服务
│   ├── calibration-service/       # 在线标定服务
│   ├── stream-ingest-service/     # 视频流接入服务
│   ├── frame-extract-service/     # 帧抽取服务
│   ├── preprocess-service/        # 预处理服务
│   ├── route-dispatch-service/    # 路由分发服务
│   ├── traffic-mirror-service/    # T型分流镜像服务
│   ├── health-monitor-service/    # 感知健康检测服务
│   ├── result-aggregate-service/  # 结果聚合服务
│   ├── alarm-judge-service/       # 告警判定服务（含Sandbox拦截器）
│   ├── notification-service/      # 推送通知服务
│   ├── history-replay-service/    # 历史回溯服务
│   ├── drift-monitor-service/     # 模型漂移监测服务
│   └── compare-dashboard-service/ # 对比分析看板服务
│
├── inference/                     # 推理服务（Python）
│   ├── quality-infer/             # 质检推理微服务
│   ├── equipment-infer/           # 设备监测推理微服务
│   ├── process-infer/             # 工艺参数推理微服务
│   ├── model-loader/              # 模型热加载服务
│   ├── sandbox-infer/             # Sandbox推理服务
│   └── atom-algorithm-store/      # 原子算法仓服务
│
├── algorithms/                    # 算法插件（Python，标准化接口）
│   ├── _base/                     # 抽象基类与接口定义
│   │   ├── base_plugin.py         # BaseAlgorithmPlugin 抽象类
│   │   └── schema.py              # 输入输出 Pydantic Schema
│   ├── atom_detect_yolo/          # 通用目标检测原子算法
│   ├── atom_segment_sam/          # 通用语义分割原子算法
│   ├── atom_classify_resnet/      # 通用图像分类器
│   ├── atom_measure_subpixel/     # 亚像素测量标定组件
│   ├── atom_enhance_dehaze/       # 去雾/增强预处理组件
│   └── task_heads/                # 场景任务头（轻量微调）
│       ├── grate_bar_defect/      # 篦条缺损检测头
│       ├── sideplate_deviation/   # 侧板偏移检测头
│       ├── steel_surface_defect/  # 钢材表面缺陷检测头
│       ├── billet_crack/          # 铸坯裂纹检测头
│       ├── material_level/        # 料面高度估计头
│       ├── feeder_state/          # 分料器状态分类头
│       ├── slag_density/          # 渣粒密度估计头
│       └── dimension_measure/     # 尺寸测量回归头
│
├── training/                      # 训练平台（仅训练环境部署）
│   ├── data_pipeline/             # 数据预处理流水线
│   ├── train_jobs/                # 训练作业配置
│   ├── eval/                      # 模型评估脚本
│   └── export/                    # 模型量化导出
│
├── deploy/                        # 部署配置
│   ├── helm/                      # Helm Charts
│   │   ├── production/            # 生产命名空间
│   │   ├── sandbox/               # Sandbox命名空间
│   │   └── training/              # 训练命名空间（严格隔离）
│   ├── k8s/                       # 原始 K8s manifests
│   └── docker/                    # Dockerfile
│
├── proto/                         # gRPC Protobuf 定义（跨服务共享）
│   ├── inference.proto
│   ├── alarm.proto
│   ├── scene.proto
│   └── health.proto
│
├── sdk/                           # 算法插件 SDK
│   └── tianjing-algo-sdk/         # 第三方算法接入标准 SDK
│
├── docs/                          # 文档
│   ├── api/                       # API 文档（OpenAPI 3.0）
│   ├── architecture/              # 架构决策记录（ADR）
│   └── runbooks/                  # 运维手册
│
├── scripts/                       # 运维脚本
├── .github/workflows/             # CI/CD Pipeline
└── CLAUDE.md                      # 本文件
```

---

## 4. 微服务命名与职责边界

### 4.1 服务命名规则

```
# 格式：{业务域}-{功能}-service
# 示例：scene-config-service, sandbox-infer-service

# Kubernetes 命名空间强制规定：
production    # 生产推理服务、告警服务、管理后台服务
sandbox       # Sandbox 推理服务（隔离，无告警权限）
training      # 训练服务（与生产网络物理隔离）
monitoring    # Prometheus / Grafana / Jaeger
middleware    # Kafka / Nacos / Redis / PostgreSQL / MinIO
```

### 4.2 服务职责边界（不可越界）

| 服务 | 允许 | 禁止 |
|---|---|---|
| `alarm-judge-service` | 判定报警级别、触发生产告警推送 | 直接调用推理服务；持久化原始图像 |
| `sandbox-infer-service` | 执行推理、写入实验室数据库 | **向任何外部系统推送告警**；访问生产数据库 |
| `traffic-mirror-service` | 复制 Kafka Topic 帧数据至 sandbox topic | 修改原始帧数据；影响生产 Topic |
| `drift-monitor-service` | 读取人工复核结果、计算精度曲线、触发重训练 | 直接修改生产模型；访问训练环境 GPU |
| `lowcode-workflow-service` | 生成场景配置 JSON、下发至路由服务 | 直接操作推理进程；绕过配置中心 |
| `health-monitor-service` | 读取视频帧、判断图像质量、推送维修工单 | 修改推理逻辑；影响正常推理流量 |
| 训练环境所有服务 | 访问训练数据仓库、训练 GPU 集群 | **访问生产网络任何资源**（光闸单向） |

---

## 5. 核心开发规范

### 5.1 Java 后端规范

```java
// 正确：使用 Record 定义 DTO
public record SceneConfigDTO(
    String sceneId,
    String factory,
    String category,
    AlgorithmConfig algorithm,
    AlarmConfig alarm
) {}

// 正确：使用虚拟线程处理视频流并发
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    scenes.forEach(scene -> executor.submit(() -> processScene(scene)));
}

// 正确：配置从 Nacos 读取，不硬编码
@Value("${tianjing.alarm.confirm-frames:3}")
private int confirmFrames;

// 禁止：硬编码生产环境地址
// private static final String KAFKA_BOOTSTRAP = "192.168.1.100:9092";

// 禁止：Service 层直接操作 HTTP，必须通过 gRPC 或 Kafka
// restTemplate.postForObject("http://infer-service/infer", request, Response.class);
```

### 5.2 Python 算法服务规范

```python
# 正确：所有算法必须继承 BaseAlgorithmPlugin
from tianjing_algo_sdk import BaseAlgorithmPlugin, InferInput, InferOutput

class YoloDetectPlugin(BaseAlgorithmPlugin):
    """通用目标检测原子算法 - 必须实现 infer() 方法"""

    plugin_id: str = "ATOM-DETECT-YOLO-V1"
    version: str = "1.2.0"

    def infer(self, input: InferInput) -> InferOutput:
        # 推理逻辑
        ...

# 正确：使用 Pydantic 严格定义输入输出
from pydantic import BaseModel, Field

class BoundingBox(BaseModel):
    x1: float = Field(..., ge=0)
    y1: float = Field(..., ge=0)
    x2: float = Field(..., gt=0)
    y2: float = Field(..., gt=0)

# 正确：推理耗时必须记录
import time
start = time.perf_counter()
result = model(image)
inference_ms = (time.perf_counter() - start) * 1000  # 必须写入输出

# 禁止：使用全局变量缓存模型（必须通过 ModelLoader 统一管理）
# global MODEL
# MODEL = torch.load("model.pt")

# 禁止：算法插件内直接读写数据库或发送 HTTP 请求
# import psycopg2   # 算法插件禁止数据库操作
# import requests   # 算法插件禁止 HTTP 调用
```

### 5.3 Vue3 前端规范

```typescript
// 正确：使用 Composition API + TypeScript
import { ref, computed } from 'vue'
import type { SceneConfig } from '@/types/scene'

const sceneList = ref<SceneConfig[]>([])
const activeCount = computed(() => sceneList.value.filter(s => s.active).length)

// 正确：API 调用统一走封装的 composable
import { useSceneApi } from '@/composables/useSceneApi'
const { fetchScenes, updateScene } = useSceneApi()

// 正确：告警级别必须使用枚举常量
import { AlarmLevel } from '@/constants/alarm'
// AlarmLevel.INFO | AlarmLevel.WARNING | AlarmLevel.CRITICAL

// 禁止：直接在组件中调用 axios
// import axios from 'axios'
// axios.get('/api/scenes')   // 必须通过封装的 composable

// 禁止：在低代码编排器中使用 eval() 执行用户输入的逻辑
// eval(userInputLogic)   // 严格禁止，存在安全风险

// 禁止：Sandbox 推理结果用实线标注框展示（只能用虚线 stroke-dasharray）
```

### 5.4 通用规范

```
命名规范：
- 类名：PascalCase（Java/TypeScript）
- 函数/变量名：camelCase（Java/TypeScript）/ snake_case（Python）
- 常量：UPPER_SNAKE_CASE
- Kafka Topic：tianjing.{domain}.{action}（全小写，点号分隔）
  示例：tianjing.frame.production、tianjing.frame.sandbox、tianjing.alarm.critical
- Kubernetes 资源名：tianjing-{service-name}（kebab-case）
- 数据库表名：snake_case，以业务域为前缀
  示例：scene_config、alarm_record、drift_metric

注释规范：
- 所有算法插件必须包含：算法原理、适用场景、精度指标、已知局限
- 生产/Sandbox 分支代码必须显式注释区分，禁止隐式判断
- 安全敏感代码（光闸传输、告警推送）必须有安全审查注释标记 // SECURITY:
- 统一使用中文注释，禁止中英文混用
```

---

## 6. 算法插件接口规范

> 所有算法（自研或第三方）必须严格遵守以下标准接口，方可接入平台。

### 6.1 标准输入输出 Schema

```python
# algorithms/_base/schema.py

from pydantic import BaseModel, Field
from typing import Optional
import numpy as np

class ROI(BaseModel):
    x: int = Field(0, ge=0)
    y: int = Field(0, ge=0)
    w: int = Field(..., gt=0)
    h: int = Field(..., gt=0)

class InferParams(BaseModel):
    conf_threshold: float = Field(0.85, ge=0.0, le=1.0)
    iou_threshold: float  = Field(0.45, ge=0.0, le=1.0)
    extra: dict = Field(default_factory=dict)  # 算法特定扩展参数

class InferInput(BaseModel):
    image: np.ndarray        # BGR格式，shape=(H, W, 3)
    roi: ROI
    params: InferParams
    scene_id: str
    frame_id: str
    timestamp_ms: int
    is_sandbox: bool = False # 是否来自 Sandbox 流，算法内部禁止根据此字段改变推理逻辑

class Detection(BaseModel):
    class_id: int
    class_name: str
    confidence: float = Field(..., ge=0.0, le=1.0)
    bbox: dict               # {x1, y1, x2, y2}
    measurement: Optional[dict] = None  # {value, unit} 仅测量类算法填写

class InferOutput(BaseModel):
    plugin_id: str
    version: str
    scene_id: str
    frame_id: str
    detections: list[Detection]
    inference_time_ms: float # 必填，用于性能监控
    timestamp_ms: int
    extra: dict = Field(default_factory=dict)
```

### 6.2 抽象基类

```python
# algorithms/_base/base_plugin.py

from abc import ABC, abstractmethod
from .schema import InferInput, InferOutput

class BaseAlgorithmPlugin(ABC):

    plugin_id: str   # 格式：ATOM-{TYPE}-{NAME}-V{N}
    version: str     # 语义化版本，如 "1.2.0"

    @abstractmethod
    def load_model(self, model_path: str) -> None:
        """加载模型权重，由 ModelLoader 服务调用，禁止在 infer() 中调用"""
        ...

    @abstractmethod
    def infer(self, input: InferInput) -> InferOutput:
        """
        核心推理方法。约束：
        - 必须是纯函数，禁止维护推理状态
        - 禁止在此方法内访问数据库、网络、文件系统
        - 禁止根据 input.is_sandbox 改变推理逻辑
        - 推理耗时必须记录在 InferOutput.inference_time_ms
        """
        ...

    @abstractmethod
    def get_metadata(self) -> dict:
        """返回算法元信息：名称、版本、适用场景、精度指标、硬件要求"""
        ...
```

### 6.3 插件注册元信息

```yaml
# 每个算法插件根目录必须有 plugin.yaml
plugin_id: "ATOM-DETECT-YOLO-V1"
name: "通用目标检测引擎"
version: "1.2.0"
type: "detection"         # detection | segmentation | classification | measurement | enhancement
backbone: "YOLOv9"
supported_scenes:
  - "链篦机侧板跑偏"
  - "烧结机壁条检测"
  - "台车篦条检测"
  - "铸坯表面缺陷"
hardware_requirements:
  min_gpu_vram_gb: 4
  supports_tensorrt: true
  supports_onnx: true
accuracy_metrics:
  map50: 0.91
  map50_95: 0.76
  inference_ms_gpu: 18
  inference_ms_cpu: 210
```

---

## 7. 数据库与存储规范

### 7.1 PostgreSQL 规范

```sql
-- 所有表必须有标准字段
CREATE TABLE scene_config (
    id          BIGSERIAL    PRIMARY KEY,
    scene_id    VARCHAR(64)  NOT NULL UNIQUE,
    factory     VARCHAR(32)  NOT NULL,
    category    VARCHAR(32)  NOT NULL,
    config_json JSONB        NOT NULL,
    is_active   BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(64)  NOT NULL,
    version     INTEGER      NOT NULL DEFAULT 1  -- 乐观锁版本号
);

-- 数据库分库规定：
-- tianjing_prod   ：生产库（场景配置、设备、告警规则、用户）
-- tianjing_train  ：训练库（仅训练环境可访问，数据集元数据）
-- tianjing_sandbox：Sandbox库（实验室推理结果，与生产库完全隔离）

-- 禁止：推理结果写入 PostgreSQL（必须写 TDengine）
-- 禁止：跨库 JOIN 查询
-- 禁止：在 Sandbox 库和生产库之间建立外键或视图
```

### 7.2 TDengine 时序数据规范

```sql
-- 推理结果超级表定义
CREATE STABLE infer_result (
    ts          TIMESTAMP,     -- 推理时间戳（毫秒精度）
    confidence  FLOAT,
    class_name  NCHAR(64),
    bbox_x1     INT,
    bbox_y1     INT,
    bbox_x2     INT,
    bbox_y2     INT,
    infer_ms    FLOAT,         -- 推理耗时（必填）
    is_anomaly  BOOL
) TAGS (
    scene_id    NCHAR(64),     -- 场景ID（分区标签）
    factory     NCHAR(32),     -- 厂部（分区标签）
    is_sandbox  BOOL           -- 是否Sandbox推理（必须标记）
);

-- 数据保留策略
-- 生产推理结果：保留 180 天
-- Sandbox 推理结果：保留 30 天
-- 感知健康指标：保留 365 天
```

### 7.3 MinIO 存储规范

```
Bucket 命名与权限规范：
tianjing-frames-prod        # 生产帧图像，推理服务只读，禁止训练服务访问
tianjing-frames-sandbox     # Sandbox帧图像，Sandbox服务读写
tianjing-models-prod        # 生产模型文件，推理服务只读
tianjing-models-staging     # 待审核模型，CI/CD 流水线写入
tianjing-datasets           # 训练数据集，仅训练环境可访问
tianjing-exports            # 导出报告，管理后台可读

图像命名规范：
{factory}/{scene_id}/{date}/{timestamp_ms}_{frame_id}.jpg
示例：sintering/SCENE-SINTER-005/2026-04/1743500000000_f00001.jpg

存储策略：
- 生产帧图像只保留异常帧 + 每小时采样帧（降低存储成本）
- 完整帧序列仅在 Sandbox 模式下临时保存，72 小时后自动清理
```

---

## 8. 消息总线规范

### 8.1 Kafka Topic 命名与分区

```
# Topic 命名：tianjing.{domain}.{subdomain}

视频帧：
tianjing.frame.production          # 生产推理帧（25fps，高优先级）
tianjing.frame.sandbox             # Sandbox推理帧（5fps，镜像降频）

推理结果：
tianjing.infer.result.production   # 生产推理结果
tianjing.infer.result.sandbox      # Sandbox推理结果（绝不与生产结果混合）

告警：
tianjing.alarm.critical            # CRITICAL级（消费者：notification-service）
tianjing.alarm.warning             # WARNING级
tianjing.alarm.info                # INFO级（仅持久化，不推送）

感知健康：
tianjing.health.camera             # 摄像头健康状态

模型漂移：
tianjing.drift.feedback            # 人工复核结果回传

# 分区规则：按 scene_id 哈希分区，确保同一场景的帧数据有序
```

### 8.2 消费者组规范

```
# 命名格式：{service-name}-{env}-cg
# 生产消费者组与 Sandbox 消费者组严格分开

正确示例（Sandbox 消费者只消费 sandbox topic）：
@KafkaListener(topics = "tianjing.frame.sandbox",
               groupId = "sandbox-infer-service-sandbox-cg")

禁止示例（Sandbox 服务不得订阅生产 topic）：
@KafkaListener(topics = "tianjing.frame.production",
               groupId = "sandbox-infer-service-prod-cg")
```

### 8.3 消息 Schema

```json
// 推理帧消息
{
  "frame_id": "f00001",
  "scene_id": "SCENE-SINTER-005",
  "timestamp_ms": 1743500000000,
  "is_sandbox": false,
  "image_url": "minio://tianjing-frames-prod/sintering/...",
  "image_width": 1920,
  "image_height": 1080,
  "roi": {"x": 0, "y": 0, "w": 1920, "h": 1080}
}

// 推理结果消息
{
  "frame_id": "f00001",
  "scene_id": "SCENE-SINTER-005",
  "plugin_id": "ATOM-DETECT-YOLO-V1",
  "is_sandbox": false,
  "detections": [],
  "inference_time_ms": 18.5,
  "timestamp_ms": 1743500000018
}
```

---

## 9. API 接口规范

### 9.1 REST API（外部接口）

```
URL 规范：
GET    /api/v1/scenes                    # 查询场景列表
POST   /api/v1/scenes                    # 新增场景
PUT    /api/v1/scenes/{scene_id}         # 更新场景配置
DELETE /api/v1/scenes/{scene_id}         # 删除场景
POST   /api/v1/scenes/{scene_id}/enable  # 启用场景
POST   /api/v1/scenes/{scene_id}/disable # 禁用场景

统一响应格式（禁止自定义格式）：
{
  "code": 0,
  "message": "success",
  "data": {},
  "trace_id": "abc123"
}

错误码规范（4位数字）：
1xxx：参数错误
2xxx：业务逻辑错误
3xxx：资源不存在
4xxx：权限错误
5xxx：系统内部错误

分页规范：
GET /api/v1/alarms?page=1&size=20&sort=created_at:desc
响应中包含：total, page, size, items
```

### 9.2 gRPC 内部接口

```protobuf
// proto/inference.proto
syntax = "proto3";
package tianjing.inference.v1;

service InferenceService {
  // 同步推理（仅用于低延迟场景，如飞剪 <10ms 要求）
  rpc InferSync(InferRequest) returns (InferResponse);
  // 异步推理状态查询
  rpc GetInferStatus(InferStatusRequest) returns (InferStatusResponse);
}

message InferRequest {
  string scene_id   = 1;
  string frame_id   = 2;
  bytes  image      = 3;
  bool   is_sandbox = 4;  // 必须传递，不得默认为 false
}
```

### 9.3 工业互联网平台对接接口

```json
// MQTT 告警上报 Topic：iiot/tianjing/alarm/{factory}
// QoS：1（至少一次，保证送达）
{
  "alarm_id": "ALM-20260415-001234",
  "scene_id": "SCENE-SINTER-005",
  "factory": "sintering",
  "alarm_level": "CRITICAL",
  "anomaly_type": "壁条脱落",
  "confidence": 0.93,
  "image_url": "https://minio.tianjing.internal/...",
  "timestamp_ms": 1743500000000,
  "is_sandbox": false
}
```

> **硬性规定**：`is_sandbox=true` 的消息绝不允许到达此接口。`alarm-judge-service` 的 Sandbox 拦截器必须在消息发布前校验，而非依赖下游过滤。

---

## 10. 安全与隔离规范

### 10.1 网络隔离规范

```yaml
# Kubernetes NetworkPolicy 示例（Sandbox 服务）
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: sandbox-infer-isolation
  namespace: sandbox
spec:
  podSelector:
    matchLabels:
      app: sandbox-infer-service
  policyTypes:
    - Ingress
    - Egress
  ingress:
    - from:
        - namespaceSelector:
            matchLabels:
              name: production
          podSelector:
            matchLabels:
              app: traffic-mirror-service
  egress:
    - to:
        - namespaceSelector:
            matchLabels:
              name: middleware
    # 严禁添加允许访问 production 命名空间其他服务的出口规则
    # 严禁添加允许访问工业互联网平台 MQTT Broker 的出口规则
```

### 10.2 数据安全规范

```
生产→训练数据流转规则（代码层面约束）：

1. 生产环境代码禁止直接连接训练环境的任何服务
2. 数据同步作业（每日凌晨执行）必须：
   a. 通过光闸传输，不走内部网络
   b. 传输前调用脱敏服务：去除摄像头标识、生产批次编号、时间戳精确到小时
   c. 传输完成后记录审计日志到 tianjing.audit.data_transfer Kafka Topic
3. 训练环境代码禁止出现生产数据库连接串
4. 模型文件从训练到生产的传递流程：
   - 训练环境推送至 Harbor（tianjing-models-staging）
   - 经 Sandbox 验证 + 人工审核 + CI/CD 流水线 三个门禁
   - 通过后由 CI/CD 推送至 tianjing-models-prod
   - 禁止任何人手动将模型文件复制到生产环境
```

### 10.3 认证与鉴权

```
服务间认证：Istio mTLS（必须开启，禁止关闭）
外部 API 认证：JWT（RS256 算法，禁止 HS256）
低代码编排器操作：需要 SCENE_EDITOR 角色
生产模型发布审核：需要 MODEL_REVIEWER 角色，且必须与提交者不是同一人（四眼原则）
Sandbox 开关：需要 SANDBOX_OPERATOR 角色

必须记录审计日志的操作：
- 场景上线/下线
- 告警规则变更
- 模型版本切换
- Sandbox 转正操作
- 数据脱敏同步
```

---

## 11. Sandbox 实验室规范

> Sandbox 是本平台的核心安全机制，以下规范具有最高约束力，任何违反均视为严重缺陷（P0）。

### 11.1 is_sandbox 标识传递规范

```
is_sandbox 标识必须在整个处理链路中原样透传，不得丢失或篡改：

视频帧采集
  → traffic-mirror-service（写入 sandbox topic，is_sandbox=true）
  → sandbox-infer-service（消费，is_sandbox=true）
  → InferOutput.is_sandbox=true
  → tianjing.infer.result.sandbox（is_sandbox=true）
  → alarm-judge-service（检查 is_sandbox，拦截，写实验室DB）
  → 绝不到达 notification-service

代码审查必查项：
1. alarm-judge-service 中 Sandbox 拦截器是否在推送前校验 is_sandbox
2. notification-service 是否有防御性校验拒绝 is_sandbox=true 的消息
3. TDengine 写入时 is_sandbox 标签是否正确设置
```

### 11.2 Sandbox 精度评估规范

```python
def calculate_daily_accuracy(scene_id: str, date: str) -> AccuracyMetric:
    """
    精度计算必须基于人工复核结果，而非模型自评。
    人工复核来源：工业互联网平台工单处置结果回传（tianjing.drift.feedback Topic）
    """
    production_results = get_production_infer_results(scene_id, date)
    human_feedbacks    = get_human_feedbacks(scene_id, date)

    tp = count_true_positives(production_results, human_feedbacks)
    fp = count_false_positives(production_results, human_feedbacks)
    fn = count_false_negatives(production_results, human_feedbacks)

    precision = tp / (tp + fp) if (tp + fp) > 0 else None
    recall    = tp / (tp + fn) if (tp + fn) > 0 else None

    # 精度连续 3 天低于阈值才触发重训练（防止偶发数据影响）
    if should_trigger_retrain(scene_id, precision, threshold=0.85, consecutive_days=3):
        trigger_retrain_job(scene_id)

    return AccuracyMetric(scene_id=scene_id, date=date,
                          precision=precision, recall=recall)
```

### 11.3 Sandbox 转正门禁

```yaml
# CI/CD 流水线 Sandbox 转正门禁
gates:
  - name: sandbox_accuracy_check
    condition: "sandbox_precision >= production_precision * 1.02"
    consecutive_hours: 48

  - name: resource_consumption_check
    condition: "sandbox_gpu_memory <= production_gpu_memory * 1.1"

  - name: inference_latency_check
    condition: "sandbox_p99_latency_ms <= production_p99_latency_ms * 1.2"

  - name: human_review
    required_approvers: 2
    role_required: "MODEL_REVIEWER"
    self_approve_forbidden: true
```

---

## 12. 测试规范

### 12.1 测试覆盖率要求

| 模块 | 单元测试覆盖率 | 集成测试 | 说明 |
|---|---|---|---|
| 算法插件 | >= 90% | 必须 | 含模糊/过曝/遮挡等噪声场景 |
| 报警判定服务 | >= 95% | 必须 | Sandbox 拦截逻辑必须 100% 覆盖 |
| 低代码编排器（后端） | >= 85% | 必须 | 配置 JSON 生成正确性 |
| 其他后端服务 | >= 80% | 推荐 | — |
| 前端组件 | >= 70% | 推荐 | — |

### 12.2 必须包含的测试用例

```python
class TestAlgorithmPlugin:

    def test_normal_image(self):
        """正常图像推理，验证输出格式符合 Schema"""

    def test_blurry_image(self):
        """模糊图像（拉普拉斯方差 < 50），推理不应崩溃"""

    def test_dark_image(self):
        """黑屏/过暗图像（平均亮度 < 30），推理不应崩溃"""

    def test_sandbox_flag_not_modified(self):
        """验证 is_sandbox 标识在推理过程中不被修改"""

    def test_inference_time_recorded(self):
        """验证推理耗时必须被记录且 > 0"""

    def test_roi_respected(self):
        """验证检测结果在 ROI 范围内"""


class TestAlarmJudgeService:

    def test_sandbox_alarm_intercepted(self):
        """核心：Sandbox 推理结果绝不触发告警推送"""
        sandbox_result = InferResult(is_sandbox=True, has_anomaly=True)
        judge_service.process(sandbox_result)
        assert notification_service.send.not_called()
        assert sandbox_db.write.called()

    def test_production_critical_alarm_sent(self):
        """生产 CRITICAL 告警正常触发多通道推送"""
```

### 12.3 性能测试基准

```
生产推理服务（GPU）：
  - 单帧推理 P50 < 20ms
  - 单帧推理 P99 < 50ms
  - 16 并发场景下 P99 < 100ms

边缘推理（Jetson）：
  - 单帧推理 P50 < 50ms
  - 单帧推理 P99 < 100ms

飞剪场景（特殊，边缘端本地完成）：
  - 端到端（相机触发→控制信号输出）< 10ms

Kafka 消息积压监控：
  - tianjing.frame.production 消费延迟 < 200ms（超出触发告警）
```

---

## 13. CI/CD 与部署规范

### 13.1 分支策略

```
main          # 生产分支，只接受来自 release/* 的合并，需要 2 人审核
develop       # 开发集成分支
feature/*     # 功能分支，命名：feature/{jira-ticket}-{short-desc}
release/*     # 发布分支，命名：release/v{major}.{minor}.{patch}
hotfix/*      # 生产紧急修复，从 main 创建，合并后同步 develop

保护规则（AI 工具不得自动提交到以下分支）：
main、develop、release/* 均禁止直接 push，必须通过 PR + 人工审核
```

### 13.2 部署流水线门禁

```yaml
pipeline_gates:
  code_quality:
    - sonarqube_quality_gate: passed
    - test_coverage: ">= module_minimum"
    - security_scan: "no_critical_vulnerabilities"

  algorithm_gates:
    - sandbox_validation_hours: ">= 48"
    - sandbox_precision_improvement: ">= 2%"
    - human_review: "approved_by_2"

  production_deployment:
    - deployment_window: "工作日 09:00-17:00"  # 禁止夜间无人值守部署
    - rollback_plan: required
    - deployment_type: "blue_green"
    - smoke_test: passed
```

### 13.3 镜像规范

```dockerfile
# 使用精确版本号，禁止 :latest
FROM python:3.11.8-slim-bookworm

# 非 root 用户运行
RUN useradd -m -u 1000 tianjing
USER tianjing

# 生产推理镜像只安装 onnxruntime-gpu 或 tensorrt，不安装完整 torch
# 禁止：镜像中包含密钥、证书、数据库连接串
# 禁止：镜像大小超过 2GB（推理）/ 5GB（训练）
```

---

## 14. 日志与可观测性规范

### 14.1 日志规范

```python
# 使用结构化 JSON 日志，禁止纯文本日志
import structlog
logger = structlog.get_logger()

# 正确：必须包含 scene_id、is_sandbox、trace_id
logger.info("inference_completed",
    scene_id=scene_id,
    frame_id=frame_id,
    is_sandbox=is_sandbox,
    plugin_id=plugin_id,
    inference_ms=inference_ms,
    has_anomaly=has_anomaly,
    trace_id=trace_id
)

# 禁止：日志中包含原始图像数据
# 禁止：日志中包含数据库连接串或密钥
# 禁止：高频推理日志使用 INFO 级别（必须用 DEBUG）
```

### 14.2 必须暴露的 Prometheus 指标

```
# 推理服务
tianjing_infer_duration_seconds{scene_id, is_sandbox}  # Histogram
tianjing_infer_total{scene_id, is_sandbox, result}     # Counter
tianjing_infer_queue_length{scene_id}                  # Gauge

# 告警服务
tianjing_alarm_total{scene_id, level, is_sandbox}      # Counter
tianjing_alarm_intercepted_total{scene_id}             # Counter（Sandbox 拦截次数）

# 感知健康
tianjing_camera_health_score{scene_id, camera_id}     # Gauge（0-100）
tianjing_camera_offline_total{scene_id}                # Counter

# 模型漂移
tianjing_model_precision{scene_id}                     # Gauge（每日更新）
tianjing_model_retrain_trigger_total{scene_id}         # Counter
```

---

## 15. 禁止行为清单

### P0 — 安全红线（立即停止并上报）

```
1. Sandbox 推理结果触发 MQTT 告警推送或写入工业互联网平台
2. 训练环境代码直接访问生产数据库（tianjing_prod）
3. 模型文件绕过 CI/CD 流水线和人工审核直接部署到生产推理服务
4. 生产推理服务的 NetworkPolicy 中出现允许访问外网的出口规则
5. 代码中硬编码任何密钥、证书、数据库密码
6. 低代码编排器中执行用户输入的任意代码（eval/exec 等）
7. is_sandbox 标识在传递链路中被篡改为 false
```

### P1 — 架构红线（需经技术评审才可豁免）

```
1. 擅自替换技术选型（如将 PostgreSQL 替换为 MySQL）
2. 微服务跨越职责边界（如推理服务直接写告警数据库）
3. 新增 Kafka Consumer 订阅了错误的 Topic（生产/Sandbox 混订）
4. 算法插件内部发起网络请求或数据库操作
5. 绕过 Nacos 配置中心硬编码业务参数
6. 在非 training 命名空间的服务中引入 PyTorch 完整依赖
7. 同一服务同时部署在 production 和 sandbox 命名空间
8. 将任何服务端口配置为 8080 或 8088（开发服务器保留端口，已被其他 Docker 容器占用）
   - 8080：禁止使用，由开发服务器其他 Docker 服务占用
   - 8088：禁止使用，由开发服务器其他 Docker 服务占用
   - 如需新增服务端口，从 8102 起续编，并在 docs/天柱天镜_端口分配表_V1.0.md 中登记
9. 微服务在本地开发环境手动以 java -jar 启动（必须通过 scripts/start-backend.sh）
   - 手动启动会丢失 TIANJING_JWT_PUBLIC_KEY_PEM 等关键环境变量，导致所有接口返回 401/500
   - start-backend.sh 是本地开发环境唯一认可的服务启动入口
10. Controller 直接返回 @Entity / @TableName 实体类作为 API 响应
    - 实体字段名（camelCase）、枚举大小写、JSONB 原始字符串极易与前端约定不一致
    - 所有接口响应必须经过专用 DTO 转换，参考 SceneConfigDetail、AlgorithmPluginDetail
```

### P2 — 工程规范（Code Review 必须修复）

```
1. 单元测试覆盖率低于模块最低要求
2. 日志中缺少 scene_id 或 trace_id 字段
3. REST API 响应格式不符合统一规范
4. Dockerfile 使用 :latest 标签
5. 代码注释中英文混用（统一使用中文注释）
6. TDengine 时序数据遗漏 is_sandbox 标签
7. 新增算法插件未提供 plugin.yaml 元信息文件
8. MinIO 新增 Bucket 未配置访问策略
9. 前端 AppLayout.vue 的 <router-view> 使用 <transition mode="out-in"> 包装
   - 当子页面 API 请求失败时，CSS transitionend 事件不触发，旧组件永久残留 DOM，
     导致路由切换视觉卡死。如需页面切换动画，使用无 mode 的 <transition> 或 JS 过渡方案
10. 新增微服务时未在 scripts/ 目录提供 seed_{module}.sql
    - 本地开发环境必须可通过种子数据直接运行，不依赖手动造数据
    - 种子数据文件命名规范：scripts/seed_{service_name}.sql
```

---

## 附录：快速参考

### 环境变量命名规范

```bash
# 格式：TIANJING_{SERVICE}_{CONFIG}
TIANJING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
TIANJING_POSTGRES_PROD_URL=jdbc:postgresql://pg-prod:5432/tianjing_prod
TIANJING_POSTGRES_SANDBOX_URL=jdbc:postgresql://pg-sandbox:5432/tianjing_sandbox
TIANJING_MINIO_ENDPOINT=http://minio:9000
TIANJING_NACOS_SERVER_ADDR=nacos:8848
TIANJING_EMQX_BROKER_URL=mqtt://emqx:1883
TIANJING_MLFLOW_TRACKING_URI=http://mlflow:5000
```

### 场景 ID 前缀规范

```
SCENE-PELLET-*    # 球团厂
SCENE-SINTER-*    # 烧结厂
SCENE-STEEL-*     # 炼钢厂
SCENE-SECTION-*   # 型钢厂
SCENE-STRIP-*     # 带钢厂
```

### 告警级别判定参考

| 级别 | 触发条件 | 推送行为 |
|---|---|---|
| `CRITICAL` | 设备停机风险/质量严重缺陷（置信度 >= 0.9 且连续 3 帧） | MQTT + 企业微信 + 工单 + SCADA 联动 |
| `WARNING` | 工艺参数偏差/设备轻微异常（置信度 >= 0.8 且连续 2 帧） | WebSocket + 移动端推送 |
| `INFO` | 状态变化记录/低置信度检测 | 仅写数据库，看板展示 |
| `SANDBOX_*` | Sandbox 推理任何结果 | **仅写实验室数据库，禁止任何外部推送** |

---

*CLAUDE.md 版本：V1.1 · 编制日期：2026-04-01 · 天柱·天镜项目组*
*V1.1 变更：§15 新增 P1 规则 9-10（服务启动规范、DTO 强制规范），P2 规则 9-10（路由过渡规范、种子数据规范），源于 2026-04-01 调试实践总结*

*本文件是项目唯一技术行为约束文件，与方案 V2.0 保持一致。*
*如本文件与口头约定存在冲突，以本文件为准。*
