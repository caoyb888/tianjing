import request from '@/utils/request'

export const systemApi = {
  listUsers: (params?: {
    page?: number
    size?: number
    role?: string
    keyword?: string
  }) => request.get('/system/users', { params }),

  listRoles: () => request.get('/system/roles'),

  getOperationLogs: (params?: {
    page?: number
    size?: number
    operator?: string
    action?: string
    startTime?: string
    endTime?: string
  }) => request.get('/system/logs', { params }),
}
