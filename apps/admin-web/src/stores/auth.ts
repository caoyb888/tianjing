import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo, UserRole } from '@/types'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  // 注意：Token 存储在 localStorage，但 Store 中用 ref 作为响应式副本
  const accessToken = ref<string>(localStorage.getItem('_tj_access_token') || '')
  const refreshToken = ref<string>(localStorage.getItem('_tj_refresh_token') || '')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!accessToken.value)
  const roles = computed(() => userInfo.value?.roles || [])
  const username = computed(() => userInfo.value?.displayName || userInfo.value?.username || '')

  function setTokens(access: string, refresh: string) {
    accessToken.value = access
    refreshToken.value = refresh
    localStorage.setItem('_tj_access_token', access)
    localStorage.setItem('_tj_refresh_token', refresh)
  }

  function clearTokens() {
    accessToken.value = ''
    refreshToken.value = ''
    userInfo.value = null
    localStorage.removeItem('_tj_access_token')
    localStorage.removeItem('_tj_refresh_token')
  }

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    setTokens(res.data.data.access_token, res.data.data.refresh_token)
    await fetchUserInfo()
  }

  async function fetchUserInfo() {
    try {
      const res = await authApi.getUserInfo()
      userInfo.value = res.data.data
    } catch {
      clearTokens()
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } finally {
      clearTokens()
    }
  }

  // 检查角色权限
  function hasRole(role: UserRole | UserRole[]): boolean {
    const checkRoles = Array.isArray(role) ? role : [role]
    return roles.value.some((r) => checkRoles.includes(r as UserRole))
  }

  return {
    accessToken,
    refreshToken,
    userInfo,
    isLoggedIn,
    roles,
    username,
    setTokens,
    clearTokens,
    login,
    fetchUserInfo,
    logout,
    hasRole,
  }
})
