import request from '@/utils/request'

export const modelApi = {
  list: (params?: {
    page?: number
    size?: number
    pluginId?: string
    status?: string
    sceneId?: string
  }) => request.get('/models', { params }),

  get: (versionId: string) => request.get(`/models/${versionId}`),

  submit: (data: Record<string, unknown>) => request.post('/models', data),

  approve: (versionId: string, data: { approved: boolean; comment?: string }) =>
    request.post(`/models/${versionId}/approve`, data),

  promote: (versionId: string) => request.post(`/models/${versionId}/promote`),
}
