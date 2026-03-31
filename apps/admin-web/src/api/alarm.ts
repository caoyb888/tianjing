import request from '@/utils/request'

export const alarmApi = {
  list: (params?: {
    page?: number
    size?: number
    sceneId?: string
    factory?: string
    level?: string
    isSandbox?: boolean
    startTime?: string
    endTime?: string
    sort?: string
  }) => request.get('/alarms', { params }),

  get: (alarmId: string) => request.get(`/alarms/${alarmId}`),

  submitFeedback: (
    alarmId: string,
    data: { feedback_type: 'confirm' | 'reject'; comment?: string }
  ) => request.post(`/alarms/${alarmId}/feedback`, data),

  retryPush: (alarmId: string) => request.post(`/alarms/${alarmId}/retry-push`),
}
