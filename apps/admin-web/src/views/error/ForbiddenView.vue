<template>
  <div class="error-page">
    <el-result
      icon="warning"
      title="403 - 无权限访问"
      sub-title="您没有权限访问此页面，请联系管理员"
    >
      <template #extra>
        <el-button type="primary" @click="router.push('/dashboard')">返回首页</el-button>
        <el-button @click="router.back()">返回上页</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<style scoped lang="scss">
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
