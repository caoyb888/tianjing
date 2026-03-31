import request from '@/utils/request'

export const auditApi = {
  getDataSyncLogs: (params?: {
    page?: number
    size?: number
    startTime?: string
    endTime?: string
    operator?: string
  }) => request.get('/audit/data-sync', { params }),
}
