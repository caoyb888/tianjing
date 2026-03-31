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

// 模型状态
export enum ModelStatus {
  PENDING_REVIEW = 'pending_review',
  SANDBOX_TESTING = 'sandbox_testing',
  APPROVED = 'approved',
  REJECTED = 'rejected',
  PRODUCTION = 'production',
  DEPRECATED = 'deprecated',
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
  feedbackStatus?: 'pending' | 'confirmed' | 'rejected'
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
}

// 模型版本
export interface ModelVersion {
  versionId: string
  pluginId: string
  version: string
  status: ModelStatus
  submittedBy: string
  reviewedBy?: string
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

// 仿真任务
export interface SimulationTask {
  taskId: string
  sceneId: string
  videoUrl: string
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled'
  progress: number
  createdAt: string
  completedAt?: string
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

// 训练作业
export interface TrainingJob {
  jobId: string
  datasetCode: string
  sceneId: string
  status: 'queued' | 'running' | 'completed' | 'failed' | 'cancelled'
  progress: number
  currentEpoch?: number
  totalEpochs?: number
  metrics?: {
    map50?: number
    loss?: number
  }
  createdAt: string
  completedAt?: string
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
