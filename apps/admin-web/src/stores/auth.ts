import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserInfo, UserRole } from '@/types'
import { authApi } from '@/api/auth'
import { tokenHolder } from '@/utils/tokenHolder'

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
    tokenHolder.set(access)
  }

  function clearTokens() {
    accessToken.value = ''
    refreshToken.value = ''
    userInfo.value = null
    tokenHolder.clear()
  }

  async function login(username: string, password: string) {
    const res = await authApi.login({ username, password })
    const loginData = res.data.data as {
      access_token: string
      refresh_token?: string
      user: { user_id: string; username: string; display_name: string; roles: UserRole[] }
    }
    setTokens(loginData.access_token, loginData.refresh_token ?? '')
    // 直接使用登录响应中的用户信息，避免立即调用 /me 接口
    userInfo.value = {
      userId: loginData.user.user_id,
      username: loginData.user.username,
      displayName: loginData.user.display_name,
      roles: loginData.user.roles,
    }
  }

  async function fetchUserInfo() {
    try {
      const res = await authApi.getUserInfo()
      const d = res.data.data as { user_id: string; username: string; display_name: string; roles: UserRole[] }
      userInfo.value = {
        userId: d.user_id,
        username: d.username,
        displayName: d.display_name,
        roles: d.roles,
      }
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
