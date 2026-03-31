import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import type { ApiResponse } from '@/types'

NProgress.configure({ showSpinner: false })

// 生成简单 trace_id（UUID v4 简化版）
function generateTraceId(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    NProgress.start()
    // Token 从 authStore 获取（内存存储，禁止 localStorage）
    // 动态导入避免循环依赖
    try {
      const { useAuthStore } = require('@/stores/auth')
      const authStore = useAuthStore()
      if (authStore.accessToken) {
        config.headers.Authorization = `Bearer ${authStore.accessToken}`
      }
    } catch {}
    // 每个请求注入 trace_id
    config.headers['X-Trace-Id'] = generateTraceId()
    return config
  },
  (error) => {
    NProgress.done()
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    NProgress.done()
    const { data } = response
    if (data.code !== 0) {
      ElMessage.error(data.message || '操作失败')
      return Promise.reject(new Error(data.message))
    }
    return response
  },
  async (error) => {
    NProgress.done()
    if (error.response?.status === 401) {
      // Token 过期，尝试静默刷新
      try {
        const { useAuthStore } = require('@/stores/auth')
        const authStore = useAuthStore()
        if (authStore.refreshToken) {
          const res = await axios.post('/api/v1/auth/refresh', {
            refresh_token: authStore.refreshToken,
          })
          if (res.data.code === 0) {
            authStore.setTokens(res.data.data.access_token, res.data.data.refresh_token)
            error.config.headers.Authorization = `Bearer ${res.data.data.access_token}`
            return request(error.config)
          }
        }
        authStore.clearTokens()
      } catch {}
      window.location.href = '/login'
    } else if (error.response?.status === 403) {
      ElMessage.error('权限不足')
    } else if (error.response?.status >= 500) {
      ElMessage.error('服务器错误，请稍后重试')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
