<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
    <el-breadcrumb-item
      v-for="(item, index) in breadcrumbs"
      :key="index"
      :to="index < breadcrumbs.length - 1 ? { path: item.path } : undefined"
    >
      {{ item.title }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const breadcrumbs = computed(() => {
  const crumbs: Array<{ title: string; path: string }> = []
  route.matched.forEach((r) => {
    if (r.meta.title && r.path !== '/') {
      crumbs.push({ title: r.meta.title as string, path: r.path })
    }
  })
  return crumbs
})
</script>

<style scoped lang="scss">
// 面包屑当前页（最后一级）使用主色
:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--tj-primary);
  font-weight: 500;
}
</style>
