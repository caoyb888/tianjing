import request from '@/utils/request'

export const deviceApi = {
  list: (params?: {
    page?: number
    size?: number
    factory?: string
    status?: string
    keyword?: string
  }) => request.get('/devices', { params }),

  get: (deviceCode: string) => request.get(`/devices/${deviceCode}`),

  register: (data: Record<string, unknown>) => request.post('/devices', data),

  update: (deviceCode: string, data: Record<string, unknown>) =>
    request.put(`/devices/${deviceCode}`, data),

  delete: (deviceCode: string) => request.delete(`/devices/${deviceCode}`),

  getHealthHistory: (
    deviceCode: string,
    params?: { start_time?: string; end_time?: string; page?: number; size?: number }
  ) => request.get(`/devices/${deviceCode}/health-history`, { params }),

  // Sprint 5 实现，当前返回 501
  getLiveStream: (deviceCode: string) =>
    request.get(`/devices/${deviceCode}/live-stream`),
}
