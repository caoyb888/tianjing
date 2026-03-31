# 天柱·天镜 · 工业视觉 AI 推理平台
# API 接口规范文档（API Specification）

> **版本**：V3.1 · **日期**：2026-03-31 · **密级**：内部文件
> **规范标准**：OpenAPI 3.0.3（本文档为人类可读 Markdown 版本，配套机器可读 YAML 版本同步维护）
> **关联文档**：方案 V2.0 · 数据库设计 V2.1 · Sprint 计划 V2.0 · CLAUDE.md

---

## 版本历史

| 版本 | 日期 | 修订说明 |
|------|------|---------|
| V1.0 | 2026-03-30 | 初始版本 |
| V2.0 | 2026-03-31 | 新增 Sandbox、离线仿真、安全网闸审计、设备资产字段等接口 |
| V3.0 | 2026-03-31 | 补齐三处业务闭环缺失：① 训练管理（数据集+训练作业）② 视频流播放 ③ 感知健康上报内部接口 |
| **V3.1** | **2026-03-31** | **依据 Sprint 计划 V2.0 调整：① 新增录像回放服务内部接口（3个）② 视频流播放接口标注 Sprint 5 延期 ③ 新增云端推理代理相关错误码及数据集来源类型 ④ 错误码补充 2015/2016/3012** |

---

## 目录

1. [总体规范](#1-总体规范)
2. [鉴权机制](#2-鉴权机制)
3. [统一响应格式](#3-统一响应格式)
4. [错误码规范](#4-错误码规范)
5. [接口端点汇总](#5-接口端点汇总)
6. [接口详细规范](#6-接口详细规范)
   - 6.1 [鉴权接口](#61-鉴权接口)
   - 6.2 [场景管理](#62-场景管理)
   - 6.3 [设备管理](#63-设备管理)
   - 6.4 [标定管理](#64-标定管理)
   - 6.5 [算法管理](#65-算法管理)
   - 6.6 [模型管理](#66-模型管理)
   - 6.7 [告警管理](#67-告警管理)
   - 6.8 [Sandbox 实验室](#68-sandbox-实验室)
   - 6.9 [离线仿真](#69-离线仿真)
   - 6.10 [训练管理（V3.0 新增）](#610-训练管理v30-新增)
   - 6.11 [模型漂移](#611-模型漂移)
   - 6.12 [数据同步审计](#612-数据同步审计)
   - 6.13 [数据看板](#613-数据看板)
   - 6.14 [系统管理](#614-系统管理)
   - 6.15 [内部服务接口（V3.0 新增）](#615-内部服务接口v30-新增)
7. [核心 Schema 定义](#7-核心-schema-定义)

---

## 1. 总体规范

### 1.1 基础信息

| 项目 | 内容 |
|------|------|
| **Base URL（生产）** | `https://tianjing.tianzhu-steel.internal/api/v1` |
| **Base URL（Staging）** | `https://tianjing-staging.tianzhu-steel.internal/api/v1` |
| **Base URL（本地开发）** | `http://localhost:8080/api/v1` |
| **内部服务 Base URL** | `http://tianjing-internal.svc.cluster.local/internal/v1` |
| **协议** | HTTPS（生产/Staging），HTTP（本地开发） |
| **数据格式** | JSON（Content-Type: application/json） |
| **字符编码** | UTF-8 |
| **时间格式** | ISO 8601，带时区，如 `2026-04-15T08:23:11+08:00` |

### 1.2 请求规范

```
# 标准请求头
Authorization: Bearer <JWT_TOKEN>          # 必须（内部服务接口除外）
Content-Type: application/json             # POST/PUT 请求必须
X-Request-ID: <uuid>                       # 可选，客户端追踪 ID

# 分页查询参数（统一规范）
?page=1&size=20&sort=created_at:desc

# 过滤参数命名规范
?factory_code=SINTER                       # 精确匹配
?keyword=壁条                              # 模糊搜索
?start_time=2026-04-01T00:00:00+08:00     # 时间范围起始
?end_time=2026-04-30T23:59:59+08:00       # 时间范围结束
```

### 1.3 接口分组总览

| 分组 | 端点数 | 主要功能 | V3.1 变化 |
|------|-------|---------|----------|
| 鉴权 | 3 | 登录/刷新/注销 | — |
| 场景管理 | 8 | 场景 CRUD、启停、历史回溯 | — |
| 设备管理 | 5 | 摄像头资产、健康历史 | +1（视频流播放，**Sprint 5 实现**） |
| 标定管理 | 2 | 在线标定、历史查询 | — |
| 算法管理 | 3 | 算法插件注册管理 | — |
| 模型管理 | 5 | MLOps 版本管理、审核发布 | — |
| 告警管理 | 4 | 告警查询、反馈、重推 | — |
| Sandbox 实验室 | 5 | 会话管理、对比报告、转正 | — |
| 离线仿真 | 5 | 工作流调试、视频仿真 | — |
| 训练管理 | 5 | 数据集+训练作业管理 | — |
| 模型漂移 | 2 | 精度趋势、触发重训练 | — |
| 数据同步审计 | 1 | 等保合规审计日志 | — |
| 数据看板 | 3 | 大屏统计、SSE 实时流 | — |
| 系统管理 | 3 | 用户、角色、操作日志 | — |
| **内部服务** | **4** | **感知健康上报 + 录像回放控制** | **✨ +3（V3.1）** |
| **合计** | **58** | | |

> **Sprint 5 延期接口**：`GET /devices/{device_code}/live-stream` 在 Sprint 5（GPU+摄像头部署后）才实现，Sprint 1-4 返回 HTTP 501；实验室阶段标定工具和大屏使用 MinIO 录像截帧替代实时流。

---

## 2. 鉴权机制

### 2.1 认证方式

所有外部接口采用 **JWT Bearer Token**（RS256 算法）认证：

```http
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Token 有效期**：8 小时（生产）/ 24 小时（Staging/Dev）

**内部服务接口**（`/internal/*`）不走 JWT，通过 **Kubernetes NetworkPolicy + Istio mTLS** 在网络层鉴权，仅允许集群内部服务调用，网关层拒绝所有外部请求。

### 2.2 角色权限矩阵

| 角色 | 场景管理 | 设备管理 | 算法/模型 | 模型审核 | Sandbox | 训练管理 | 数据看板 | 系统管理 |
|------|---------|---------|---------|---------|---------|---------|---------|---------|
| **ADMIN** | 读写 | 读写 | 读写 | ✅ 审核 | 读写 | 读写 | 读 | 读写 |
| **SCENE_EDITOR** | 读写 | 读写 | 只读 | — | 只读 | 只读 | 读 | — |
| **MODEL_REVIEWER** | 只读 | 只读 | 只读 | ✅ **审核** | 只读 | 只读 | 读 | — |
| **SANDBOX_OPERATOR** | 只读 | 只读 | 只读 | — | ✅ 读写 | 只读 | 读 | — |
| **VIEWER** | 只读 | 只读 | 只读 | — | 只读 | 只读 | 读 | — |

> **四眼原则**：`MODEL_REVIEWER` 不能审核自己提交的模型版本，系统自动校验，违反时返回 HTTP 403 / 错误码 4004。

### 2.3 Token 获取与刷新流程

```
用户登录
  POST /auth/login { username, password }
  → 返回 access_token（8h）

Token 即将过期时刷新（无需重新登录）
  POST /auth/refresh（携带当前有效 Token）
  → 返回新 access_token（重置 8h）

退出登录
  POST /auth/logout
  → 服务端将 Token 加入黑名单
```

---

## 3. 统一响应格式

### 3.1 成功响应

**所有接口**统一使用以下包装结构，禁止自定义格式：

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | integer | 业务状态码，`0` = 成功 |
| `message` | string | 状态描述 |
| `data` | any | 业务数据，错误时为 `null` |
| `trace_id` | string | Jaeger 链路追踪 ID，全链路唯一，排查问题必备 |

### 3.2 分页响应

分页查询的 `data` 字段结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "total": 128,
    "page": 1,
    "size": 20,
    "items": [ ... ]
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

### 3.3 错误响应

```json
{
  "code": 2005,
  "message": "Sandbox 验证时长不足，当前 36 小时，需要 >= 48 小时",
  "detail": "sandbox_hours=36, required=48",
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

> `detail` 字段仅在 Staging/Dev 环境返回详细信息，生产环境脱敏处理。

---

## 4. 错误码规范

### 4.1 HTTP 状态码与业务码映射原则

| HTTP 状态码 | 业务码范围 | 含义 |
|------------|---------|------|
| 200 OK | code=0 | 成功 |
| 201 Created | code=0 | 创建成功 |
| 400 Bad Request | 1xxx | 请求参数错误（客户端问题） |
| 400 Bad Request | 2xxx | 业务逻辑错误（状态机/前置条件约束） |
| 401 Unauthorized | 4001 / 4002 | 未认证或认证失败 |
| 403 Forbidden | 4003 / 4004 | 权限不足或四眼原则违反 |
| 404 Not Found | 3xxx | 资源不存在 |
| 409 Conflict | 2001 | 乐观锁版本冲突 |
| 500 Internal Server Error | 5xxx | 服务端内部错误 |

### 4.2 完整错误码表

#### 1xxx — 参数错误

| 错误码 | 说明 |
|-------|------|
| 1001 | 请求参数缺失或格式错误（字段级别） |
| 1002 | JSON 格式错误，无法解析请求体 |
| 1003 | 分页参数超出范围（page/size 不合法） |
| 1004 | 时间范围参数错误（start_time > end_time） |
| 1005 | 枚举值不在允许范围内 |

#### 2xxx — 业务逻辑错误

| 错误码 | 说明 |
|-------|------|
| 2001 | 乐观锁版本冲突，请刷新后重试 |
| 2002 | 资源状态不允许此操作（如 ACTIVE 场景不可删除） |
| 2003 | 场景启用前置条件不满足（未绑定摄像头/模型） |
| 2004 | 摄像头绑定冲突（场景已绑定摄像头） |
| 2005 | 模型 Sandbox 验证时长不足（< 48 小时） |
| 2006 | 告警推送状态不为 FAILED，无需重试 |
| 2007 | Sandbox 转正门禁未全部通过（响应附带门禁详情） |
| 2008 | 告警已被反馈，不允许重复提交 |
| 2009 | 仿真任务视频文件不存在或已过期（> 72 小时） |
| 2010 | 数据同步批次完整性校验失败（checksum 不匹配） |
| **2011** | **训练作业对应的数据集版本不存在或已被删除** |
| **2012** | **训练作业所需 GPU 资源超出训练集群配额** |
| **2013** | **视频流转换失败（RTSP 源不可达或格式不支持）** |
| **2014** | **训练作业当前状态不允许取消（已 COMPLETED/FAILED）** |
| **2015** | **录像回放任务不存在或已结束，无法停止** |
| **2016** | **同一场景已有录像回放任务在运行，请先停止当前任务** |

#### 3xxx — 资源不存在

| 错误码 | 说明 |
|-------|------|
| 3001 | 场景不存在 |
| 3002 | 摄像头设备不存在 |
| 3003 | 算法插件不存在 |
| 3004 | 模型版本不存在 |
| 3005 | 告警记录不存在 |
| 3006 | Sandbox 会话不存在 |
| 3007 | 仿真任务不存在 |
| 3008 | 用户不存在 |
| 3009 | 标定记录不存在 |
| **3010** | **数据集不存在** |
| **3011** | **训练作业不存在** |
| **3012** | **录像回放会话不存在** |

#### 4xxx — 权限错误

| 错误码 | 说明 |
|-------|------|
| 4001 | Token 未携带或已过期 |
| 4002 | 用户名或密码错误 |
| 4003 | 当前角色无权执行此操作 |
| 4004 | 违反四眼原则（审核人与提交人不能为同一人） |
| 4005 | 账号已被锁定，请联系管理员 |

#### 5xxx — 服务端内部错误

| 错误码 | 说明 |
|-------|------|
| 5001 | 服务内部错误（通用） |
| 5002 | 数据库连接失败或超时 |
| 5003 | Redis 缓存异常 |
| 5004 | Kafka 消息发送失败 |
| 5005 | MinIO 存储访问失败 |
| 5006 | 推理服务不可用（GPU 资源不足或服务宕机） |
| 5007 | MQTT 推送失败（工业互联网平台不可达） |
| 5008 | 依赖服务超时（gRPC 调用超时） |

---

## 5. 接口端点汇总

### 完整端点一览表（V3.0，共 55 个）

| # | 方法 | 路径 | 说明 | 所需角色 | V3.0 |
|---|------|------|------|---------|------|
| 1 | POST | `/auth/login` | 用户登录 | — | |
| 2 | POST | `/auth/refresh` | 刷新 Token | 已登录用户 | |
| 3 | POST | `/auth/logout` | 退出登录 | 已登录用户 | |
| 4 | GET | `/scenes` | 查询场景列表 | 所有角色 | |
| 5 | POST | `/scenes` | 新增场景 | SCENE_EDITOR+ | |
| 6 | GET | `/scenes/{scene_id}` | 查询场景详情 | 所有角色 | |
| 7 | PUT | `/scenes/{scene_id}` | 更新场景配置 | SCENE_EDITOR+ | |
| 8 | DELETE | `/scenes/{scene_id}` | 删除场景（软删除） | ADMIN | |
| 9 | POST | `/scenes/{scene_id}/enable` | 启用场景 | SCENE_EDITOR+ | |
| 10 | POST | `/scenes/{scene_id}/disable` | 禁用场景 | SCENE_EDITOR+ | |
| 11 | GET | `/scenes/{scene_id}/history` | 配置变更历史 | 所有角色 | |
| 12 | POST | `/scenes/{scene_id}/rollback` | 回滚配置版本 | SCENE_EDITOR+ | |
| 13 | GET | `/devices` | 查询设备列表 | 所有角色 | |
| 14 | POST | `/devices` | 注册摄像头设备 | SCENE_EDITOR+ | |
| 15 | GET | `/devices/{device_code}` | 查询设备详情 | 所有角色 | |
| 16 | PUT | `/devices/{device_code}` | 更新设备信息 | SCENE_EDITOR+ | |
| 17 | GET | `/devices/{device_code}/health` | 设备健康历史 | 所有角色 | |
| **18** | **GET** | **`/devices/{device_code}/live-stream`** | **获取 Web 端播放地址** | **所有角色** | **✨** |
| 19 | GET | `/scenes/{scene_id}/calibrations` | 标定历史 | 所有角色 | |
| 20 | POST | `/scenes/{scene_id}/calibrations` | 提交标定结果 | SCENE_EDITOR+ | |
| 21 | GET | `/algorithms` | 算法插件列表 | 所有角色 | |
| 22 | POST | `/algorithms` | 注册算法插件 | ADMIN | |
| 23 | GET | `/algorithms/{plugin_id}` | 算法插件详情 | 所有角色 | |
| 24 | GET | `/models` | 模型版本列表 | 所有角色 | |
| 25 | GET | `/models/{version_id}` | 模型版本详情 | 所有角色 | |
| 26 | POST | `/models/{version_id}/approve` | 审核通过（四眼原则） | MODEL_REVIEWER+ | |
| 27 | POST | `/models/{version_id}/reject` | 拒绝审核 | MODEL_REVIEWER+ | |
| 28 | POST | `/models/{version_id}/deprecate` | 废弃模型版本 | ADMIN | |
| 29 | GET | `/alarms` | 告警列表 | 所有角色 | |
| 30 | GET | `/alarms/{alarm_id}` | 告警详情 | 所有角色 | |
| 31 | POST | `/alarms/{alarm_id}/feedback` | 提交人工处置反馈 | 所有角色 | |
| 32 | POST | `/alarms/{alarm_id}/retry-push` | 重试告警推送 | SCENE_EDITOR+ | |
| 33 | GET | `/sandbox/sessions` | Sandbox 会话列表 | SANDBOX_OPERATOR+ | |
| 34 | POST | `/sandbox/sessions` | 启动 Sandbox 会话 | SANDBOX_OPERATOR+ | |
| 35 | GET | `/sandbox/sessions/{session_id}` | 会话详情 | SANDBOX_OPERATOR+ | |
| 36 | POST | `/sandbox/sessions/{session_id}/stop` | 停止会话 | SANDBOX_OPERATOR+ | |
| 37 | GET | `/sandbox/sessions/{session_id}/report` | 对比分析报告 | SANDBOX_OPERATOR+ | |
| 38 | POST | `/sandbox/sessions/{session_id}/promote` | 申请模型转正 | SANDBOX_OPERATOR+ | |
| 39 | GET | `/simulations` | 仿真任务列表 | SCENE_EDITOR+ | |
| 40 | POST | `/simulations` | 创建仿真任务 | SCENE_EDITOR+ | |
| 41 | POST | `/simulations/upload-url` | 获取录像上传 URL | SCENE_EDITOR+ | |
| 42 | GET | `/simulations/{task_id}` | 仿真任务详情 | SCENE_EDITOR+ | |
| 43 | POST | `/simulations/{task_id}/cancel` | 取消仿真任务 | SCENE_EDITOR+ | |
| **44** | **GET** | **`/training/datasets`** | **数据集列表** | **SCENE_EDITOR+** | **✨** |
| **45** | **GET** | **`/training/datasets/{dataset_code}`** | **数据集详情** | **SCENE_EDITOR+** | **✨** |
| **46** | **GET** | **`/training/jobs`** | **训练作业列表** | **SCENE_EDITOR+** | **✨** |
| **47** | **POST** | **`/training/jobs`** | **提交训练作业** | **ADMIN** | **✨** |
| **48** | **POST** | **`/training/jobs/{job_id}/cancel`** | **取消训练作业** | **ADMIN** | **✨** |
| 49 | GET | `/drift/metrics` | 精度漂移趋势 | 所有角色 | |
| 50 | POST | `/drift/retrain-trigger` | 手动触发重训练 | ADMIN | |
| 51 | GET | `/audit/data-sync` | 数据同步审计日志 | ADMIN | |
| 52 | GET | `/dashboard/overview` | 平台概览统计 | 所有角色 | |
| 53 | GET | `/dashboard/alarms/realtime` | 实时告警 SSE 流 | 所有角色 | |
| 54 | GET | `/dashboard/inference/trend` | 推理统计趋势 | 所有角色 | |
| 55 | GET | `/system/users` | 用户列表 | ADMIN | |
| 56 | PUT | `/system/users/{user_id}/roles` | 更新用户角色 | ADMIN | |
| 57 | GET | `/system/operation-logs` | 操作审计日志 | ADMIN | |
| **58** | **POST** | **`/internal/devices/health/report`** | **【内部】感知健康批量上报** | **集群内部** | **✨** |
| **59** | **POST** | **`/internal/replay/start`** | **【内部】启动录像回放** | **集群内部** | **✨ V3.1** |
| **60** | **POST** | **`/internal/replay/stop`** | **【内部】停止录像回放** | **集群内部** | **✨ V3.1** |
| **61** | **GET** | **`/internal/replay/status`** | **【内部】查询回放状态** | **集群内部** | **✨ V3.1** |

---

## 6. 接口详细规范

---

### 6.1 鉴权接口

#### `POST /auth/login` — 用户登录

**请求体**

```json
{
  "username": "zhangsan",
  "password": "********"
}
```

**成功响应（200）**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "access_token": "eyJhbGciOiJSUzI1NiJ9...",
    "expires_in": 28800,
    "token_type": "Bearer",
    "user": {
      "user_id": "U20001",
      "username": "zhangsan",
      "display_name": "张三",
      "roles": ["SCENE_EDITOR"]
    }
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

**错误响应**

| HTTP | code | 场景 |
|------|------|------|
| 400 | 1001 | username/password 字段缺失 |
| 401 | 4002 | 用户名或密码错误 |
| 403 | 4005 | 账号已被锁定 |

---

#### `POST /auth/refresh` — 刷新 Token

携带当前有效 Token（即将过期时调用），无需请求体，返回结构同登录。

#### `POST /auth/logout` — 退出登录

无请求体，服务端将当前 Token 加入黑名单，返回 `{"code":0,"message":"success",...}`。

---

### 6.2 场景管理

#### `GET /scenes` — 查询场景列表

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20，最大 100 |
| `sort` | string | 否 | 排序，如 `created_at:desc` |
| `factory_code` | string | 否 | 厂部筛选：PELLET/SINTER/STEEL/SECTION/STRIP |
| `category` | string | 否 | 场景分类：QUALITY_INSPECT/EQUIPMENT_MONITOR/PROCESS_PARAM |
| `status` | string | 否 | 状态：DRAFT/ACTIVE/INACTIVE/SANDBOX_ONLY |
| `priority` | string | 否 | 优先级：P1/P2/P3/P4 |
| `keyword` | string | 否 | 场景名称模糊搜索 |

**成功响应（200）** — 返回分页场景列表，每条 `SceneConfigDetail` 见 [§7 Schema 定义](#7-核心-schema-定义)。

---

#### `POST /scenes` — 新增场景

**所需角色**：SCENE_EDITOR 或 ADMIN

**请求体**（`SceneConfigBase`）

```json
{
  "scene_name": "烧结机壁条脱落堵塞检测",
  "factory_code": "SINTER",
  "process_code": "SINTER_MACHINE",
  "category": "EQUIPMENT_MONITOR",
  "priority": "P1",
  "frame_interval": 5,
  "alarm_config_json": {
    "level": "CRITICAL",
    "confirm_frames": 3,
    "suppress_seconds": 300,
    "push_channels": ["IIOT_PLATFORM", "WECHAT_WORK"],
    "scada_signal": false
  }
}
```

**成功响应（201）** — 返回完整 `SceneConfigDetail`，初始 `status` 为 `DRAFT`。

---

#### `PUT /scenes/{scene_id}` — 更新场景配置

> **乐观锁**：请求体须携带 `version` 字段，与当前数据库版本不一致时返回 **HTTP 409 / code 2001**。
> 变更立即写入 `scene_config_history` 历史表。ACTIVE 场景变更通过 Nacos 事件热加载，**无需重启**。

**请求体** = `SceneConfigBase` + `version`（integer，乐观锁）+ `change_desc`（string，变更说明）

---

#### `POST /scenes/{scene_id}/enable` — 启用场景

启用后系统依次执行：① 建立 RTSP 视频流连接；② 注册路由规则；③ 预热算法模型；④ 更新 Redis 缓存。

**前置条件**：已绑定摄像头 + 已配置生产模型，否则返回 **HTTP 400 / code 2003**。

---

#### `POST /scenes/{scene_id}/rollback` — 回滚配置

**请求体**

```json
{
  "target_version": 2,
  "rollback_reason": "新配置误报率过高，回滚到上一稳定版本"
}
```

---

### 6.3 设备管理

#### `POST /devices` — 注册摄像头设备

> **安全注意**：`rtsp_url` 含密码，服务端 **AES-256 加密存储**，响应时仅返回 host 部分（脱敏）。

**请求体**（`CameraDeviceBase` + `device_code`）

```json
{
  "device_code": "CAM-SINTER-001",
  "device_name": "烧结机1#壁条检测相机",
  "scene_id": "SCENE-SINTER-005",
  "ip_address": "192.168.10.101",
  "mac_address": "AA:BB:CC:DD:EE:01",
  "vendor": "HIKVISION",
  "firmware_version": "V5.7.2_220929",
  "rtsp_url": "rtsp://admin:password@192.168.10.101:554/Streaming/Channels/101",
  "protocol": "RTSP",
  "resolution_width": 1920,
  "resolution_height": 1080,
  "fps": 25,
  "location_desc": "烧结机1#台车下方1.5米",
  "is_supplement_light": true
}
```

---

#### `GET /devices/{device_code}/live-stream` — 获取 Web 端实时流地址 ✨ V3.0 新增

> ⚠️ **Sprint 5 延期实现**（依赖现场摄像头安装与流媒体服务部署）
> Sprint 1–4 实验室阶段此接口返回 **HTTP 501 Not Implemented**，前端展示占位提示"摄像头接入后可用"。
> 在线标定工具在实验室阶段使用 MinIO 录像截帧替代（参见 §6.15 录像回放接口）。
>
> **正式实现说明**：方案 V2.0 规划了实时监控大屏和在线标定工具，两者均需在浏览器内直播摄像头画面。
> 浏览器**无法直接播放 RTSP 流**，必须经后端流媒体服务转换为 WebRTC / HTTP-FLV / HLS 协议。
> 此接口调用流媒体转码服务（如 SRS / ZLMediaKit），按需拉流转换并返回 Web 可播放地址。

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `protocol` | string | 否 | 目标协议：`webrtc`（默认）/ `flv` / `hls` |
| `quality` | string | 否 | 画质：`original`（默认）/ `low`（降码率，标定工具用） |

**成功响应（200）**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "stream_url": "wss://streaming.tianjing.internal/live/CAM-SINTER-001.flv",
    "protocol": "flv",
    "expires_at": "2026-04-15T10:23:11+08:00",
    "session_token": "st_abc123",
    "width": 1920,
    "height": 1080,
    "fps": 25
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

**说明**

| 字段 | 说明 |
|------|------|
| `stream_url` | Web 端播放 URL。WebRTC 格式：`webrtc://...`；FLV 格式：`wss://...` 或 `http://...`；HLS 格式：`https://.../m3u8` |
| `expires_at` | 流地址有效期（1 小时），过期后需重新调用此接口获取新地址 |
| `session_token` | 流会话令牌，停止播放时前端应调用流媒体服务释放，避免资源泄漏 |

**协议选型建议**

| 场景 | 推荐协议 | 原因 |
|------|---------|------|
| 实时监控大屏 | WebRTC | 延迟最低（< 500ms），实时性最佳 |
| 在线标定工具 | FLV（HTTP-FLV） | 兼容性好，延迟可接受（1-3s），无需特殊服务器配置 |
| 历史录像回放 | HLS | 兼容所有设备，延迟不敏感 |

**错误响应**

| HTTP | code | 场景 |
|------|------|------|
| 404 | 3002 | 设备不存在 |
| 400 | 2013 | RTSP 源不可达或摄像头离线 |
| 400 | 2003 | 设备 health_status 为 OFFLINE，无法拉流 |

---

### 6.4 标定管理

#### `POST /scenes/{scene_id}/calibrations` — 提交在线标定结果

现场运维人员在标定工具界面点击参考点后，客户端提交两点像素坐标，服务端自动计算比例尺：

`scale_mm_per_px = ref_distance_mm / sqrt((x2-x1)² + (y2-y1)²)`

提交成功后，该场景原有 `is_active=true` 的标定记录**自动置为 false**，保证同一场景始终只有一条生效标定。

**请求体**

```json
{
  "device_code": "CAM-PELLET-001",
  "ref_distance_mm": 100.0,
  "pixel_p1_x": 240,
  "pixel_p1_y": 360,
  "pixel_p2_x": 1680,
  "pixel_p2_y": 360,
  "remark": "相机移位后重新标定"
}
```

**成功响应（201）** — 返回 `CalibrationRecord`，含计算结果 `scale_mm_per_px`。

---

### 6.5 算法管理

#### `POST /algorithms` — 注册算法插件

**所需角色**：ADMIN

**`ui_schema_json` 字段说明**（V2.0 新增，低代码编排器属性面板动态渲染用）：

```json
{
  "properties": {
    "conf_threshold": {
      "type": "number",
      "title": "置信度阈值",
      "minimum": 0.1,
      "maximum": 1.0,
      "default": 0.85,
      "ui:widget": "slider"
    },
    "roi_enabled": {
      "type": "boolean",
      "title": "启用ROI区域",
      "default": true,
      "ui:widget": "switch"
    },
    "target_classes": {
      "type": "array",
      "title": "检测目标类别",
      "items": { "type": "string" },
      "ui:widget": "tag_select"
    }
  },
  "required": ["conf_threshold"]
}
```

支持的 `ui:widget` 类型：`slider`（滑块）/ `switch`（开关）/ `select`（下拉）/ `tag_select`（标签多选）/ `number_input`（数字输入）/ `text_input`（文字输入）

---

### 6.6 模型管理

#### 模型版本状态机

```
STAGING
  ↓ 开启 Sandbox 验证
SANDBOX_VALIDATING（验证 ≥ 48h，精度提升 ≥ 2%）
  ↓ 申请转正
REVIEWING（等待 MODEL_REVIEWER 四眼审核）
  ↓ 审核通过
PRODUCTION（蓝绿部署上线）

任意状态 → DEPRECATED（废弃）
```

#### `POST /models/{version_id}/approve` — 审核通过

**所需角色**：MODEL_REVIEWER 或 ADMIN

**四眼原则**：审核人不能是该版本的提交者，系统自动校验，违反时返回 HTTP 403 / code 4004。

**前置条件检查**：
- 模型状态必须为 `REVIEWING`
- `sandbox_hours >= 48`
- Sandbox 精度提升 `>= 2%`（`precision_delta >= 0.02`）

**成功响应（200）**

```json
{
  "code": 0,
  "data": {
    "version_id": "MV-ATOM-DETECT-YOLO-V1-20260415-001",
    "status": "PRODUCTION",
    "deployed_at": "2026-04-15T10:30:00+08:00"
  }
}
```

---

### 6.7 告警管理

#### `POST /alarms/{alarm_id}/feedback` — 提交人工处置反馈

此接口是**模型漂移监测的核心数据来源**，通常由工业互联网平台在工单处置后自动回调。

**`feedback_result` 取值含义**

| 值 | 含义 | 对模型精度的影响 |
|----|------|----------------|
| `TRUE_POSITIVE` | 确认为真实异常 | TP+1，精确率不受影响 |
| `FALSE_POSITIVE` | AI 误报 | FP+1，精确率下降 |
| `FALSE_NEGATIVE` | AI 漏检（人工主动发现） | FN+1，召回率下降 |

**请求体**

```json
{
  "feedback_result": "FALSE_POSITIVE",
  "feedback_desc": "现场确认为正常振动导致的图像噪声",
  "actual_anomaly_type": null
}
```

---

### 6.8 Sandbox 实验室

#### Sandbox 工作流程

```
① SCENE_EDITOR 为场景上传候选模型（status=STAGING）
② SANDBOX_OPERATOR 启动 Sandbox 会话
   POST /sandbox/sessions { scene_id, candidate_model_id, mirror_fps:5 }
③ 系统开启 T 型分流（traffic-mirror-service）
   生产流（25fps）→ 生产推理 → 生产告警
   镜像流（5fps）→ Sandbox 推理 → 实验室数据库（不触发任何外部告警）
④ 持续运行 ≥ 48 小时，系统持续生成对比报告
   GET /sandbox/sessions/{session_id}/report
⑤ 门禁全部通过后申请转正
   POST /sandbox/sessions/{session_id}/promote
⑥ MODEL_REVIEWER 审核通过
   POST /models/{version_id}/approve
⑦ 蓝绿部署上线，模型状态 → PRODUCTION
```

#### `POST /sandbox/sessions/{session_id}/promote` — 申请转正

门禁未全部通过时返回 HTTP 400 / code 2007，并在响应中附带各门禁失败原因：

```json
{
  "code": 2007,
  "message": "转正门禁未全部通过",
  "gate_detail_json": {
    "hours_gate": { "pass": false, "hours": 36, "required_hours": 48 },
    "accuracy_gate": { "pass": true, "delta": 0.031, "required_delta": 0.02 },
    "resource_gate": { "pass": true, "mb_diff": 120 },
    "latency_gate": { "pass": true, "ms_diff": 2.1 }
  },
  "trace_id": "abc123"
}
```

---

### 6.9 离线仿真

#### `POST /simulations` — 创建仿真任务

> **重要**：仿真触发的告警**不推送**任何外部系统（MQTT/工业互联网/企业微信），仅用于工作流节点逻辑验证。

**请求体**

```json
{
  "scene_id": "SCENE-SINTER-005",
  "task_name": "4月15日壁条脱落录像验证",
  "video_file_url": "minio://tianjing-sim-temp/user123/sinter_fault_20260415.mp4",
  "use_current_workflow": true
}
```

#### `POST /simulations/upload-url` — 获取录像上传预签名 URL

客户端使用此 URL 直接 PUT 文件到 MinIO，无需经过后端服务中转（节省带宽）。

```json
请求体：{ "filename": "test_video.mp4", "file_size_mb": 512.5 }
响应data：{
  "upload_url": "https://minio.../tianjing-sim-temp/user123/...?X-Amz-Signature=...",
  "video_file_url": "minio://tianjing-sim-temp/user123/test_video.mp4",
  "expires_at": "2026-04-15T11:23:11+08:00"
}
```

---

### 6.10 训练管理（V3.0 新增）

> **补充原因**：数据库设计 V2.0 中明确定义了 `dataset` 和 `train_job` 表，前端训练平台控制面板必须依赖这些接口查询数据集列表、提交训练作业、监控训练进度。V2.0 API 规范中此模块完全缺失，V3.0 补全。
>
> **访问限制**：训练管理接口仅访问 `tianjing_train` 库，通过应用层隔离确保不与 `tianjing_prod` 库交叉。训练 GPU 集群通过 Kubernetes `training` 命名空间物理隔离。

---

#### `GET /training/datasets` — 查询数据集列表

**所需角色**：SCENE_EDITOR 或 ADMIN

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `sort` | string | 否 | 如 `created_at:desc` |
| `scene_id` | string | 否 | 按场景过滤 |
| `factory_code` | string | 否 | 按厂部过滤 |
| `source_type` | string | 否 | 来源过滤：`PRODUCTION_SYNC` / `MANUAL_COLLECT` / `AUGMENTED` |

**成功响应（200）**

```json
{
  "code": 0,
  "data": {
    "total": 32,
    "page": 1,
    "size": 20,
    "items": [
      {
        "dataset_code": "DS-SCENE-SINTER-005-202604",
        "dataset_name": "烧结机壁条2026-04月数据集",
        "scene_id": "SCENE-SINTER-005",
        "factory_code": "SINTER",
        "source_type": "PRODUCTION_SYNC",
        "total_samples": 3840,
        "positive_samples": 612,
        "negative_samples": 3228,
        "minio_prefix": "tianjing-datasets/sintering/SCENE-SINTER-005/2026-04/",
        "created_at": "2026-04-16T01:00:00+08:00",
        "created_by": "SYSTEM"
      }
    ]
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

---

#### `GET /training/datasets/{dataset_code}` — 查询数据集详情

**路径参数**：`dataset_code`（如 `DS-SCENE-SINTER-005-202604`）

**成功响应（200）** — 在列表字段基础上额外返回：

```json
{
  "code": 0,
  "data": {
    "dataset_code": "DS-SCENE-SINTER-005-202604",
    "dataset_name": "烧结机壁条2026-04月数据集",
    "scene_id": "SCENE-SINTER-005",
    "factory_code": "SINTER",
    "source_type": "PRODUCTION_SYNC",
    "total_samples": 3840,
    "positive_samples": 612,
    "negative_samples": 3228,
    "minio_prefix": "tianjing-datasets/sintering/SCENE-SINTER-005/2026-04/",
    "class_distribution": {
      "壁条脱落": 318,
      "壁条堵塞": 176,
      "壁条翘起": 118,
      "正常": 3228
    },
    "sample_image_urls": [
      "https://minio.tianjing.internal/tianjing-datasets/.../sample_001.jpg",
      "https://minio.tianjing.internal/tianjing-datasets/.../sample_002.jpg"
    ],
    "created_at": "2026-04-16T01:00:00+08:00",
    "updated_at": "2026-04-16T01:30:00+08:00",
    "created_by": "SYSTEM"
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

---

#### `GET /training/jobs` — 查询训练作业列表

**所需角色**：SCENE_EDITOR 或 ADMIN

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `page` | integer | 否 | 页码，默认 1 |
| `size` | integer | 否 | 每页条数，默认 20 |
| `sort` | string | 否 | 如 `created_at:desc` |
| `plugin_id` | string | 否 | 按算法插件过滤 |
| `status` | string | 否 | 状态过滤：PENDING / RUNNING / COMPLETED / FAILED / CANCELLED |
| `trigger_type` | string | 否 | 触发方式：MANUAL / AUTO_DRIFT / SCHEDULED |

**成功响应（200）**

```json
{
  "code": 0,
  "data": {
    "total": 15,
    "page": 1,
    "size": 20,
    "items": [
      {
        "job_id": "TJ-ATOM-DETECT-YOLO-V1-202604150830",
        "plugin_id": "ATOM-DETECT-YOLO-V1",
        "dataset_version_id": "DS-SCENE-SINTER-005-202604",
        "trigger_type": "AUTO_DRIFT",
        "drift_metric_id": "DM-SCENE-SINTER-005-20260415",
        "status": "RUNNING",
        "gpu_count": 2,
        "best_epoch": null,
        "best_map50": null,
        "mlflow_run_id": "run_abc123def456",
        "started_at": "2026-04-15T08:32:00+08:00",
        "finished_at": null,
        "created_at": "2026-04-15T08:30:00+08:00",
        "created_by": "SYSTEM"
      }
    ]
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

---

#### `POST /training/jobs` — 提交训练作业

**所需角色**：ADMIN

**请求体**

```json
{
  "plugin_id": "ATOM-DETECT-YOLO-V1",
  "dataset_version_id": "DS-SCENE-SINTER-005-202604",
  "train_config_json": {
    "epochs": 200,
    "batch_size": 16,
    "lr": 0.01,
    "img_size": 640,
    "augment": {
      "hsv_h": 0.015,
      "hsv_s": 0.7,
      "hsv_v": 0.4,
      "flipud": 0.0,
      "fliplr": 0.5
    }
  },
  "gpu_count": 2,
  "trigger_reason": "季节性光照变化导致模型精度下滑，手动触发全量重训练"
}
```

**字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `plugin_id` | string | ✅ | 目标算法插件 ID |
| `dataset_version_id` | string | ✅ | 使用的数据集编码，必须存在于 `tianjing_train.dataset` |
| `train_config_json` | object | ✅ | 训练超参配置，结构见 `train_job` 表注释 |
| `gpu_count` | integer | 否 | 分配 GPU 数，默认 1，最大受训练集群配额限制 |
| `trigger_reason` | string | 否 | 触发原因说明，写入训练作业备注 |

**成功响应（201）**

```json
{
  "code": 0,
  "data": {
    "job_id": "TJ-ATOM-DETECT-YOLO-V1-202604151400",
    "plugin_id": "ATOM-DETECT-YOLO-V1",
    "dataset_version_id": "DS-SCENE-SINTER-005-202604",
    "trigger_type": "MANUAL",
    "status": "PENDING",
    "gpu_count": 2,
    "mlflow_run_id": null,
    "estimated_start_at": "2026-04-15T14:05:00+08:00",
    "created_at": "2026-04-15T14:00:00+08:00",
    "created_by": "admin"
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

**错误响应**

| HTTP | code | 场景 |
|------|------|------|
| 400 | 1001 | 必填字段缺失 |
| 400 | 2011 | `dataset_version_id` 不存在或已删除 |
| 400 | 2012 | 请求 GPU 数量超出训练集群可用配额 |
| 403 | 4003 | 非 ADMIN 角色 |

---

#### `POST /training/jobs/{job_id}/cancel` — 取消训练作业

**所需角色**：ADMIN

**路径参数**：`job_id`（如 `TJ-ATOM-DETECT-YOLO-V1-202604151400`）

> **前置条件**：作业状态必须为 `PENDING` 或 `RUNNING`，`COMPLETED` / `FAILED` 状态不可取消（返回 HTTP 400 / code 2014）。

**请求体**

```json
{
  "cancel_reason": "配置有误，取消后重新提交"
}
```

**成功响应（200）**

```json
{
  "code": 0,
  "data": {
    "job_id": "TJ-ATOM-DETECT-YOLO-V1-202604151400",
    "status": "CANCELLED",
    "cancelled_at": "2026-04-15T14:12:00+08:00"
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

---

### 6.11 模型漂移

#### `GET /drift/metrics` — 查询精度漂移趋势

**查询参数**：`scene_id`（必填）、`start_date`、`end_date`（均为 `date` 格式，如 `2026-04-01`）

精确率连续 3 天低于 85% 时，系统自动触发重训练并在响应的 `alert` 字段中返回预警信息。

#### `POST /drift/retrain-trigger` — 手动触发重训练

**所需角色**：ADMIN

> **注意**：此接口触发的是**模型漂移驱动的增量重训练**，底层实际调用 `POST /training/jobs`（触发方式标记为 `AUTO_DRIFT`）。响应中 `retrain_job_id` 即对应 `train_job.job_id`，可通过 `GET /training/jobs/{job_id}` 查询进度。

---

### 6.12 数据同步审计

#### `GET /audit/data-sync` — 查询数据同步审计日志

**所需角色**：ADMIN（等保合规，仅管理员可查）

**查询参数**：`page`、`size`、`scene_id`、`sync_status`（SUCCESS/PARTIAL/FAILED）、`start_time`、`end_time`、`sync_batch_id`

**安全说明**：审计日志**永久保留，禁止删除**，是等保合规审查时数据跨区流转合规性的核心证据。

---

### 6.13 数据看板

#### `GET /dashboard/alarms/realtime` — 实时告警 SSE 流

返回 `text/event-stream`（Server-Sent Events），大屏实时接收告警推送。

```
event: alarm
data: {"alarm_id":"ALM-20260415-000001","scene_id":"SCENE-SINTER-005","alarm_level":"CRITICAL","anomaly_type":"壁条脱落","confidence":0.93,"factory_code":"SINTER","alarm_at":"2026-04-15T08:23:11+08:00"}

event: heartbeat
data: {"ts":1743500000000}
```

每 30 秒发送 heartbeat 保持连接，客户端断连后 EventSource 自动重连。

---

### 6.14 系统管理

#### `PUT /system/users/{user_id}/roles` — 更新用户角色

**所需角色**：ADMIN

**请求体**

```json
{
  "roles": ["SCENE_EDITOR", "SANDBOX_OPERATOR"]
}
```

传入的 roles 数组为**全量替换**（非增量追加）。

---

### 6.15 内部服务接口（V3.0 新增）

> **访问控制**：内部接口通过 **Kubernetes NetworkPolicy** 和 **Istio mTLS** 限制，仅允许集群内部微服务（`health-monitor-service` 或边缘节点）调用。
> API 网关层**拒绝所有来自集群外部的请求**（返回 404，不暴露接口存在）。
> 无 JWT 鉴权要求，但须通过 Istio 双向 TLS 证书认证。

---

#### `POST /internal/devices/health/report` — 批量上报摄像头健康检测指标 ✨ V3.0 新增

> **补充原因**：感知健康检测服务（`health-monitor-service`）或边缘节点每 30 秒定时采集图像质量指标，需要一个接收接口将检测结果持久化。V2.0 只有查询接口，缺少写入入口。
>
> **架构决策**：内部上报采用 **HTTP + 批量** 方式（而非每条写一次 Kafka），原因：① 每30秒16路摄像头约产生16条记录，频率不高，批量 HTTP 足够；② 减少 Kafka Topic 数量；③ 服务端可在接收时做聚合判断（如连续3次模糊才生成故障记录），无需消费者额外逻辑。

**Base URL**（内部专用）：`http://tianjing-internal.svc.cluster.local/internal/v1`

**请求体** — 支持批量上报，单次最多 50 条

```json
[
  {
    "device_code": "CAM-SINTER-001",
    "scene_id": "SCENE-SINTER-005",
    "check_at": "2026-04-15T08:23:00+08:00",
    "laplacian_var": 312.5,
    "avg_brightness": 128,
    "optical_flow_dx": 0.12,
    "optical_flow_dy": -0.08,
    "health_score": 92,
    "fault_type": null,
    "fault_desc": null
  },
  {
    "device_code": "CAM-PELLET-001",
    "scene_id": "SCENE-PELLET-001",
    "check_at": "2026-04-15T08:23:00+08:00",
    "laplacian_var": 38.2,
    "avg_brightness": 105,
    "optical_flow_dx": 0.05,
    "optical_flow_dy": 0.02,
    "health_score": 41,
    "fault_type": "BLURRY",
    "fault_desc": "拉普拉斯方差 38.2 低于阈值 50，图像模糊"
  }
]
```

**请求体字段说明**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `device_code` | string | ✅ | 摄像头设备编码 |
| `scene_id` | string | ✅ | 所属场景 ID |
| `check_at` | datetime | ✅ | 检测时间戳（毫秒精度） |
| `laplacian_var` | float | ✅ | 拉普拉斯方差，< 100=模糊，< 50=严重模糊 |
| `avg_brightness` | integer | ✅ | 平均亮度 0-255，< 30=黑屏，> 220=过曝 |
| `optical_flow_dx` | float | 否 | 光流 X 方向位移（像素/帧），> ±10=偏移 |
| `optical_flow_dy` | float | 否 | 光流 Y 方向位移（像素/帧） |
| `health_score` | integer | ✅ | 综合评分 0-100，由边缘端计算 |
| `fault_type` | string | 否 | 故障类型：BLURRY / OFFLINE / SHIFTED / LIGHT_FAIL |
| `fault_desc` | string | 否 | 故障详情描述 |

**服务端处理逻辑**

服务端在接收到批量上报后执行以下逻辑：

```
1. 批量写入 camera_health_record 表（分区表，按月）
2. 同时写入 TDengine camera_health_ts 超级表（时序聚合用）
3. 更新 camera_device.health_status 和 health_score 为最新值
4. 判断是否需要触发动作：
   ├─ fault_type == BLURRY  → 推送维修工单到工业互联网平台（MQTT）
   ├─ fault_type == OFFLINE → 发出告警 + 暂停该摄像头的推理任务（熔断）
   └─ fault_type == SHIFTED → 推送重标定工单 + 提示运维人员重新 ROI 标定
5. 触发的动作记录 action_taken 字段并回填 work_order_id
```

**成功响应（200）**

```json
{
  "code": 0,
  "message": "批量上报成功",
  "data": {
    "received_count": 16,
    "fault_count": 1,
    "actions_triggered": [
      {
        "device_code": "CAM-PELLET-001",
        "fault_type": "BLURRY",
        "action": "WORK_ORDER",
        "work_order_id": "WO-20260415-0012"
      }
    ]
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

**错误响应**

| HTTP | code | 场景 |
|------|------|------|
| 400 | 1001 | 必填字段缺失或格式错误 |
| 400 | 1003 | 批量数量超过 50 条限制 |
| 404 | 3002 | 批量中存在不认识的 device_code |

---

### 6.16 录像回放服务接口（V3.1 新增）

> **用途**：实验室阶段（Sprint 1–4）以录像文件替代真实摄像头进行全链路功能验证。回放服务将视频文件解码为视频帧，以与实时摄像头完全相同的 Kafka 消息格式注入推理管道，平台下游无感知。
>
> **访问控制**：内部接口，与 §6.15 相同，仅限集群内部服务调用（NetworkPolicy + Istio mTLS）。
>
> **生命周期**：Sprint 5 摄像头接入生产后，回放服务作为**实验室专用工具保留**，可继续用于新场景调试和回归测试，不废弃。

**Base URL**：`http://tianjing-internal.svc.cluster.local/internal/v1`

---

#### `POST /internal/replay/start` — 启动录像回放 ✨ V3.1 新增

**请求体**

```json
{
  "scene_id": "SCENE-SINTER-FIRE-001",
  "video_file_url": "minio://tianjing-lab-video/sintering/sinter_fire_20260401.mp4",
  "fps": 10,
  "speed": 1.0,
  "loop": true,
  "is_sandbox": false
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `scene_id` | string | ✅ | 绑定场景，帧数据携带此 scene_id 注入 Kafka |
| `video_file_url` | string | ✅ | MinIO 视频文件路径（`minio://` 协议） |
| `fps` | integer | 否 | 抽帧帧率，默认 10fps，范围 1–25 |
| `speed` | float | 否 | 回放速度倍率，默认 1.0，范围 0.5–4.0 |
| `loop` | boolean | 否 | 是否循环回放，默认 true |
| `is_sandbox` | boolean | 否 | true 时帧数据注入沙箱 Topic，默认 false（注入生产 Topic） |

**成功响应（201）**

```json
{
  "code": 0,
  "message": "回放已启动",
  "data": {
    "replay_id": "REPLAY-SINTER-001-20260404",
    "scene_id": "SCENE-SINTER-FIRE-001",
    "status": "RUNNING",
    "kafka_topic": "tianjing.frames.prod",
    "started_at": "2026-04-04T09:00:00+08:00"
  },
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

**错误响应**

| HTTP | code | 场景 |
|------|------|------|
| 400 | 2016 | 同一场景已有回放任务在运行 |
| 404 | 3001 | scene_id 不存在 |
| 400 | 2009 | 视频文件不存在或无法解码 |

---

#### `POST /internal/replay/stop` — 停止录像回放 ✨ V3.1 新增

**请求体**

```json
{ "replay_id": "REPLAY-SINTER-001-20260404" }
```

**成功响应（200）**：返回 `{ "code": 0, "data": { "replay_id": "...", "status": "STOPPED" } }`

**错误响应**：replay_id 不存在返回 HTTP 404 / code 3012；已停止状态返回 HTTP 400 / code 2015。

---

#### `GET /internal/replay/status` — 查询所有活跃回放状态 ✨ V3.1 新增

**成功响应（200）**

```json
{
  "code": 0,
  "data": [
    {
      "replay_id": "REPLAY-SINTER-001-20260404",
      "scene_id": "SCENE-SINTER-FIRE-001",
      "status": "RUNNING",
      "video_file_url": "minio://tianjing-lab-video/...",
      "fps": 10,
      "speed": 1.0,
      "loop": true,
      "is_sandbox": false,
      "frames_published": 18432,
      "started_at": "2026-04-04T09:00:00+08:00",
      "elapsed_seconds": 1843
    }
  ],
  "trace_id": "7b4e2c1a9f3d8b05"
}
```

---

## 7. 核心 Schema 定义

### 7.1 通用枚举值汇总

| 实体 | 字段 | 枚举值 |
|------|------|------|
| `factory_code` | 厂部编码 | PELLET / SINTER / STEEL / SECTION / STRIP |
| `SceneCategory` | 场景分类 | QUALITY_INSPECT / EQUIPMENT_MONITOR / PROCESS_PARAM |
| `ScenePriority` | 优先级 | P1 / P2 / P3 / P4 |
| `SceneStatus` | 场景状态 | DRAFT / ACTIVE / INACTIVE / SANDBOX_ONLY |
| `CameraVendor` | 摄像头厂商 | HIKVISION / DAHUA / UNIVIEW / OTHER |
| `CameraProtocol` | 接入协议 | RTSP / GB28181 |
| `CameraHealth` | 健康状态 | HEALTHY / BLURRY / OFFLINE / SHIFTED / UNKNOWN |
| `PluginType` | 算法类型 | DETECTION / SEGMENTATION / CLASSIFICATION / MEASUREMENT / ENHANCEMENT |
| `ModelStatus` | 模型状态 | STAGING / SANDBOX_VALIDATING / REVIEWING / PRODUCTION / DEPRECATED |
| `ModelFormat` | 导出格式 | ONNX / TENSORRT / TORCHSCRIPT |
| `AlarmLevel` | 告警级别 | INFO / WARNING / CRITICAL |
| `PushStatus` | 推送状态 | PENDING / SENT / FAILED |
| `PushChannel` | 推送通道 | IIOT_PLATFORM / WECHAT_WORK / SMS / WEBSOCKET |
| `FeedbackResult` | 反馈结果 | TRUE_POSITIVE / FALSE_POSITIVE / FALSE_NEGATIVE |
| `SandboxStatus` | 会话状态 | RUNNING / PAUSED / COMPLETED / PROMOTED / ABORTED |
| `PromoteStatus` | 转正状态 | PENDING / APPLIED / APPROVED / REJECTED / PROMOTED |
| `SimulationStatus` | 仿真状态 | PENDING / RUNNING / COMPLETED / FAILED |
| `TrainJobStatus` | 训练状态 | PENDING / RUNNING / COMPLETED / FAILED / CANCELLED |
| `TrainTrigger` | 训练触发方式 | MANUAL / AUTO_DRIFT / SCHEDULED |
| `DatasetSource` | 数据来源 | PRODUCTION_SYNC / MANUAL_COLLECT / AUGMENTED / **LAB_REPLAY**（V3.1 新增，录像标注数据） |
| `ReplayStatus` | 回放状态 | RUNNING / STOPPED / ERROR |
| `InferBackend` | 推理后端类型 | LOCAL_GPU / CLOUD_API / ONNX_CPU（云端代理注册时在 metadata_json 中声明） |
| `StreamProtocol` | 视频流协议 | webrtc / flv / hls |
| `SyncStatus` | 同步状态 | SUCCESS / PARTIAL / FAILED |
| `UserRole` | 系统角色 | ADMIN / SCENE_EDITOR / MODEL_REVIEWER / SANDBOX_OPERATOR / VIEWER |

### 7.2 关键 Schema 概要

#### `SceneConfigDetail`

```
scene_id           : string   场景业务主键 SCENE-{厂部}-{序号}
scene_name         : string   场景名称
factory_code       : string   厂部编码
process_code       : string   工序编码
category           : enum     场景分类
priority           : enum     优先级
status             : enum     场景状态
workflow_json      : object   低代码工作流 JSON {nodes, edges}
algo_config_json   : object   算法配置 {plugin_id, conf_threshold, iou_threshold, extra}
alarm_config_json  : object   告警配置 {level, confirm_frames, suppress_seconds, push_channels, scada_signal}
roi_config_json    : object   ROI 配置 {regions:[{name,x,y,w,h}], calibration_id}
sandbox_enabled    : boolean  是否开启 Sandbox
sandbox_model_id   : string?  候选模型版本 ID
prod_model_id      : string?  当前生产模型 ID
frame_interval     : integer  推理帧间隔（1-25）
version            : integer  乐观锁版本号
created_at / updated_at / created_by / updated_by : 标准审计字段
```

#### `AlgorithmPlugin`（含 V2.0 新增的 `ui_schema_json`）

```
plugin_id          : string   ATOM-{TYPE}-{NAME}-V{N} 或 HEAD-{NAME}-V{N}
plugin_name        : string
plugin_type        : enum     DETECTION/SEGMENTATION/CLASSIFICATION/MEASUREMENT/ENHANCEMENT
is_atom            : boolean  true=原子算法，false=场景任务头
parent_plugin_id   : string?  父原子算法（任务头必填）
version            : string   语义化版本 1.x.x
backbone           : string?  主干网络名称
metadata_json      : object   {supported_scenes, hardware:{min_gpu_vram_gb,supports_tensorrt,supports_onnx}, accuracy:{map50,map50_95,inference_ms_gpu,inference_ms_cpu}}
ui_schema_json     : object   前端动态表单 Schema（JSON Schema Draft-07 子集）
status             : enum     REGISTERED/ACTIVE/DEPRECATED
current_model_version_id : string?
```

#### `ModelVersion`

```
version_id         : string   MV-{plugin_id}-{yyyyMMdd}-{seq}
plugin_id          : string
mlflow_run_id      : string?
model_path         : string   MinIO 路径
export_format      : enum     ONNX/TENSORRT/TORCHSCRIPT
map50 / map50_95 / precision_score / recall_score / inference_ms_gpu / inference_ms_cpu : number?
model_size_mb      : number?
status             : enum     状态机见 §6.6
sandbox_hours      : integer  Sandbox 验证累计小时（需 ≥ 48 才可申请转正）
sandbox_precision / sandbox_recall : number?
approved_by        : string?  四眼原则，不能与提交者相同
approved_at / deployed_at / deprecated_at : datetime?
train_job_id       : string?  来源训练作业 ID
```

#### `Dataset`（V3.0 新增）

```
dataset_code       : string   DS-{scene_id}-{yyyyMM}
dataset_name       : string
scene_id           : string
factory_code       : string
source_type        : enum     PRODUCTION_SYNC/MANUAL_COLLECT/AUGMENTED
total_samples      : integer
positive_samples   : integer  含异常目标的样本数
negative_samples   : integer  无异常的正常样本数
minio_prefix       : string   MinIO 存储路径前缀
class_distribution : object   各类别样本数 {类别名: count}（详情接口返回）
sample_image_urls  : array    预览图 URL 列表（详情接口返回，最多 5 张）
created_at / updated_at / created_by / updated_by : 标准审计字段
```

#### `TrainJob`（V3.0 新增）

```
job_id             : string   TJ-{plugin_id}-{yyyyMMddHHmm}
plugin_id          : string
dataset_version_id : string   使用的数据集编码
trigger_type       : enum     MANUAL/AUTO_DRIFT/SCHEDULED
drift_metric_id    : string?  AUTO_DRIFT 触发时关联的漂移指标 ID
train_config_json  : object   {epochs, batch_size, lr, img_size, augment:{...}}
gpu_count          : integer
status             : enum     PENDING/RUNNING/COMPLETED/FAILED/CANCELLED
best_epoch         : integer? 最优 Epoch
best_map50         : number?  最优 mAP@50
best_map50_95      : number?  最优 mAP@50:95
mlflow_run_id      : string?  MLflow 实验 run_id（可跳转到 MLflow UI 查看详细日志）
error_msg          : string?  失败原因
started_at / finished_at : datetime?
created_at / created_by : 标准审计字段
```

#### `LiveStreamResult`（V3.0 新增）

```
stream_url         : string   Web 端播放 URL（WebRTC/FLV/HLS）
protocol           : enum     webrtc/flv/hls
expires_at         : datetime 流地址有效期（1小时）
session_token      : string   流会话令牌，停止播放时释放
width              : integer  视频宽度（像素）
height             : integer  视频高度（像素）
fps                : integer  实际帧率
```

#### `HealthReportItem`（V3.0 新增，内部接口请求体单元）

```
device_code        : string   ✅ 摄像头设备编码
scene_id           : string   ✅ 所属场景 ID
check_at           : datetime ✅ 检测时间戳
laplacian_var      : float    ✅ 拉普拉斯方差（清晰度）< 100=模糊，< 50=严重模糊
avg_brightness     : integer  ✅ 平均亮度 0-255，< 30=黑屏，> 220=过曝
optical_flow_dx    : float    光流 X 位移，|dx|>10=偏移
optical_flow_dy    : float    光流 Y 位移
health_score       : integer  ✅ 综合评分 0-100
fault_type         : enum?    BLURRY/OFFLINE/SHIFTED/LIGHT_FAIL，正常为 null
fault_desc         : string?  故障描述
```

---

## 附录 A：V3.0 补充接口总结

| 缺失编号 | 接口 | 方法 | 路径 | 端点数 |
|---------|------|------|------|-------|
| 缺失一 | 训练管理 | GET/POST | `/training/datasets` + `/training/jobs` | 5 |
| 缺失二 | 视频流播放 | GET | `/devices/{device_code}/live-stream` | 1 |
| 缺失三 | 感知健康上报 | POST | `/internal/devices/health/report` | 1 |

**新增错误码**：2011、2012、2013、2014、3010、3011（共 6 个，均在 §4.2 完整表中已列出）

---

## 附录 B：MQTT 对接规范（工业互联网平台）

此部分定义平台与工业互联网平台之间的 MQTT 消息格式，非 REST API，归入附录供对接参考。

### 告警上报（平台 → 工业互联网）

**Topic**：`iiot/tianjing/alarm/{factory_code}`（如 `iiot/tianjing/alarm/SINTER`）
**QoS**：1（至少一次送达）

```json
{
  "alarm_id": "ALM-20260415-000001",
  "scene_id": "SCENE-SINTER-005",
  "factory_code": "SINTER",
  "alarm_level": "CRITICAL",
  "anomaly_type": "壁条脱落",
  "confidence": 0.93,
  "measurement_val": null,
  "measurement_unit": null,
  "image_url": "https://minio.tianjing.internal/tianjing-frames-prod/sintering/...",
  "timestamp_ms": 1743500000000,
  "is_sandbox": false
}
```

> **硬性规定**：`is_sandbox=true` 的消息**绝不允许**发送到此 Topic。`alarm-judge-service` 的 Sandbox 拦截器在消息发布前强制校验，不依赖下游过滤。

### 感知健康故障工单上报（平台 → 工业互联网）

**Topic**：`iiot/tianjing/health/{factory_code}`

```json
{
  "work_order_type": "CAMERA_FAULT",
  "device_code": "CAM-PELLET-001",
  "scene_id": "SCENE-PELLET-001",
  "fault_type": "BLURRY",
  "fault_desc": "拉普拉斯方差 38.2 低于阈值 50，图像模糊",
  "health_score": 41,
  "action_required": "清洁镜头或更换相机",
  "timestamp_ms": 1743500000000
}
```

### 处置结果回传（工业互联网 → 平台）

**Endpoint**：`POST /alarms/{alarm_id}/feedback`（REST API，见 §6.7）

---

*API 接口规范文档版本：V3.0 · 编制日期：2026-03-31 · 天柱·天镜项目组*

*本文档依据项目经理组织的两轮专家审查意见迭代完成，V3.0 补齐训练管理（5个端点）、视频流播放（1个端点）、感知健康内部上报（1个端点）三处业务闭环缺失，全文共定义 55 个 REST 端点 + 2 个 MQTT Topic。*

*本文档为 Markdown 可读版本，配套 YAML 机器可读版本（OpenAPI 3.0.3）同步维护，可直接导入 Swagger UI / Apifox / Postman 使用。*
