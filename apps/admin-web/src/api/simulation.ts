import request from '@/utils/request'

export const simulationApi = {
  list: (params?: {
    page?: number
    size?: number
    sceneId?: string
    status?: string
  }) => request.get('/simulations', { params }),

  get: (taskId: string) => request.get(`/simulations/${taskId}`),

  // 发送 snake_case 键名匹配后端 SimulationCreateRequest
  create: (data: {
    sceneId: string
    videoUrl: string
    videoLabel?: string
    pluginId?: string
    frameFps?: number
    extraVideos?: { videoUrl: string; label: string }[]
  }) =>
    request.post('/simulations', {
      scene_id:     data.sceneId,
      video_url:    data.videoUrl,
      video_label:  data.videoLabel ?? 'MIXED',
      plugin_id:    data.pluginId ?? 'CLOUD-PROXY-V1',
      frame_fps:    data.frameFps ?? 1,
      extra_videos: data.extraVideos?.map(v => ({ video_url: v.videoUrl, label: v.label })),
    }),

  /** 向已有任务追加一个视频（分次上传） */
  addVideo: (taskId: string, videoUrl: string, label: string) =>
    request.post(`/simulations/${taskId}/videos`, { video_url: videoUrl, label }),

  /** 查询任务的视频列表 */
  listVideos: (taskId: string) =>
    request.get(`/simulations/${taskId}/videos`),

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
      timeout: 0, // 视频上传不设超时，依赖上传进度判断
      onUploadProgress: (e) => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded * 100) / e.total))
        }
      },
    })
  },
}
