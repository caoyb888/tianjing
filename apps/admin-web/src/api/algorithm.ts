import request from '@/utils/request'

export const algorithmApi = {
  list: (params?: {
    page?: number
    size?: number
    type?: string
    keyword?: string
  }) => request.get('/algorithms', { params }),

  get: (pluginId: string) => request.get(`/algorithms/${pluginId}`),

  register: (data: Record<string, unknown>) => request.post('/algorithms', data),

  healthCheck: (pluginId: string) => request.get(`/algorithms/${pluginId}/health-check`),
}
