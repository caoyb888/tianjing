import request from '@/utils/request'

export const simulationApi = {
  list: (params?: {
    page?: number
    size?: number
    sceneId?: string
    status?: string
  }) => request.get('/simulations', { params }),

  get: (taskId: string) => request.get(`/simulations/${taskId}`),

  create: (data: Record<string, unknown>) => request.post('/simulations', data),

  cancel: (taskId: string) => request.post(`/simulations/${taskId}/cancel`),

  uploadVideo: (file: File, onProgress?: (percent: number) => void) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/simulations/upload-video', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded * 100) / e.total))
        }
      },
    })
  },
}
