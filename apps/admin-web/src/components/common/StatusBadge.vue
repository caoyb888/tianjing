<template>
  <el-tag :type="tagType" :size="size" :effect="effect">{{ label }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  status: string
  map?: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }>
  size?: 'large' | 'default' | 'small'
  effect?: 'dark' | 'light' | 'plain'
}

const props = withDefaults(defineProps<Props>(), {
  size: 'small',
  effect: 'dark',  // 统一使用 dark 效果
})

// 统一使用 effect="dark" + 调整颜色语义
const defaultMap: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  // 运行态
  active: { label: '运行中', type: 'success' },
  running: { label: '运行中', type: 'success' },
  online: { label: '在线', type: 'success' },
  completed: { label: '已完成', type: 'success' },
  // 停止态
  inactive: { label: '已停用', type: 'info' },
  offline: { label: '离线', type: 'danger' },
  cancelled: { label: '已取消', type: 'info' },
  // 中间态
  pending: { label: '等待中', type: 'warning' },
  queued: { label: '排队中', type: 'warning' },
  draft: { label: '草稿', type: 'info' },
  // 异常态
  failed: { label: '失败', type: 'danger' },
  warning: { label: '告警', type: 'warning' },
  // 告警级别（AlarmListView 使用）
  CRITICAL: { label: 'CRITICAL', type: 'danger' },
  WARNING: { label: 'WARNING', type: 'warning' },
  INFO: { label: 'INFO', type: 'info' },
}

const currentMap = computed(() => props.map || defaultMap)
const currentConfig = computed(() => currentMap.value[props.status] || { label: props.status, type: 'info' as const })
const label = computed(() => currentConfig.value.label)
const tagType = computed(() => currentConfig.value.type)
</script>

<style scoped lang="scss">
// CRITICAL 标签加粗加边框强调
:deep(.el-tag--danger.el-tag--dark) {
  font-weight: 600;
  border: 1px solid var(--tj-critical);
}
</style>
