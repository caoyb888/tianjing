// 统一响应格式
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  trace_id: string
}

// 分页响应
export interface PageResponse<T> {
  total: number
  page: number
  size: number
  items: T[]
}

// 用户角色枚举
export enum UserRole {
  ADMIN = 'ADMIN',
  SCENE_EDITOR = 'SCENE_EDITOR',
  MODEL_REVIEWER = 'MODEL_REVIEWER',
  SANDBOX_OPERATOR = 'SANDBOX_OPERATOR',
  VIEWER = 'VIEWER',
}

// 告警级别枚举
export enum AlarmLevel {
  CRITICAL = 'CRITICAL',
  WARNING = 'WARNING',
  INFO = 'INFO',
}

// 场景类别
export enum SceneCategory {
  QUALITY = 'quality',
  EQUIPMENT = 'equipment',
  PROCESS = 'process',
}

// 场景状态
export enum SceneStatus {
  ACTIVE = 'active',
  INACTIVE = 'inactive',
  DRAFT = 'draft',
}

// 工厂厂部
export enum Factory {
  PELLET = 'pellet',       // 球团厂
  SINTERING = 'sintering', // 烧结厂
  STEEL = 'steel',         // 炼钢厂
  SECTION = 'section',     // 型钢厂
  STRIP = 'strip',         // 带钢厂
}

// 模型状态（与后端 ModelVersion.status 严格对齐）
// 状态机：STAGING → SANDBOX_VALIDATING → REVIEWING → PRODUCTION → DEPRECATED
// 审核拒绝：REVIEWING → STAGING（回退）；蓝绿切换：PRODUCTION → DEPRECATED
export enum ModelStatus {
  STAGING            = 'STAGING',
  SANDBOX_VALIDATING = 'SANDBOX_VALIDATING',
  REVIEWING          = 'REVIEWING',
  PRODUCTION         = 'PRODUCTION',
  DEPRECATED         = 'DEPRECATED',
}

// 告警场景配置类型
export interface SceneConfig {
  sceneId: string
  factory: Factory
  category: SceneCategory
  status: SceneStatus
  name: string
  description?: string
  algorithmConfig: Record<string, unknown>
  alarmConfig: Record<string, unknown>
  createdAt: string
  updatedAt: string
  createdBy: string
  version: number
}

// 用户信息
export interface UserInfo {
  userId: string
  username: string
  displayName: string
  roles: UserRole[]
  email?: string
}

// 告警检测框
export interface AlarmDetection {
  class_id: number
  class_name: string
  confidence: number
  bbox: { x1: number; y1: number; x2: number; y2: number }
}

// 告警记录
export interface AlarmRecord {
  alarmId: string
  sceneId: string
  factory: Factory
  alarmLevel: AlarmLevel
  anomalyType: string
  confidence: number
  imageUrl: string
  timestamp: string
  isSandbox: boolean
  /** 推送状态：PENDING / SUCCESS / FAILED / INTERCEPTED */
  pushStatus?: string
  /** 人工处置时间 */
  feedbackAt?: string
  feedbackStatus?: 'pending' | 'TRUE_POSITIVE' | 'FALSE_POSITIVE' | 'FALSE_NEGATIVE'
  detections?: AlarmDetection[]
}

// 设备信息
export interface DeviceInfo {
  deviceCode: string
  deviceName: string
  sceneId: string
  factory: Factory
  ipAddress: string
  streamUrl?: string
  status: 'online' | 'offline' | 'warning'
  lastHeartbeat: string
}

// 算法插件
export interface AlgorithmPlugin {
  pluginId: string
  name: string
  version: string
  type: 'detection' | 'segmentation' | 'classification' | 'measurement' | 'enhancement'
  supportedScenes: string[]
  accuracyMetrics: {
    map50?: number
    map50_95?: number
    inferenceMs: number
  }
  description?: string           // 算法简要描述
  businessDimension?: string     // 适合的业务维度
}

// 模型版本
export interface ModelVersion {
  versionId: string
  pluginId: string
  version: string
  status: ModelStatus
  submittedBy?: string
  approvedBy?: string
  createdAt: string
  sandboxSessionId?: string
}

// Sandbox 会话
export interface SandboxSession {
  sessionId: string
  sceneId: string
  pluginId: string
  status: 'running' | 'completed' | 'failed'
  startTime: string
  endTime?: string
  precision?: number
  recall?: number
  comparedToProduction?: boolean
}

// 仿真视频（与后端 SimulationVideo 对应）
export interface SimulationVideo {
  id?: number
  taskId: string
  videoUrl: string
  videoName?: string
  sortOrder: number
  label: 'NORMAL' | 'ABNORMAL' | 'MIXED'
  status: string
  totalFrames?: number
  matchedAlarms?: number
  errorMsg?: string
}

// 仿真任务（字段名与后端 SimulationTask 实体对齐）
export interface SimulationTask {
  taskId: string
  sceneId: string
  taskName: string
  videoFileUrl: string          // 对应实体 videoFileUrl
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  progress?: number             // 仿真执行进度 0-100，RUNNING 时有值
  totalFrames?: number
  matchedAlarms?: number
  falseAlarmCount?: number
  errorMsg?: string
  createdAt: string
  finishedAt?: string           // 对应实体 finishedAt，不是 completedAt
  // 扩展字段
  videos?: SimulationVideo[]    // 关联视频列表
  reviewProgress?: number       // 审核进度 0-100
}

// 训练数据集
export interface TrainingDataset {
  datasetCode: string
  name: string
  factory: Factory
  imageCount: number
  annotationCount: number
  versions: string[]
  createdAt: string
}

// 训练作业（与后端 TrainJob 实体对齐）
// 状态机：PENDING → RUNNING → COMPLETED / FAILED / CANCELLED
export interface TrainingJob {
  jobId: string
  pluginId: string
  datasetVersionId: string
  triggerType?: string
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  bestEpoch?: number
  bestMap50?: number
  bestMap5095?: number
  mlflowRunId?: string
  modelVersionId?: string
  errorMsg?: string
  startedAt?: string
  finishedAt?: string
  createdAt: string
  createdBy?: string
}

// 路由元信息扩展
declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    roles?: UserRole[]
    icon?: string
    keepAlive?: boolean
    hidden?: boolean
  }
}
