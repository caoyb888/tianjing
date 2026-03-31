import { useAuthStore } from '@/stores/auth'
import { PERMISSION_MATRIX } from '@/constants'
import type { UserRole } from '@/types'

// 检查当前用户是否有指定权限
export function hasPermission(permission: string): boolean {
  const authStore = useAuthStore()
  const allowedRoles = PERMISSION_MATRIX[permission as keyof typeof PERMISSION_MATRIX]
  if (!allowedRoles || allowedRoles.length === 0) return true
  return authStore.roles.some((role) => allowedRoles.includes(role as UserRole))
}

// 检查是否有指定角色
export function hasRole(role: UserRole | UserRole[]): boolean {
  const authStore = useAuthStore()
  const roles = Array.isArray(role) ? role : [role]
  return authStore.roles.some((r) => roles.includes(r as UserRole))
}
