<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="420px"
    :close-on-click-modal="false"
  >
    <div class="confirm-content">
      <el-icon class="confirm-icon" :class="type"><component :is="iconMap[type]" /></el-icon>
      <div class="confirm-message">{{ message }}</div>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button :type="type === 'danger' ? 'danger' : 'primary'" :loading="loading" @click="handleConfirm">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Warning, CircleCheck, InfoFilled } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    title?: string
    message: string
    type?: 'warning' | 'danger' | 'info'
  }>(),
  { title: '确认操作', type: 'warning' }
)

const emit = defineEmits<{ confirm: [] }>()

const visible = defineModel<boolean>('visible', { default: false })
const loading = ref(false)

const iconMap = {
  warning: Warning,
  danger: Warning,
  info: InfoFilled,
  success: CircleCheck,
}

async function handleConfirm() {
  loading.value = true
  try {
    emit('confirm')
  } finally {
    loading.value = false
    visible.value = false
  }
}
</script>

<style scoped lang="scss">
.confirm-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.confirm-icon {
  font-size: 24px;
  flex-shrink: 0;

  &.warning { color: #e6a23c; }
  &.danger { color: #f56c6c; }
  &.info { color: #909399; }
}

.confirm-message {
  font-size: 14px;
  color: #606266;
  line-height: 1.5;
}
</style>
