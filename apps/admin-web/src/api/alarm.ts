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
    // S2-09：三选项 TRUE_POSITIVE / FALSE_POSITIVE / FALSE_NEGATIVE
    data: { feedback_type: 'TRUE_POSITIVE' | 'FALSE_POSITIVE' | 'FALSE_NEGATIVE'; comment?: string }
  ) => request.post(`/alarms/${alarmId}/feedback`, data),

  retryPush: (alarmId: string) => request.post(`/alarms/${alarmId}/retry-push`),
}
