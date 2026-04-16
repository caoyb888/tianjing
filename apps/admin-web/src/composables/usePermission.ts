import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { PERMISSION_MATRIX } from '@/constants'
import { UserRole } from '@/types'

export function usePermission() {
  const authStore = useAuthStore()

  const hasPermission = (permission: string): boolean => {
    const allowedRoles = PERMISSION_MATRIX[permission as keyof typeof PERMISSION_MATRIX]
    if (!allowedRoles || allowedRoles.length === 0) return true
    return authStore.roles.some((role) => allowedRoles.includes(role as UserRole))
  }

  const hasRole = (role: UserRole | UserRole[]): boolean => {
    const roles = Array.isArray(role) ? role : [role]
    return authStore.roles.some((r) => roles.includes(r as UserRole))
  }

  const isAdmin = computed(() => hasRole(UserRole.ADMIN))
  const canEdit = computed(() => hasRole([UserRole.ADMIN, UserRole.SCENE_EDITOR]))

  return { hasPermission, hasRole, isAdmin, canEdit }
}
