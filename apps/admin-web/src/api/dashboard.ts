import request from '@/utils/request'

export const dashboardApi = {
  getOverview: () => request.get('/dashboard/overview'),

  getInferenceTrend: (params?: {
    sceneId?: string
    factory?: string
    interval?: string
    startTime?: string
    endTime?: string
  }) => request.get('/dashboard/inference-trend', { params }),

  // SSE 实时告警流（不通过 axios，直接用 EventSource）
  getRealtimeAlarmsUrl: () => {
    const token = localStorage.getItem('_tj_access_token')
    return `/api/v1/dashboard/alarms/realtime?token=${token}`
  },
}
