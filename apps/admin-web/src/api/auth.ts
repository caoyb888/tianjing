import request from '@/utils/request'

export const authApi = {
  login: (data: { username: string; password: string }) =>
    request.post('/auth/login', data),

  refresh: (data: { refresh_token: string }) =>
    request.post('/auth/refresh', data),

  logout: () => request.post('/auth/logout'),

  getUserInfo: () => request.get('/auth/me'),
}
