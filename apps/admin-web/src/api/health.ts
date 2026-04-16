import request from '@/utils/request'

export const healthApi = {
  // 获取所有摄像头健康概览
  getCameraOverview: (params?: { factory?: string; status?: string }) =>
    request.get('/health-monitor/cameras', { params }),

  // 获取单个摄像头健康历史（7天）
  getCameraHistory: (
    cameraId: string,
    params?: { days?: number; start_time?: string; end_time?: string }
  ) => request.get(`/health-monitor/cameras/${cameraId}/history`, { params }),

  // 获取感知健康汇总统计
  getSummary: () => request.get('/health-monitor/summary'),

  // 提交维修工单
  submitRepairTicket: (cameraId: string, data: { fault_type: string; description?: string }) =>
    request.post(`/health-monitor/cameras/${cameraId}/repair-ticket`, data),
}
