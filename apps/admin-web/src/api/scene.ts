import request from '@/utils/request'

export const sceneApi = {
  list: (params?: {
    page?: number
    size?: number
    factory?: string
    category?: string
    status?: string
    keyword?: string
    sort?: string
  }) => request.get('/scenes', { params }),

  get: (sceneId: string) => request.get(`/scenes/${sceneId}`),

  create: (data: Record<string, unknown>) => request.post('/scenes', data),

  update: (sceneId: string, data: Record<string, unknown>) =>
    request.put(`/scenes/${sceneId}`, data),

  delete: (sceneId: string) => request.delete(`/scenes/${sceneId}`),

  enable: (sceneId: string) => request.post(`/scenes/${sceneId}/enable`),

  disable: (sceneId: string) => request.post(`/scenes/${sceneId}/disable`),

  getHistory: (sceneId: string, params?: { page?: number; size?: number }) =>
    request.get(`/scenes/${sceneId}/history`, { params }),

  rollback: (sceneId: string, data: { version: number }) =>
    request.post(`/scenes/${sceneId}/rollback`, data),
}
