import request from '@/utils/request'

export const calibrationApi = {
  submit: (sceneId: string, data: Record<string, unknown>) =>
    request.post(`/calibration/${sceneId}`, data),

  getHistory: (sceneId: string, params?: { page?: number; size?: number }) =>
    request.get(`/calibration/${sceneId}`, { params }),
}
