<template>
  <div class="sandbox-detail" v-loading="loading">
    <PageHeader title="实验会话详情" :description="`会话 ID：${sessionId}`">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-if="session?.status === 'running'" type="warning" @click="stopSession">停止会话</el-button>
        <el-button v-if="session?.status === 'completed'" type="primary" @click="$router.push(`/sandbox/sessions/${sessionId}/report`)">查看对比报告</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" v-if="session">
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">基本信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="会话ID">{{ session.sessionId }}</el-descriptions-item>
            <el-descriptions-item label="场景">{{ session.sceneId }}</el-descriptions-item>
            <el-descriptions-item label="算法插件">{{ session.pluginId }}</el-descriptions-item>
            <el-descriptions-item label="状态"><StatusBadge :status="session.status" /></el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatDateTime(session.startTime) }}</el-descriptions-item>
            <el-descriptions-item v-if="session.endTime" label="结束时间">{{ formatDateTime(session.endTime) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">精度指标</span></template>
          <div v-if="session.precision !== undefined" class="metric-grid">
            <div class="metric-item">
              <div class="metric-value">{{ formatPercent(session.precision) }}</div>
              <div class="metric-label">精确率 (Precision)</div>
            </div>
            <div class="metric-item">
              <div class="metric-value">{{ formatPercent(session.recall!) }}</div>
              <div class="metric-label">召回率 (Recall)</div>
            </div>
          </div>
          <EmptyState v-else description="实验进行中，待完成后查看精度指标" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { sandboxApi } from '@/api/sandbox'
import { formatDateTime, formatPercent } from '@/utils/format'
import type { SandboxSession } from '@/types'

const route = useRoute()
const sessionId = route.params.sessionId as string
const session = ref<SandboxSession | null>(null)
const loading = ref(false)

async function loadSession() {
  loading.value = true
  try {
    const res = await sandboxApi.getSession(sessionId)
    session.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function stopSession() {
  await sandboxApi.stopSession(sessionId)
  ElMessage.success('会话已停止')
  loadSession()
}

onMounted(loadSession)
</script>

<style scoped lang="scss">
.metric-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  padding: 16px 0;
}
.metric-item {
  text-align: center;
}
.metric-value {
  font-size: 32px;
  font-weight: 700;
  color: #1890ff;
}
.metric-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.card-title { font-weight: 600; }
</style>
