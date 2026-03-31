# 天柱·天镜 · 工业视觉 AI 推理平台
# 技术规范文档（Tech Spec）

> **版本**：V1.1 · **日期**：2026-03-31 · **密级**：内部文件
> **适用范围**：平台所有后端微服务（Java/Python）、前端工程（Vue3）
> **关联文档**：方案 V2.0 · 数据库设计 V2.1 · API 接口规范 V3.1 · Sprint 计划 V2.0
>
> **V1.1 修订说明**（依据 Sprint 计划 V2.0 调整）：
> 1. **新增 §10.5 推理适配器模式（Inference Adapter Pattern）**：Cloud Proxy / Local GPU / CPU ONNX Runtime 三种推理后端的抽象层设计，支持透明切换
> 2. **新增 §10.6 录像回放服务编码规范**：Recording Replay Service 实现约定，包含帧投递格式与限流保护
> 3. **§7.2 Nacos 配置补充**：新增 `CLOUD_VISION_API_KEY`、`INFER_BACKEND` 等实验室阶段必要配置项说明
> 4. **新增 §10.7 GPU 特性开关模式**：`infer_backend` 配置驱动的特性开关，Sprint 5 无需修改代码即可切换本地 GPU

---

## 目录

1. [总体约定](#1-总体约定)
2. [分层架构约定](#2-分层架构约定)
3. [编码规范](#3-编码规范)
   - 3.1 [Java 后端服务规范](#31-java-后端服务规范)
   - 3.2 [Python AI 服务规范](#32-python-ai-服务规范)
   - 3.3 [Vue3 前端规范](#33-vue3-前端规范)
   - 3.4 [SQL / 数据库操作规范](#34-sql--数据库操作规范)
4. [异常处理标准](#4-异常处理标准)
5. [日志格式规范](#5-日志格式规范)
6. [安全编码要求](#6-安全编码要求)
7. [配置管理规范](#7-配置管理规范)
8. [测试规范](#8-测试规范)
9. [API 开发规范（补充）](#9-api-开发规范补充)
10. [AI 推理服务规范](#10-ai-推理服务规范)
    - 10.5 [推理适配器模式（★V1.1）](#105-推理适配器模式v11)
    - 10.6 [录像回放服务规范（★V1.1）](#106-录像回放服务规范v11)
    - 10.7 [GPU 特性开关模式（★V1.1）](#107-gpu-特性开关模式v11)

---

## 1. 总体约定

### 1.1 技术栈版本基准

| 层次 | 技术 | 版本 | 备注 |
|------|------|------|------|
| **后端框架** | Spring Boot | 3.2.x | Java 21 LTS |
| **ORM** | MyBatis-Plus | 3.5.x | 禁用 MyBatis XML，统一 Lambda 风格 |
| **数据库** | PostgreSQL | 16 | 关系型主库 |
| **时序库** | TDengine | 3.x | 推理结果时序存储 |
| **缓存** | Redis | 7.x | 场景配置热缓存 |
| **消息总线** | Apache Kafka | 3.6.x | 推理结果流处理 |
| **配置中心** | Nacos | 2.3.x | 动态配置 / 服务注册 |
| **链路追踪** | Jaeger + OpenTelemetry | 1.x | 全链路 trace_id |
| **推理框架** | TensorRT 8.x + ONNX Runtime 1.x | — | GPU 推理优化 |
| **训练框架** | PyTorch 2.x + MMDetection 3.x | — | 工业检测生态 |
| **对象存储** | MinIO（RELEASE.2024+） | — | S3 兼容协议 |
| **容器编排** | Kubernetes 1.28+ | — | GPU Operator 集成 |
| **服务网格** | Istio 1.20+ | — | mTLS 内部鉴权 |
| **前端框架** | Vue 3.4+ + TypeScript 5.x | — | Composition API |
| **构建工具** | Vite 5.x | — | 前端构建 |
| **模型管理** | MLflow 2.x | — | 模型版本 + 实验管理 |

### 1.2 开发环境要求

- **Java 版本**：OpenJDK 21（严禁使用低于 21 的版本，不允许使用 Oracle JDK）
- **Python 版本**：3.11（AI 服务统一版本，使用 `pyproject.toml` 管理依赖）
- **Node.js 版本**：20 LTS（前端统一版本）
- **代码格式化**：后端使用 Checkstyle（Google Java Style）；Python 使用 `ruff` + `black`；前端使用 ESLint + Prettier
- **IDE 插件强制安装**：SonarLint（静态代码分析）

### 1.3 仓库与分支约定

| 分支 | 说明 | 合并规则 |
|------|------|---------|
| `main` | 生产版本，只接受 Release 合并 | 需 2 人审批 |
| `develop` | 集成测试分支 | 需 1 人审批 + CI 通过 |
| `feature/<ticket-id>-<brief>` | 功能开发分支，从 `develop` 切出 | PR → develop |
| `hotfix/<ticket-id>-<brief>` | 生产紧急修复，从 `main` 切出 | PR → main + develop |
| `release/<version>` | 发版准备，禁止新功能提交 | PR → main |

**Commit Message 规范**（Conventional Commits）：

```
<type>(<scope>): <subject>

[optional body]

[optional footer: TICKET-xxx]
```

| type | 含义 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 代码重构（不影响功能） |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `docs` | 文档更新 |
| `chore` | 构建/依赖变更 |
| `ci` | CI/CD 流水线变更 |

---

## 2. 分层架构约定

### 2.1 后端微服务分层模型

每个 Spring Boot 微服务必须遵循以下五层结构，**禁止跨层调用**：

```
┌─────────────────────────────────────────────────┐
│  Controller 层（接入层）                          │
│  职责：参数校验、权限检查、请求/响应转换             │
│  禁止：包含任何业务逻辑                            │
├─────────────────────────────────────────────────┤
│  Service 层（业务逻辑层）                         │
│  职责：业务规则、事务边界、跨 Repository 编排       │
│  禁止：直接操作 HTTP Request/Response             │
├─────────────────────────────────────────────────┤
│  Domain 层（领域模型层）                          │
│  职责：实体定义、领域规则（纯 POJO，无 Spring 依赖）│
│  包含：Entity、VO、DTO、枚举、常量                 │
├─────────────────────────────────────────────────┤
│  Repository 层（数据访问层）                      │
│  职责：数据库 CRUD、缓存读写、消息发布              │
│  禁止：包含业务判断逻辑                            │
├─────────────────────────────────────────────────┤
│  Infrastructure 层（基础设施层）                  │
│  职责：外部 HTTP 调用、Kafka 生产者/消费者配置、     │
│        MinIO 客户端、Nacos 集成等                 │
└─────────────────────────────────────────────────┘
```

### 2.2 包结构约定

```
com.tianzhu.tianjing.<service-name>
├── controller/          # REST 控制器
├── service/             # 业务接口
│   └── impl/            # 业务实现
├── domain/
│   ├── entity/          # DB 实体类（对应数据库表）
│   ├── dto/             # 请求 DTO（Request Objects）
│   ├── vo/              # 响应 VO（View Objects）
│   └── enums/           # 枚举类型
├── repository/          # 数据访问（MyBatis Mapper）
├── infrastructure/
│   ├── kafka/           # Kafka 生产者/消费者
│   ├── redis/           # Redis 操作封装
│   ├── minio/           # MinIO 客户端
│   └── external/        # 外部服务调用（Feign Client）
├── config/              # Spring 配置类
├── exception/           # 异常定义
└── util/                # 工具类（无 Spring 依赖）
```

### 2.3 微服务间通信约定

| 调用类型 | 技术选型 | 适用场景 |
|---------|---------|---------|
| **同步调用** | Spring Cloud OpenFeign + Resilience4j | 管理后台服务间调用 |
| **异步解耦** | Apache Kafka | 推理结果投递、告警事件 |
| **内部集群调用** | gRPC（Protobuf）+ Istio mTLS | 推理核心服务高频调用 |
| **配置变更** | Nacos 事件通知 | 场景配置热更新广播 |

**服务调用超时规范**：

| 调用类型 | 连接超时 | 读取超时 | 重试次数 |
|---------|---------|---------|---------|
| Feign 同步调用 | 2s | 5s | 2 次（幂等接口） |
| gRPC 推理调用 | 1s | 3s | 0（推理不重试，直接降级） |
| Kafka 消息发布 | — | — | 3 次（异步补偿） |
| Redis 操作 | 500ms | 1s | 1 次 |

### 2.4 Python AI 服务分层模型

```
tianjing_<service_name>/
├── api/                 # FastAPI 路由层（仅参数校验 + 序列化）
├── service/             # 业务逻辑层
├── core/                # 核心推理逻辑（无框架依赖）
│   ├── models/          # 模型加载与管理
│   └── processors/      # 图像预处理/后处理
├── infrastructure/
│   ├── kafka/           # Kafka 消费者/生产者
│   ├── minio/           # 图像存取
│   └── db/              # TDengine 写入
├── domain/              # 数据类定义（dataclass / Pydantic）
└── config/              # 配置加载（基于 Pydantic Settings）
```

---

## 3. 编码规范

### 3.1 Java 后端服务规范

#### 3.1.1 命名规范

| 元素 | 命名风格 | 示例 |
|------|---------|------|
| 类名 | UpperCamelCase | `SceneConfigService` |
| 方法名 | lowerCamelCase | `enableScene()` |
| 变量名 | lowerCamelCase | `sceneId` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 包名 | 全小写，下划线分隔 | `alarm_dispatch` |
| 数据库实体类 | 与表名对应，后缀 `Entity` | `SceneConfigEntity` |
| DTO 类 | 业务名 + `Request` / `Response` | `SceneCreateRequest` |
| VO 类 | 业务名 + `VO` | `SceneDetailVO` |
| Kafka Topic | `tianjing.<domain>.<event>` | `tianjing.infer.result` |
| Redis Key | `tianjing:<domain>:<id>` | `tianjing:scene:active:SC-001` |

#### 3.1.2 方法规范

- 单个方法不超过 **50 行**，超过必须拆分（超过 80 行强制拆分）
- 方法参数不超过 **4 个**，超过时必须封装为 DTO 对象
- 禁止使用魔法数字（Magic Number），所有数值常量必须定义为 `static final` 常量并附加注释
- 禁止在循环体中执行数据库查询（N+1 问题），必须批量查询

```java
// ✅ 正确：批量查询
List<SceneConfigEntity> scenes = sceneRepository.findBySceneIds(sceneIds);

// ❌ 禁止：循环中查询
for (String sceneId : sceneIds) {
    SceneConfigEntity scene = sceneRepository.findBySceneId(sceneId);
}
```

#### 3.1.3 空值处理

- 禁止直接返回 `null`，使用 `Optional<T>` 或抛出业务异常
- 所有 API 响应字段在 JSON 序列化时，`null` 字段不输出（`@JsonInclude(NON_NULL)`）
- 集合类型返回值不允许为 `null`，返回空集合 `Collections.emptyList()`
- 使用 `@NotNull` / `@NotBlank` 注解配合 Spring Validation，不在 Service 层手动判空

#### 3.1.4 并发与线程安全

- Service Bean 为单例，禁止在 Service 类中定义可变的实例变量（非线程安全）
- 对 Redis 的复合操作（如 check-then-set）必须使用 Lua 脚本或 `RedisTemplate.execute(SessionCallback)` 保证原子性
- 数据库更新必须使用乐观锁（`version` 字段），冲突时抛出 `OptimisticLockException`（业务码 2001）

```java
// 乐观锁更新标准写法
int updated = sceneRepository.updateWithVersion(entity);
if (updated == 0) {
    throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT);
}
```

#### 3.1.5 禁止事项

| 禁止行为 | 替代方案 |
|---------|---------|
| `System.out.println()` 调试输出 | 使用 SLF4J Logger |
| `e.printStackTrace()` 异常输出 | 统一异常处理器捕获并记录 |
| 捕获 `Exception` 后吞掉异常 | 必须记录日志或重新抛出 |
| `new Thread()` 直接创建线程 | 使用 Spring `@Async` + 线程池 |
| `Thread.sleep()` 在业务代码中 | 使用 Kafka 延时队列或 Scheduled |
| 硬编码 IP / 端口 / 密码 | 使用 Nacos 配置中心或 K8s Secret |
| 直接引用 `HttpServletRequest` 在 Service 层 | Controller 层提取后作为参数传入 |

### 3.2 Python AI 服务规范

#### 3.2.1 命名规范

| 元素 | 命名风格 | 示例 |
|------|---------|------|
| 模块/文件 | snake_case | `infer_engine.py` |
| 类名 | UpperCamelCase | `YoloInferEngine` |
| 函数/方法 | snake_case | `load_model()` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_CONF_THRESHOLD` |
| 私有方法 | 单下划线前缀 | `_preprocess_frame()` |

#### 3.2.2 类型注解要求

**所有函数/方法必须包含完整的类型注解**，使用 `from __future__ import annotations` 支持前向引用：

```python
# ✅ 正确
def infer(self, frame: np.ndarray, params: InferParams) -> InferResult:
    ...

# ❌ 禁止：无类型注解
def infer(self, frame, params):
    ...
```

#### 3.2.3 数据类定义

使用 Pydantic v2 定义所有输入/输出数据结构，禁止使用裸 `dict` 传递业务数据：

```python
from pydantic import BaseModel, Field

class InferResult(BaseModel):
    scene_id: str
    anomaly_count: int = Field(ge=0)
    confidence: float = Field(ge=0.0, le=1.0)
    image_url: str | None = None
    infer_ms: int
```

#### 3.2.4 模型加载规范

- 推理服务启动时完成模型预热，不在首次推理请求时加载
- 模型权重文件路径通过环境变量注入，不硬编码
- TensorRT 引擎文件与 ONNX 权重文件必须同时提供，TensorRT 不可用时降级到 ONNX Runtime

```python
# 模型加载标准模式
class ModelLoader:
    def __init__(self, model_path: str, device: str = "cuda"):
        self._engine = self._load_with_fallback(model_path, device)

    def _load_with_fallback(self, path: str, device: str):
        trt_path = path.replace(".onnx", ".engine")
        if Path(trt_path).exists() and device == "cuda":
            return TensorRTEngine(trt_path)
        logger.warning("TensorRT engine not found, fallback to ONNX Runtime")
        return OnnxRuntimeEngine(path, device)
```

#### 3.2.5 图像数据传输规范

- **服务内部**：`np.ndarray`（BGR，HWC，uint8），禁止转换为 base64 在同进程内传递
- **服务间 gRPC**：Protobuf `bytes` 字段，传输 JPEG 压缩后的原始字节
- **结果存储（MinIO）**：保存原始帧 JPEG（质量 90），路径格式：`tianjing/{scene_id}/{date}/{alarm_id}.jpg`
- 禁止在推理热路径（每帧调用的函数）中进行内存分配，使用预分配缓冲区

### 3.3 Vue3 前端规范

#### 3.3.1 组件规范

- 所有组件使用 `<script setup lang="ts">` Composition API 风格
- 组件文件名使用 UpperCamelCase，与组件名保持一致：`SceneConfigCard.vue`
- Props 必须定义 TypeScript 类型，禁止使用 `any`
- 禁止在模板中直接调用复杂计算函数，使用 `computed` 缓存

```vue
<!-- ✅ 正确 -->
<script setup lang="ts">
interface Props {
  sceneId: string
  status: 'ACTIVE' | 'INACTIVE' | 'DRAFT'
}
const props = defineProps<Props>()
const statusLabel = computed(() => STATUS_MAP[props.status])
</script>

<!-- ❌ 禁止：在模板中调用函数 -->
<template>
  <span>{{ getStatusLabel(status) }}</span>
</template>
```

#### 3.3.2 API 调用规范

- 所有 HTTP 请求封装在 `src/api/` 目录下，禁止在组件中直接调用 axios
- 使用统一的请求拦截器自动注入 JWT Token 和 `X-Request-ID`
- 使用统一响应拦截器处理业务错误码（非 0 时统一弹出错误提示）
- SSE（Server-Sent Events）连接必须在组件卸载时（`onUnmounted`）关闭

#### 3.3.3 状态管理

- 全局状态使用 Pinia Store，禁止在组件间通过 Props 传递超过 3 层的数据
- Store 命名：`use<Domain>Store`，如 `useSceneStore`
- 敏感数据（JWT Token）存储在内存 Store 中，不写入 `localStorage`

### 3.4 SQL / 数据库操作规范

#### 3.4.1 通用规范

- 所有 SQL 关键字大写，表名/字段名小写：`SELECT id, scene_name FROM scene_config`
- 禁止使用 `SELECT *`，必须明确列出所需字段
- 所有查询都必须有索引支持，禁止全表扫描（`EXPLAIN ANALYZE` 验证）
- 删除操作一律使用软删除（`is_deleted = TRUE`），禁止物理删除业务数据

#### 3.4.2 事务边界

- 事务注解 `@Transactional` 只加在 Service 实现层，禁止加在 Controller 层
- 跨数据库操作（如同时写 PostgreSQL + Kafka）使用"本地事务 + 事件补偿"模式，不使用分布式事务

```java
@Transactional(rollbackFor = Exception.class)
public AlarmRecord createAlarm(AlarmCreateRequest request) {
    // 1. 写 PostgreSQL（事务内）
    AlarmRecord record = alarmRepository.insert(buildEntity(request));
    // 2. 发 Kafka（事务外）—— 在事务提交后通过 TransactionalEventListener 发送
    eventPublisher.publishEvent(new AlarmCreatedEvent(record));
    return record;
}
```

#### 3.4.3 TDengine 写入规范

- TDengine 写入必须使用 **批量写入**（每批 ≥ 100 条），禁止逐条写入
- 超级表（STable）字段变更需经 DBA 审批，不允许运行时动态添加 Tag
- 时序数据写入延迟超过 5s 需告警

#### 3.4.4 Flyway 迁移规范

- 迁移脚本命名：`V{yyyyMMdd}__{description}.sql`，如 `V20260331__add_data_sync_audit.sql`
- 每个迁移文件只执行一类变更（加表、加字段、加索引分开写）
- 已应用的迁移脚本**严禁修改**，必须新建迁移脚本修正
- 所有 DDL 变更必须同步更新数据库设计文档

---

## 4. 异常处理标准

### 4.1 异常分类体系

```
TianjingException（平台基础异常）
├── BusinessException（业务异常，对应 API 错误码 1xxx-4xxx）
│   ├── ParamException（参数异常，1xxx）
│   ├── ResourceNotFoundException（资源不存在，3xxx）
│   ├── AuthException（鉴权异常，4xxx）
│   └── BusinessRuleException（业务规则异常，2xxx）
└── InfrastructureException（基础设施异常，5xxx）
    ├── DatabaseException（数据库异常，5002）
    ├── CacheException（缓存异常，5003）
    ├── MessageBusException（Kafka 异常，5004）
    └── InferenceException（推理服务异常，5006）
```

### 4.2 异常定义规范

```java
// 业务异常标准定义（携带错误码）
@Getter
public class BusinessException extends TianjingException {
    private final int code;
    private final String detail;  // 仅在 Staging/Dev 环境输出

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.detail = detail;
    }
}

// 错误码枚举
public enum ErrorCode {
    OPTIMISTIC_LOCK_CONFLICT(2001, "乐观锁版本冲突，请刷新后重试"),
    SCENE_STATUS_NOT_ALLOWED(2002, "资源状态不允许此操作"),
    SCENE_PRECONDITION_NOT_MET(2003, "场景启用前置条件不满足"),
    // ... 对应 API 规范 V3.0 第 4 章完整错误码表
    ;

    private final int code;
    private final String message;
}
```

### 4.3 统一异常处理器

每个 Spring Boot 服务必须定义 `GlobalExceptionHandler`，覆盖以下情形：

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // 1. 业务异常 → 返回对应错误码，HTTP 400/401/403/404/409
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex,
                                                             HttpServletRequest req) {
        log.warn("[BIZ_ERROR] traceId={} code={} msg={} uri={}",
                 TraceUtil.getTraceId(), ex.getCode(), ex.getMessage(), req.getRequestURI());
        return buildErrorResponse(ex.getCode(), ex.getMessage(), ex.getDetail(),
                                  resolveHttpStatus(ex.getCode()));
    }

    // 2. 参数校验失败 → HTTP 400, code=1001
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(joining(", "));
        log.warn("[PARAM_ERROR] traceId={} detail={}", TraceUtil.getTraceId(), detail);
        return buildErrorResponse(1001, "请求参数缺失或格式错误", detail, BAD_REQUEST);
    }

    // 3. 基础设施异常 → HTTP 500, code=5xxx，不暴露内部细节
    @ExceptionHandler(InfrastructureException.class)
    public ResponseEntity<ApiResponse<Void>> handleInfra(InfrastructureException ex,
                                                          HttpServletRequest req) {
        log.error("[INFRA_ERROR] traceId={} code={} uri={}", TraceUtil.getTraceId(),
                  ex.getCode(), req.getRequestURI(), ex);
        return buildErrorResponse(ex.getCode(), ex.getMessage(), null, INTERNAL_SERVER_ERROR);
    }

    // 4. 兜底未知异常 → HTTP 500, code=5001，不暴露内部细节
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex,
                                                            HttpServletRequest req) {
        log.error("[UNKNOWN_ERROR] traceId={} uri={}", TraceUtil.getTraceId(),
                  req.getRequestURI(), ex);
        return buildErrorResponse(5001, "服务内部错误", null, INTERNAL_SERVER_ERROR);
    }
}
```

### 4.4 Python 服务异常处理

```python
# FastAPI 全局异常处理器
@app.exception_handler(BusinessException)
async def business_exception_handler(request: Request, exc: BusinessException):
    logger.warning(
        "business_error",
        trace_id=get_trace_id(),
        code=exc.code,
        message=exc.message,
        path=request.url.path,
    )
    return JSONResponse(
        status_code=exc.http_status,
        content={"code": exc.code, "message": exc.message,
                 "trace_id": get_trace_id(), "data": None},
    )

@app.exception_handler(Exception)
async def unknown_exception_handler(request: Request, exc: Exception):
    logger.error(
        "unknown_error",
        trace_id=get_trace_id(),
        path=request.url.path,
        exc_info=exc,
    )
    return JSONResponse(
        status_code=500,
        content={"code": 5001, "message": "服务内部错误",
                 "trace_id": get_trace_id(), "data": None},
    )
```

### 4.5 Kafka 消费异常处理

- **可重试异常**（网络超时、数据库临时不可用）：抛出 `RetryableException`，由 Spring Kafka 自动重试（最多 3 次，指数退避）
- **不可重试异常**（数据格式错误、业务规则违反）：发送到死信队列（DLT Topic：`tianjing.<domain>.dlt`），不重试，记录告警日志
- 消费端必须实现幂等处理，以消息中的唯一业务 ID 去重

---

## 5. 日志格式规范

### 5.1 日志框架与配置

- **Java 服务**：SLF4J + Logback，禁止直接使用 `java.util.logging` 或 `log4j`
- **Python 服务**：`structlog`（结构化日志），禁止使用 `print()` 输出业务日志

所有服务统一输出 **JSON 格式日志**，便于 ELK / Loki 采集解析。

### 5.2 日志字段规范（必填字段）

每条日志必须包含以下字段：

| 字段名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `timestamp` | ISO 8601 | 日志时间（带时区） | `2026-03-31T10:23:45.123+08:00` |
| `level` | string | 日志级别 | `INFO` / `WARN` / `ERROR` |
| `service` | string | 服务名（对应 K8s Service 名） | `scene-config-service` |
| `trace_id` | string | Jaeger 全链路追踪 ID | `7b4e2c1a9f3d8b05` |
| `span_id` | string | 当前 Span ID | `a1b2c3d4` |
| `logger` | string | Logger 类名 | `SceneConfigServiceImpl` |
| `message` | string | 日志正文（英文，机器可读） | `scene_enabled` |
| `thread` | string | 线程名 | `http-nio-8080-exec-1` |

### 5.3 业务日志字段（推荐附加）

```json
{
  "timestamp": "2026-03-31T10:23:45.123+08:00",
  "level": "INFO",
  "service": "scene-config-service",
  "trace_id": "7b4e2c1a9f3d8b05",
  "span_id": "a1b2c3d4",
  "logger": "SceneConfigServiceImpl",
  "message": "scene_enabled",
  "thread": "http-nio-8080-exec-3",
  "user": "zhangsan",
  "scene_id": "SC-PELLETING-001",
  "elapsed_ms": 12
}
```

### 5.4 日志级别使用规范

| 级别 | 使用场景 | 示例 |
|------|---------|------|
| `TRACE` | 调试时临时输出，**禁止提交到主干分支** | 单帧图像处理中间变量 |
| `DEBUG` | 开发/Staging 环境可用，生产关闭 | SQL 执行详情、参数值 |
| `INFO` | **主业务流程的关键节点**（操作开始/完成、状态变更） | 场景启用、模型部署、告警触发 |
| `WARN` | 可自恢复的异常（降级、重试、配置缺失但有默认值） | Redis 缓存 Miss、Kafka 消费重试 |
| `ERROR` | 需要人工介入的故障（数据库连接失败、推理服务宕机） | 数据库连接池耗尽、GPU OOM |

**禁止行为**：
- 禁止将用户密码、JWT Token、图像原始数据写入日志
- 禁止在 ERROR 级别记录业务校验失败（应用 WARN）
- 禁止在生产环境开启 DEBUG 级别（性能影响）

### 5.5 推理服务专项日志

推理服务（质检/设备监测/工艺参数）必须额外记录以下指标日志（写入 TDengine `infer_perf_ts` 表）：

```json
{
  "timestamp": "2026-03-31T10:23:45.100+08:00",
  "level": "INFO",
  "service": "quality-infer-service",
  "message": "infer_complete",
  "scene_id": "SC-CAST-001",
  "model_version": "v2.3.1",
  "is_sandbox": false,
  "preprocess_ms": 3,
  "infer_ms": 18,
  "postprocess_ms": 2,
  "total_ms": 23,
  "anomaly_count": 2,
  "gpu_util_pct": 65
}
```

### 5.6 日志保留策略

| 环境 | 保留时长 | 存储位置 |
|------|---------|---------|
| 生产（ERROR/WARN） | 180 天 | ELK / Loki |
| 生产（INFO） | 30 天 | ELK / Loki |
| Staging | 7 天 | ELK / Loki |
| 开发 | 本地，不集中采集 | — |

---

## 6. 安全编码要求

### 6.1 认证与授权

#### 6.1.1 JWT 规范

- **算法**：RS256（非对称签名），禁止使用 HS256（共享密钥）
- **私钥**：存储在 Kubernetes Secret，不允许存入代码仓库或配置文件
- **Token 有效期**：生产 8 小时；Staging/Dev 24 小时
- **Token 黑名单**：登出后立即写入 Redis，Key 格式：`tianjing:token:blacklist:{jti}`，TTL = Token 剩余有效期
- 禁止在 Token Payload 中存储敏感信息（密码 hash、身份证号等）

#### 6.1.2 权限校验

- Controller 层每个接口必须显式声明所需权限（`@RequiresRole`），不允许"默认开放"
- 数据行级权限：用户只能访问其所属厂部的场景数据，`WHERE factory_code = :userFactoryCode`
- 四眼原则校验（模型审核）：在 Service 层硬校验，`model.getSubmittedBy().equals(currentUser)` 时抛出 `AuthException(4004)`

#### 6.1.3 内部服务接口

- `/internal/*` 接口**不经过 JWT 认证**，依赖 Kubernetes NetworkPolicy 限制访问来源 Pod
- Istio mTLS 确保内部通信加密，禁止明文 HTTP 在集群内传输业务数据
- 网关层（Ingress / API Gateway）必须拦截所有来自集群外部对 `/internal/*` 路径的请求

### 6.2 输入验证

- 所有来自外部的输入（HTTP 请求体、查询参数、路径参数）必须经过 Bean Validation 校验，不信任客户端输入
- 文件上传（仿真视频、标注数据）必须校验：文件类型（白名单）、文件大小（≤ 2GB）、文件名（禁止路径穿越字符 `../`）
- 分页参数强制边界：`page ≥ 1`，`1 ≤ size ≤ 100`
- 时间范围参数：`start_time < end_time`，最大查询跨度 ≤ 31 天（防止大范围扫描）

### 6.3 SQL 注入防护

- 禁止使用字符串拼接构造 SQL，**所有 SQL 参数必须使用 MyBatis `#{}` 占位符**（非 `${}`）
- 动态排序字段（`ORDER BY`）必须通过白名单校验，不允许将用户输入直接拼入排序字段名
- MyBatis XML 中如必须使用 `${}` 处理表名/列名，必须通过枚举类型严格限定取值范围

```xml
<!-- ✅ 正确：参数化查询 -->
<select id="findBySceneId">
  SELECT id, scene_name FROM scene_config WHERE scene_id = #{sceneId}
</select>

<!-- ❌ 禁止：字符串拼接 -->
<select id="findBySceneId">
  SELECT id, scene_name FROM scene_config WHERE scene_id = '${sceneId}'
</select>
```

### 6.4 敏感数据保护

- **密码存储**：使用 BCrypt（cost factor ≥ 12），禁止 MD5/SHA1/明文存储
- **日志脱敏**：日志中不允许出现密码、完整 JWT Token、图像 base64 数据、摄像头 RTSP 地址（含凭证）
- **API 响应脱敏**：密码字段永远不在响应中返回；错误 `detail` 字段仅在 Staging/Dev 环境返回（`spring.profiles.active` 判断）
- **数据库加密**：摄像头 RTSP 地址、用户密码 hash 使用 PostgreSQL `pgcrypto` 列级加密存储

```java
// API 响应脱敏标准写法
@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
private String passwordHash;  // 反序列化有效，序列化不输出
```

### 6.5 安全网闸（Data Gap）规范

- 生产数据流向训练环境必须经过物理单向光闸，严禁通过网络直接同步
- 经光闸传输的数据必须先脱敏（人脸、工号、IP 地址等个人信息脱除）
- 每次数据同步必须记录 `data_sync_audit` 表（sync_id、传输文件数、checksum、传输时间）
- 安全网闸传输日志每日由安全管理员核查，发现异常立即告警

### 6.6 容器与 K8s 安全

- 所有容器镜像禁止以 `root` 用户运行，使用 `runAsNonRoot: true` + `runAsUser: 10001`
- 容器镜像禁止包含调试工具（curl、wget、ssh），基础镜像使用 Distroless 或 Alpine
- K8s Service Account 遵循最小权限原则，推理服务 Pod 不允许有写入生产数据库的 Secret
- 所有外部镜像拉取前必须经 Harbor 扫描（CVE 高危漏洞 0 容忍），通过后才能部署到生产

### 6.7 依赖安全

- 每个 Sprint 结束前执行一次依赖漏洞扫描（Java：`mvn dependency-check:check`；Python：`safety check`）
- CVSS 评分 ≥ 7.0 的漏洞必须在下一个 Sprint 内修复
- 禁止引入许可证为 GPL/AGPL 的第三方库（影响商业发布），允许 MIT/Apache 2.0/BSD

---

## 7. 配置管理规范

### 7.1 配置分级

| 配置类型 | 存储位置 | 运行时可变 | 示例 |
|---------|---------|----------|------|
| **基础设施配置**（数据库连接、Kafka broker） | Nacos + K8s Secret | 否（需重启） | DB URL、密码 |
| **业务参数配置**（推理阈值、告警规则） | Nacos（动态） | **是（热更新）** | 置信度阈值 0.85 |
| **场景运行时配置**（workflow JSON） | PostgreSQL → Redis 缓存 | **是（毫秒级热更新）** | 检测 ROI 坐标 |
| **静态常量**（枚举、版本号） | 代码仓库 | 否（随版本发布） | API 版本 v1 |

### 7.2 Nacos 配置命名空间约定

```
Namespace: tianjing-{env}  (prod / staging / dev)
Group: {service-name}
DataId: {service-name}-{env}.yaml
```

示例：`tianjing-prod` 命名空间，`scene-config-service` 组，`scene-config-service-prod.yaml` 文件。

### 7.2.1 实验室阶段必要配置项（★V1.1，依据 Sprint 计划 V2.0）

以下配置项在 Sprint 0-4（实验室阶段）必须存在，Sprint 5（GPU 到位后）可废弃或覆盖：

```yaml
# 文件：cloud-inference-proxy-dev.yaml（Nacos, tianjing-dev 命名空间）
# 文件：cloud-inference-proxy-staging.yaml（Nacos, tianjing-staging 命名空间）

cloud:
  vision:
    provider: ALIBABA_CLOUD        # 云端推理服务商：ALIBABA_CLOUD / BAIDU_EASYDL
    api_key: ${CLOUD_VISION_API_KEY}  # 通过 K8s Secret 注入，禁止写入配置文件明文
    endpoint: ${CLOUD_VISION_ENDPOINT}
    timeout_ms: 3000               # 单次调用超时（ms），超时降级为 ONNX_CPU
    max_retries: 2                 # 重试次数（仅超时类错误重试，业务错误不重试）
    rate_limit_qps: 10             # 云端 API 限速（每秒请求数），防止超额计费

infer:
  backend: CLOUD_API               # 全局默认推理后端；CLOUD_API / LOCAL_GPU / ONNX_CPU
                                   # 可被 algorithm_plugin.infer_backend 字段覆盖（插件级粒度）
  onnx_cpu:
    model_dir: /opt/tianjing/models/onnx  # CPU ONNX 降级模型存储路径（容器挂载）
    num_threads: 4                 # ONNX Runtime CPU 线程数

replay:
  kafka_topic: tianjing.frames.prod    # 与真实摄像头相同 Topic，下游无感知
  default_fps: 5                       # 默认回放帧率（实验室验证用）
  max_loop_count: 100                  # 无限循环的实际上限（防止资源泄漏）
```

**环境变量注入规范**（K8s Secret → 容器环境变量）：

```yaml
# K8s Deployment env 段
env:
  - name: CLOUD_VISION_API_KEY
    valueFrom:
      secretKeyRef:
        name: tianjing-cloud-vision-secret
        key: api_key
  - name: CLOUD_VISION_ENDPOINT
    valueFrom:
      secretKeyRef:
        name: tianjing-cloud-vision-secret
        key: endpoint
```

> **安全要求**：`CLOUD_VISION_API_KEY` 和 `CLOUD_VISION_ENDPOINT` 必须通过 K8s Secret 注入，
> 禁止写入 Nacos 配置明文，禁止提交到 Git 仓库，禁止出现在任何日志输出中。

### 7.3 禁止事项

- 禁止将任何配置硬编码在 `application.yml` 的 `prod` 环境配置中
- 禁止在代码中使用 `System.getenv()` 直接读取环境变量（统一通过 Spring `@Value` 或 `@ConfigurationProperties`）
- 禁止将不同环境的配置差异通过 `if` 条件判断处理，使用 Spring Profile 隔离

---

## 8. 测试规范

### 8.1 测试分层策略

| 测试类型 | 工具 | 覆盖率要求 | 范围 |
|---------|------|---------|------|
| **单元测试** | JUnit 5 + Mockito / pytest | 核心 Service 层 ≥ 80% | 不依赖外部服务 |
| **集成测试** | Spring Boot Test + Testcontainers | 关键接口全覆盖 | 真实数据库 + Redis |
| **API 合约测试** | RestAssured | 所有对外 API | 对照 API 规范 V3.0 |
| **推理精度测试** | 自研测试框架 | 每个场景 mAP ≥ 0.85 | 测试数据集 |

### 8.2 测试数据管理

- 集成测试使用 **Testcontainers** 启动临时数据库，测试完成后自动销毁，不污染开发数据库
- 测试 Fixtures 数据使用 `@Sql` 注解或 Flyway `afterMigrate` 插入，不手工维护
- 测试中禁止调用真实外部服务（工业互联网平台、MQTT Broker），必须 Mock

### 8.3 推理服务 Sandbox 测试

新模型在生产环境发布前，必须满足以下 Sandbox 门控：

| 门控项 | 阈值 | 检测方式 |
|--------|------|---------|
| Sandbox 验证时长 | ≥ 48 小时 | 系统自动检查 |
| mAP（验证集） | ≥ 0.85 | 离线评估报告 |
| 推理延迟 P99 | ≤ 50ms（边缘）/ ≤ 20ms（服务器）| 性能测试 |
| 假阳性率变化 | 不超过生产版本 +5% | 对比报告 |
| 四眼审核 | 通过 | 人工审核记录 |

---

## 9. API 开发规范（补充）

> 本章补充 API 接口规范 V3.0 未涵盖的服务端实现约定。

### 9.1 响应时间要求

| 接口类型 | P95 目标 | P99 目标 | 超时处理 |
|---------|---------|---------|---------|
| 简单 CRUD（读） | ≤ 100ms | ≤ 200ms | 超时返回 5008 |
| 复杂查询（分页/统计） | ≤ 500ms | ≤ 1s | 超时返回 5008 |
| 场景启用/停用 | ≤ 300ms | ≤ 500ms | 超时返回 5008 |
| 推理触发（异步） | ≤ 50ms（投递确认）| — | 返回任务 ID |
| SSE 首条数据 | ≤ 500ms | ≤ 1s | — |

### 9.2 幂等性要求

以下操作必须实现幂等，客户端可安全重试：

- `POST /models/{version_id}/approve` — 基于 `version_id + reviewer` 幂等
- `POST /alarms/{alarm_id}/feedback` — 基于 `alarm_id` 幂等（重复提交返回 2008）
- `POST /training/jobs` — 基于 `dataset_code + config_hash` 幂等（返回已存在的作业 ID）
- 所有 Kafka 消费处理 — 基于消息 Key（业务 ID）幂等，使用 Redis 记录已处理 ID

### 9.3 分页与数据量保护

- 单次分页最大返回条数：`size ≤ 100`（超出返回 1003）
- 告警查询最大时间范围：31 天（超出返回 1004）
- TDengine 时序数据查询最大返回：10,000 条（超出时自动降采样返回）
- 禁止提供无分页的批量导出接口，超大导出使用异步任务 + 文件下载模式

### 9.4 API 版本管理

- 当前版本：`/api/v1`
- 版本升级（不兼容变更）时，新旧版本并行运行至少 **90 天**，旧版本通过 `Deprecated` 响应头告知客户端
- 字段新增为向后兼容变更，不升级版本号；字段删除/重命名为不兼容变更，必须升级版本号

---

## 10. AI 推理服务规范

### 10.1 算法插件标准协议

所有接入平台的算法必须遵循 `Image In → JSON Out` 标准插件协议（参见方案 V2.0 第 7.3 节），以下为补充实现约定：

- **健康检查端点**：每个算法容器必须实现 `GET /health`，返回模型加载状态、GPU 内存占用、最近推理延迟
- **预热接口**：启动时调用 `POST /warmup`，确保首帧推理延迟不影响 SLA
- **资源声明**：`plugin_meta` 必须声明所需 GPU 显存（MB），调度器据此分配资源

### 10.2 模型版本控制

- 模型文件命名：`{scene_code}_{algo_type}_{version}.{ext}`，如 `SC-PELLETING-001_detect_v2.3.1.onnx`
- 生产环境最多同时运行 2 个版本（当前版本 + 蓝绿发布中的新版本）
- 模型权重文件存储在 MinIO `models/` bucket，Harbor 存储打包后的容器镜像
- 禁止直接在生产环境覆盖模型文件，所有变更必须创建新版本记录

### 10.3 推理性能基线

| 场景类型 | 硬件 | 目标延迟（P95） | 最低帧率 |
|---------|------|-------------|---------|
| 铸坯表面缺陷（高分辨率） | 服务器 GPU（A100） | ≤ 20ms/帧 | 25 FPS |
| 链篦机侧板跑偏 | 服务器 GPU（T4） | ≤ 30ms/帧 | 15 FPS |
| 设备状态检测（边缘） | Jetson AGX Xavier | ≤ 50ms/帧 | 10 FPS |
| 工艺参数估计（边缘） | 海思 3559 | ≤ 80ms/帧 | 5 FPS |

### 10.4 模型漂移处理规范

当 `drift_metric` 服务检测到漂移时，按以下优先级响应：

```
精度下降 ≥ 10%（连续 3 天）
  → 自动触发 [WARNING] 告警 → 推送 ADMIN
  → 自动创建数据补采工单

精度下降 ≥ 20%（连续 1 天）
  → 自动触发 [CRITICAL] 告警 → 推送 ADMIN + 值班负责人
  → 自动提交重训练作业（使用最近 30 天数据）
  → 场景自动切回上一版本模型（回退保护）
```

---

### 10.5 推理适配器模式（★V1.1）

> **背景**：GPU 服务器采购周期较长，实验室阶段（Sprint 0-4）使用云端视觉 API 替代本地推理；
> Sprint 5 GPU 到位后，通过修改 `algorithm_plugin.infer_backend` 字段完成切换，**无需改动任何业务代码**。

#### 10.5.1 接口定义（Java）

```java
/**
 * 推理适配器统一接口
 * 所有推理后端必须实现此接口，确保对上层业务透明
 */
public interface InferenceAdapter {

    /**
     * 执行推理
     *
     * @param pluginId  算法插件 ID
     * @param imageData 原始帧数据（JPEG/PNG 字节）
     * @param params    推理参数（来自 algorithm_plugin.metadata_json）
     * @return 标准推理结果 JSON（Image In → JSON Out 协议）
     */
    InferResult infer(String pluginId, byte[] imageData, Map<String, Object> params);

    /**
     * 健康检查（供 Redis 健康缓存定时探针调用）
     */
    HealthStatus checkHealth();

    /**
     * 当前适配器支持的推理后端类型
     */
    InferBackend getBackendType();
}

public enum InferBackend {
    LOCAL_GPU,   // 本地 TensorRT / GPU（Sprint 5+）
    CLOUD_API,   // 云端视觉 API（阿里云 / 百度 EasyDL，实验室阶段）
    ONNX_CPU     // CPU ONNX Runtime（无 GPU 降级）
}
```

#### 10.5.2 适配器工厂（Spring Bean 自动选择）

```java
@Component
public class InferenceAdapterFactory {

    private final Map<InferBackend, InferenceAdapter> adapters;

    public InferenceAdapterFactory(List<InferenceAdapter> adapterList) {
        this.adapters = adapterList.stream()
            .collect(Collectors.toMap(InferenceAdapter::getBackendType, Function.identity()));
    }

    /**
     * 根据插件配置选择适配器
     * 优先级：插件级 infer_backend > 全局配置 infer.backend > 默认 ONNX_CPU
     */
    public InferenceAdapter getAdapter(AlgorithmPlugin plugin) {
        InferBackend backend = Optional.ofNullable(plugin.getInferBackend())
            .orElse(globalConfig.getDefaultBackend());

        // 如果目标后端不健康，自动降级到 ONNX_CPU
        if (backend != InferBackend.ONNX_CPU && !isHealthy(backend)) {
            log.warn("Backend {} unhealthy, falling back to ONNX_CPU for plugin {}",
                backend, plugin.getPluginId());
            backend = InferBackend.ONNX_CPU;
        }

        return adapters.get(backend);
    }

    private boolean isHealthy(InferBackend backend) {
        String cacheKey = "tianjing:cloud:proxy:health:" + backend.name().toLowerCase();
        String cached = redisTemplate.opsForValue().get(cacheKey);
        return cached != null && cached.contains("\"status\":\"UP\"");
    }
}
```

#### 10.5.3 三种适配器实现规范

| 适配器 | 类名 | 实现要点 |
|--------|------|---------|
| **Cloud API 适配器** | `CloudVisionInferenceAdapter` | 调用阿里云/百度 EasyDL REST API；超时 3s；失败重试 2 次（仅超时类）；错误响应映射为标准 `InferResult(error=true)` |
| **Local GPU 适配器** | `LocalGpuInferenceAdapter` | 通过 gRPC 调用本地 TensorRT 推理容器；连接池复用；GPU OOM 时抛 `InfrastructureException(code=5003)` |
| **ONNX CPU 适配器** | `OnnxCpuInferenceAdapter` | 使用 ONNX Runtime Java Binding；每个插件独立 Session；首次加载后缓存 Session；无状态线程安全 |

> **降级链**：`CLOUD_API` 失败 → `ONNX_CPU`；`LOCAL_GPU` OOM → `ONNX_CPU`；`ONNX_CPU` 失败 → 返回空结果（不降级，记录 ERROR 日志）

---

### 10.6 录像回放服务规范（★V1.1）

> **背景**：实验室阶段以 MinIO 中的存量录像替代摄像头实时流。Recording Replay Service 读取 mp4 文件，
> 按配置帧率解码后向 `tianjing.frames.prod` Kafka Topic 投递，帧数据格式与真实摄像头完全相同。

#### 10.6.1 Kafka 帧消息格式（与摄像头服务相同）

```json
{
  "scene_id":    "SCENE-SINTER-FIRE-001",
  "device_code": "CAM-SINTER-FIRE-001",
  "timestamp":   "2026-04-15T14:30:00.123Z",
  "frame_seq":   12345,
  "source":      "REPLAY",
  "session_id":  "REPLAY-SCENE-SINTER-FIRE-001-20260415143000",
  "image_base64": "..."
}
```

> **注意**：`source` 字段区分真实摄像头（`LIVE`）和录像回放（`REPLAY`），
> 下游推理服务**必须接受两者**，不得依赖 `source` 字段做业务判断。

#### 10.6.2 帧率控制实现

```python
# recording_replay_service/core/frame_publisher.py

import asyncio
import cv2
from minio import Minio
from kafka import KafkaProducer

async def replay_video(session: ReplaySession, producer: KafkaProducer) -> None:
    """
    按指定帧率解码视频并向 Kafka 投递帧数据。
    使用 asyncio.sleep 控制帧率，避免 CPU 忙等。
    """
    cap = cv2.VideoCapture(download_from_minio(session.minio_video_url))
    frame_interval = 1.0 / session.replay_fps  # 帧间隔（秒）

    frame_seq = 0
    loop_count = 0

    while True:
        ret, frame = cap.read()
        if not ret:
            loop_count += 1
            if session.loop_count > 0 and loop_count >= session.loop_count:
                break  # 达到循环次数，退出
            cap.set(cv2.CAP_PROP_POS_FRAMES, 0)  # 重置到开头
            continue

        start = asyncio.get_event_loop().time()

        # 编码并投递
        _, jpeg = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
        message = build_frame_message(session, frame_seq, jpeg.tobytes())
        producer.send('tianjing.frames.prod', value=message)

        # 更新进度（每 100 帧写一次 DB，避免频繁写）
        frame_seq += 1
        if frame_seq % 100 == 0:
            update_session_progress(session.session_id, frame_seq)

        # 精确帧率控制
        elapsed = asyncio.get_event_loop().time() - start
        sleep_time = max(0, frame_interval - elapsed)
        await asyncio.sleep(sleep_time)

    cap.release()
    mark_session_completed(session.session_id, frame_seq)
```

#### 10.6.3 并发和限流约束

- **同场景互斥**：同一 `scene_id` 同时只允许一个 `RUNNING` 状态的回放会话（DB 唯一索引 `uq_replay_scene_running` 保证）
- **Kafka 限流**：单次回放最大投递速率 **30 fps**（Kafka 生产者配置 `linger.ms=33`），防止 Topic 积压影响真实摄像头数据
- **MinIO 下载**：视频文件先完整下载到本地临时目录 `/tmp/tianjing-replay/`，避免流式解码抖动
- **临时文件清理**：回放结束（`COMPLETED` / `STOPPED` / `FAILED`）后立即删除本地临时文件
- **告警拦截**：回放帧触发的告警必须打上 `is_replay=true` 标记，告警拦截器确保此类告警**不推送 MQTT / 工业互联网**

---

### 10.7 GPU 特性开关模式（★V1.1）

> **原则**：GPU 相关功能（TensorRT 推理、GPU 调度、算力监控）通过 `infer_backend` 字段和 K8s 节点 Label 控制开关，
> Sprint 1-4 代码与 Sprint 5 代码完全相同，仅配置不同。

#### 10.7.1 切换 checklist（GPU 服务器到位后执行）

```sql
-- 步骤 1：更新所有已上线插件的推理后端
UPDATE algorithm_plugin
   SET infer_backend = 'LOCAL_GPU',
       updated_by = 'ops-gpu-migration',
       updated_at = NOW()
 WHERE status = 'ACTIVE'
   AND is_deleted = FALSE;

-- 步骤 2：验证更新（确认无 CLOUD_API 残留）
SELECT plugin_id, infer_backend FROM algorithm_plugin
 WHERE status = 'ACTIVE' AND is_deleted = FALSE;
```

```yaml
# 步骤 3：Nacos 配置更新（tianjing-prod 命名空间）
# 文件：cloud-inference-proxy-prod.yaml
infer:
  backend: LOCAL_GPU   # 从 CLOUD_API 改为 LOCAL_GPU
```

```bash
# 步骤 4：K8s 节点打 GPU Label（触发 GPU 调度器启用）
kubectl label nodes <gpu-node-name> tianjing.io/gpu=true

# 步骤 5：滚动重启推理服务，加载新配置
kubectl rollout restart deployment/cloud-inference-proxy -n tianjing-prod
kubectl rollout restart deployment/route-dispatch-service -n tianjing-prod
```

#### 10.7.2 Feature Flag 命名约定

| Flag Key（Nacos） | 默认值（Sprint 0-4） | Sprint 5 后 | 说明 |
|-------------------|---------------------|-------------|------|
| `infer.backend` | `CLOUD_API` | `LOCAL_GPU` | 全局推理后端 |
| `gpu.scheduling.enabled` | `false` | `true` | K8s GPU 资源请求开关 |
| `tensorrt.warmup.enabled` | `false` | `true` | TensorRT 预热接口调用 |
| `live.stream.enabled` | `false` | `true` | 摄像头实时流（HTTP 501 开关） |

> **约定**：所有与 GPU / 硬件相关的代码路径必须通过上表 Flag 控制，**禁止使用 `if (env == "prod")` 方式判断**。

---

## 附录 A：代码审查检查清单

提交 PR 前，作者自检，Reviewer 复检：

**架构合规**
- [ ] 无跨层调用（Controller → Repository 直调）
- [ ] 业务逻辑在 Service 层，Controller 只做入参/出参转换
- [ ] 新增接口已在 API 规范文档中同步更新

**安全**
- [ ] 无硬编码密码/密钥/IP
- [ ] 所有 SQL 使用参数化查询
- [ ] 日志无敏感信息输出
- [ ] 新增接口有权限注解

**异常处理**
- [ ] 无空的 `catch` 块
- [ ] 业务异常使用统一错误码
- [ ] 基础设施异常已记录 ERROR 日志

**日志**
- [ ] 关键业务操作有 INFO 日志
- [ ] 日志含 trace_id 字段
- [ ] 无 TRACE/DEBUG 日志残留在主干分支

**测试**
- [ ] 新功能有对应单元/集成测试
- [ ] 测试覆盖率不低于当前基线

---

## 附录 B：文档维护说明

| 触发条件 | 文档更新要求 |
|---------|------------|
| 新增微服务 | 更新第 2 章微服务分层结构图 |
| 引入新技术栈 | 更新第 1.1 节技术栈版本基准 |
| 新增/修改错误码 | 同步更新 API 规范 V3.1 第 4 章 |
| 修改数据库表结构 | 同步更新数据库设计文档 V2.1 |
| 安全策略变更 | 更新第 6 章，同步通知全体开发人员 |
| 推理后端切换（Sprint 5 GPU 到位） | 执行 §10.7.1 切换 checklist，更新 §10.3 性能基线实测数据 |
| Cloud API 密钥轮换 | 更新 K8s Secret，无需修改代码或 Nacos 配置 |

> **本文档由项目技术负责人维护，每季度至少审查一次，确保与实际实现保持同步。**

---

*技术规范文档版本：V1.1 · 编制日期：2026-03-31 · 天柱·天镜项目组*

*V1.1：依据 Sprint 计划 V2.0 补充推理适配器模式（§10.5）、录像回放服务规范（§10.6）、GPU 特性开关模式（§10.7），*
*更新 Nacos 配置说明（§7.2.1），补充 CLOUD_VISION_API_KEY 安全注入规范。*
*与方案 V2.0、数据库设计 V2.1、API 规范 V3.1、Sprint 计划 V2.0 保持完全一致。*
