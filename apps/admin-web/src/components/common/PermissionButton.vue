<template>
  <el-button v-if="canShow" v-bind="$attrs" :disabled="disabled || !canAct">
    <slot />
  </el-button>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { UserRole } from '@/types'

const props = withDefaults(
  defineProps<{
    permission?: string
    roles?: UserRole[]
    disabled?: boolean
    hideIfNoPermission?: boolean
  }>(),
  { hideIfNoPermission: false }
)

const authStore = useAuthStore()

const canAct = computed(() => {
  if (props.roles && props.roles.length > 0) {
    return authStore.hasRole(props.roles)
  }
  return true
})

const canShow = computed(() => {
  if (props.hideIfNoPermission) return canAct.value
  return true
})
</script>
