import request from '@/utils/request'

export const sandboxApi = {
  listSessions: (params?: {
    page?: number
    size?: number
    sceneId?: string
    status?: string
  }) => request.get('/sandbox/sessions', { params }),

  getSession: (sessionId: string) => request.get(`/sandbox/sessions/${sessionId}`),

  createSession: (data: {
    scene_id: string
    production_model_version_id?: string
    experiment_model_version_id: string
  }) => request.post('/sandbox/sessions', data),

  stopSession: (sessionId: string) =>
    request.post(`/sandbox/sessions/${sessionId}/stop`),

  getReport: (sessionId: string) =>
    request.get(`/sandbox/sessions/${sessionId}/report`),

  promote: (sessionId: string, data: { comment?: string }) =>
    request.post(`/sandbox/sessions/${sessionId}/promote`, data),

  /** S3-06: Sandbox 拦截器核心验证（内部接口，调用 alarm-judge-service） */
  interceptVerify: () =>
    request.post('/alarm-judge/internal/sandbox/intercept-verify'),
}
