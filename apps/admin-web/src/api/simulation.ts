import request from '@/utils/request'

export const simulationApi = {
  list: (params?: {
    page?: number
    size?: number
    sceneId?: string
    status?: string
  }) => request.get('/simulations', { params }),

  get: (taskId: string) => request.get(`/simulations/${taskId}`),

  // 发送 snake_case 键名匹配后端 SimulationCreateRequest（scene_id / video_url）
  create: (data: { sceneId: string; videoUrl: string }) =>
    request.post('/simulations', { scene_id: data.sceneId, video_url: data.videoUrl }),

  cancel: (taskId: string) => request.post(`/simulations/${taskId}/cancel`),

  exportDataset: (taskId: string, data: {
    datasetVersionId: string
    datasetCode?: string
    confThreshold?: number
    includeNegatives?: boolean
  }) => request.post(`/simulations/${taskId}/export-dataset`, {
    dataset_version_id: data.datasetVersionId,
    dataset_code: data.datasetCode,
    conf_threshold: data.confThreshold ?? 0.7,
    include_negatives: data.includeNegatives ?? true,
  }),

  getExportStatus: (taskId: string) =>
    request.get(`/simulations/${taskId}/export-status`),

  uploadVideo: (file: File, sceneId: string, onProgress?: (percent: number) => void) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('scene_id', sceneId)
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
