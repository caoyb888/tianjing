<template>
  <div class="search-bar">
    <el-form :inline="true" :model="modelValue" @submit.prevent="handleSearch">
      <slot />
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { Search, Refresh } from '@element-plus/icons-vue'

const emit = defineEmits<{
  search: []
  reset: []
}>()

defineProps<{
  modelValue?: Record<string, unknown>
}>()

function handleSearch() {
  emit('search')
}

function handleReset() {
  emit('reset')
}
</script>

<style scoped lang="scss">
.search-bar {
  background: #fff;
  padding: 16px 16px 0;
  border-radius: 4px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}
</style>
