<template>
  <el-tag :type="tagType" :size="size" :effect="effect">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  status: string
  map?: Record<string, { label: string; type: '' | 'success' | 'warning' | 'danger' | 'info' }>
  size?: 'large' | 'default' | 'small'
  effect?: 'dark' | 'light' | 'plain'
}

const props = withDefaults(defineProps<Props>(), {
  size: 'small',
  effect: 'light',
})

const defaultMap: Record<string, { label: string; type: '' | 'success' | 'warning' | 'danger' | 'info' }> = {
  active: { label: '运行中', type: 'success' },
  inactive: { label: '已停用', type: 'info' },
  draft: { label: '草稿', type: '' },
  online: { label: '在线', type: 'success' },
  offline: { label: '离线', type: 'danger' },
  warning: { label: '告警', type: 'warning' },
  running: { label: '运行中', type: 'success' },
  completed: { label: '已完成', type: 'success' },
  failed: { label: '失败', type: 'danger' },
  pending: { label: '等待中', type: 'info' },
  cancelled: { label: '已取消', type: 'info' },
  queued: { label: '排队中', type: '' },
}

const currentMap = computed(() => props.map || defaultMap)
const currentConfig = computed(() => currentMap.value[props.status] || { label: props.status, type: '' as const })
const label = computed(() => currentConfig.value.label)
const tagType = computed(() => currentConfig.value.type)
</script>
