<template>
  <div class="empty-state">
    <el-empty :description="description" :image-size="imageSize">
      <slot />
    </el-empty>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  description?: string
  imageSize?: number
}>(), {
  description: '暂无数据',
  imageSize: 120,
})
</script>

<style scoped lang="scss">
.empty-state {
  padding: var(--tj-space-xl) 0;
  display: flex;
  justify-content: center;
}

// 空状态图标与文字色调整
:deep(.el-empty__description) {
  color: var(--tj-text-secondary);
  font-size: var(--tj-font-sm);
}
</style>
