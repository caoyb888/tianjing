import request from '@/utils/request'

export const trainingApi = {
  listDatasets: (params?: {
    page?: number
    size?: number
    factory?: string
    keyword?: string
  }) => request.get('/training/datasets', { params }),

  getDataset: (datasetCode: string) =>
    request.get(`/training/datasets/${datasetCode}`),

  listJobs: (params?: {
    page?: number
    size?: number
    sceneId?: string
    status?: string
  }) => request.get('/training/jobs', { params }),

  getJob: (jobId: string) => request.get(`/training/jobs/${jobId}`),

  submitJob: (data: Record<string, unknown>) =>
    request.post('/training/jobs', data),

  cancelJob: (jobId: string) => request.post(`/training/jobs/${jobId}/cancel`),

  listDatasetVersions: () => request.get('/training/dataset-versions'),
}
