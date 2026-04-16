import request from '@/utils/request'

export const systemApi = {
  listUsers: (params?: {
    page?: number
    size?: number
    role?: string
    keyword?: string
  }) => request.get('/system/users', { params }),

  createUser: (data: {
    username: string
    display_name: string
    password: string
    roles?: string[]
    email?: string
    dept_code?: string
  }) => request.post('/system/users', data),

  deleteUser: (userId: string) =>
    request.delete(`/system/users/${userId}`),

  updateUserRoles: (userId: string, data: { roles: string[] }) =>
    request.put(`/system/users/${userId}/roles`, data),

  listRoles: () => request.get('/system/roles'),

  getOperationLogs: (params?: {
    page?: number
    size?: number
    operator?: string
    action?: string
    startTime?: string
    endTime?: string
  }) => request.get('/system/operation-logs', { params }),
}
