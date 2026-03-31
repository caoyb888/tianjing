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
  padding: 40px 0;
  display: flex;
  justify-content: center;
}
</style>
