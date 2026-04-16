import request from '@/utils/request'

export const driftApi = {
  getMetrics: (params?: {
    sceneId?: string
    startDate?: string
    endDate?: string
  }) => request.get('/drift/metrics', { params }),

  triggerRetrain: (data: { sceneId: string; reason?: string }) =>
    request.post('/drift/retrain', data),
}
