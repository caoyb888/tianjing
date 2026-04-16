import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types'

/**
 * v-permission 指令
 * 用法：v-permission="'ADMIN'" 或 v-permission="['ADMIN', 'SCENE_EDITOR']"
 * 无权限时隐藏元素（display:none）
 */
export const permissionDirective: Directive = {
  mounted(el: HTMLElement, binding: DirectiveBinding<UserRole | UserRole[]>) {
    checkPermission(el, binding)
  },
  updated(el: HTMLElement, binding: DirectiveBinding<UserRole | UserRole[]>) {
    checkPermission(el, binding)
  },
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding<UserRole | UserRole[]>) {
  const authStore = useAuthStore()
  const requiredRoles = Array.isArray(binding.value) ? binding.value : [binding.value]
  if (requiredRoles.length === 0) return
  if (!authStore.hasRole(requiredRoles)) {
    el.style.display = 'none'
  } else {
    el.style.display = ''
  }
}
