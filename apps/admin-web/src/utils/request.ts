import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import type { ApiResponse } from '@/types'

// 配置 NProgress
NProgress.configure({ showSpinner: false })

const request = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    NProgress.start()
    // Token 注入（从 authStore 获取，避免循环依赖用动态导入）
    const token = localStorage.getItem('_tj_access_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
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
      // 处理业务错误
      ElMessage.error(data.message || '操作失败')
      return Promise.reject(new Error(data.message))
    }
    return response
  },
  async (error) => {
    NProgress.done()
    if (error.response?.status === 401) {
      // Token 过期，尝试刷新
      const refreshToken = localStorage.getItem('_tj_refresh_token')
      if (refreshToken) {
        try {
          const res = await axios.post('/api/v1/auth/refresh', { refresh_token: refreshToken })
          if (res.data.code === 0) {
            localStorage.setItem('_tj_access_token', res.data.data.access_token)
            // 重试原始请求
            error.config.headers.Authorization = `Bearer ${res.data.data.access_token}`
            return request(error.config)
          }
        } catch {
          // 刷新失败，跳转登录
        }
      }
      localStorage.removeItem('_tj_access_token')
      localStorage.removeItem('_tj_refresh_token')
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
