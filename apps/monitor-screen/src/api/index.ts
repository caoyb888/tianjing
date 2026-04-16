import axios from 'axios'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10_000,
})

// ── 筛选参数类型 ─────────────────────────────────────────
export interface FilterParams {
  factory?: string      // 厂部编码：PELLET/SINTER/STEEL/SECTION/STRIP
  sceneId?: string      // 场景ID
  levels?: string[]     // 告警级别多选：['CRITICAL', 'WARNING', 'INFO']
}

// ── 场景选项类型 ─────────────────────────────────────────
export interface SceneOption {
  sceneId: string
  sceneName: string
  factory: string
}

// ── 厂部汇总类型 ─────────────────────────────────────────
export interface FactorySummary {
  factory: string
  factoryName: string
  todayAlarms: number
  activeScenes: number
  onlineDevices: number
  todayInferences: number
}

export interface OverviewData {
  active_scenes: number
  online_devices: number
  today_alarms: number
  today_inferences: number
  critical_alarms: number
  warning_alarms: number
  info_alarms: number
  total_scenes?: number
  avg_infer_latency_ms?: number
}

export interface TrendPoint {
  time: string
  count: number
  alarms: number
  avg_latency_ms?: number
}

export interface AlarmItem {
  alarmId: string
  alarmLevel: 'CRITICAL' | 'WARNING' | 'INFO'
  anomalyType: string
  sceneId: string
  sceneName?: string     // 新增：场景中文名
  factory: string
  confidence: number
  timestamp: string
  isSandbox: boolean
}

// ── 厂部常量定义 ─────────────────────────────────────────
export const FACTORY_OPTIONS = [
  { value: '', label: '全部厂部' },
  { value: 'PELLET', label: '球团厂' },
  { value: 'SINTER', label: '烧结厂' },
  { value: 'STEEL', label: '炼钢厂' },
  { value: 'SECTION', label: '型钢厂' },
  { value: 'STRIP', label: '带钢厂' },
] as const

export const LEVEL_OPTIONS = [
  { value: 'CRITICAL', label: '严重', color: '#ff4d4f' },
  { value: 'WARNING', label: '警告', color: '#faad14' },
  { value: 'INFO', label: '信息', color: '#597ef7' },
] as const

export const monitorApi = {
  // 概览统计（支持厂部/场景筛选）
  getOverview: (params?: FilterParams) =>
    request.get<{ data: OverviewData }>('/dashboard/overview', { params }),

  // 厂部汇总统计（热力柱图）
  getFactorySummary: () =>
    request.get<{ data: FactorySummary[] }>('/dashboard/factory-summary'),

  // 推理趋势（支持厂部/场景筛选）
  getInferenceTrend: (days = 7, params?: FilterParams) =>
    request.get<{ data: TrendPoint[] }>('/dashboard/inference-trend', {
      params: { days, ...params },
    }),

  // 实时告警列表（支持厂部/场景/级别筛选）
  getRecentAlarms: (size = 20, params?: FilterParams) =>
    request.get<{ data: { items: AlarmItem[] } }>('/alarms', {
      params: {
        page: 1,
        size,
        sort: 'created_at:desc',
        isSandbox: false,
        factory: params?.factory || undefined,
        scene_id: params?.sceneId || undefined,
        levels: params?.levels?.join(',') || undefined,
      },
    }),

  // 获取活跃场景列表（用于场景下拉联动）
  getActiveScenes: (factory?: string) =>
    request.get<{ data: { items: SceneOption[] } }>('/scenes', {
      params: { factory, status: 'ACTIVE', page: 1, size: 100 },
    }),
}
