import axios from 'axios'

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 10_000,
})

export interface OverviewData {
  active_scenes: number
  online_devices: number
  today_alarms: number
  today_inferences: number
  critical_count: number
  warning_count: number
  info_count: number
}

export interface TrendPoint {
  time: string
  count: number
  alarms: number
}

export interface AlarmItem {
  alarmId: string
  alarmLevel: 'CRITICAL' | 'WARNING' | 'INFO'
  anomalyType: string
  sceneId: string
  factory: string
  confidence: number
  timestamp: string
  isSandbox: boolean
}

export const monitorApi = {
  getOverview: () => request.get<{ data: OverviewData }>('/dashboard/overview'),

  getInferenceTrend: (days = 7) =>
    request.get<{ data: TrendPoint[] }>('/dashboard/inference-trend', {
      params: { interval: '1d', days },
    }),

  getRecentAlarms: (size = 20) =>
    request.get<{ data: { items: AlarmItem[] } }>('/alarms', {
      params: { page: 1, size, sort: 'created_at:desc', isSandbox: false },
    }),
}
