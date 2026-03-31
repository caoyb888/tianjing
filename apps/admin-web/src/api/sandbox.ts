import request from '@/utils/request'

export const sandboxApi = {
  listSessions: (params?: {
    page?: number
    size?: number
    sceneId?: string
    status?: string
  }) => request.get('/sandbox/sessions', { params }),

  getSession: (sessionId: string) => request.get(`/sandbox/sessions/${sessionId}`),

  createSession: (data: Record<string, unknown>) =>
    request.post('/sandbox/sessions', data),

  stopSession: (sessionId: string) =>
    request.post(`/sandbox/sessions/${sessionId}/stop`),

  getReport: (sessionId: string) =>
    request.get(`/sandbox/sessions/${sessionId}/report`),

  promote: (sessionId: string, data: { comment?: string }) =>
    request.post(`/sandbox/sessions/${sessionId}/promote`, data),
}
