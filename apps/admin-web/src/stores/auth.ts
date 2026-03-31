import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo, UserRole } from '@/types'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  // Token 只存内存，禁止持久化至 localStorage（安全规范）
  const accessToken = ref<string>('')
  const refreshToken = ref<string>('')
  const userInfo = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!accessToken.value)
  const roles = computed(() => userInfo.value?.roles || [])
  const username = computed(() => userInfo.value?.displayName || userInfo.value?.username || '')

  function setTokens(access: string, refresh: string) {
    accessToken.value = access
    refreshToken.value = refresh
  }

  function clearTokens() {
    accessToken.value = ''
    refreshToken.value = ''
    userInfo.value = null
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
