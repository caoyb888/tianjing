<template>
  <div class="sandbox-detail" v-loading="loading">
    <PageHeader title="实验会话详情" :description="`会话 ID：${sessionId}`">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-if="session?.status === 'running'" type="warning" @click="stopSession">停止会话</el-button>
        <el-button v-if="session?.status === 'completed'" type="primary" @click="$router.push(`/sandbox/sessions/${sessionId}/report`)">查看对比报告</el-button>
      </template>
    </PageHeader>

    <!-- SandboxProgressPanel：小时数 / 48h 门禁进度条（S2-10 验收标准） -->
    <el-card shadow="never" class="progress-panel" v-if="session">
      <div class="progress-header">
        <div class="progress-hours">
          <span class="hours-value">{{ elapsedHours }}</span>
          <span class="hours-unit">/ 48 小时</span>
          <el-tag :type="gateTagType" size="small" style="margin-left: 12px">
            {{ gateTagLabel }}
          </el-tag>
        </div>
        <span class="progress-hint">Sandbox 转正门禁：连续验证 ≥ 48h（CLAUDE.md §11.3）</span>
      </div>
      <el-progress
        :percentage="progressPercent"
        :stroke-width="14"
        :status="progressStatus"
        :striped="session.status === 'running'"
        :striped-flow="session.status === 'running'"
        :duration="10"
      />
      <div class="progress-footer">
        <span>开始：{{ formatDateTime(session.startTime) }}</span>
        <span v-if="session.endTime">结束：{{ formatDateTime(session.endTime) }}</span>
        <span v-else-if="session.status === 'running'" class="running-tip">运行中（每分钟自动刷新）</span>
      </div>
    </el-card>

    <el-row :gutter="16" v-if="session" style="margin-top: 16px">
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { sandboxApi } from '@/api/sandbox'
import { formatDateTime, formatPercent } from '@/utils/format'
import type { SandboxSession } from '@/types'

// Sandbox 转正门禁：连续验证 ≥ 48h（CLAUDE.md §11.3）
const GATE_HOURS = 48

const route = useRoute()
const sessionId = route.params.sessionId as string
const session = ref<SandboxSession | null>(null)
const loading = ref(false)
// 当前时间，running 状态下每分钟更新，驱动 elapsedHours 重新计算
const now = ref(Date.now())
let ticker: ReturnType<typeof setInterval> | null = null

/** 已运行小时数（整数，最大 48） */
const elapsedHours = computed(() => {
  if (!session.value) return 0
  const start = new Date(session.value.startTime).getTime()
  const end = session.value.endTime
    ? new Date(session.value.endTime).getTime()
    : now.value
  return Math.min(Math.floor((end - start) / 3_600_000), GATE_HOURS)
})

/** 门禁进度百分比（0-100） */
const progressPercent = computed(() =>
  Math.min(Math.round((elapsedHours.value / GATE_HOURS) * 100), 100)
)

/** el-progress status 属性 */
const progressStatus = computed(() => {
  if (!session.value) return undefined
  if (session.value.status === 'failed') return 'exception'
  if (progressPercent.value >= 100) return 'success'
  return undefined
})

/** 门禁标签文案与颜色 */
const gateTagType = computed(() => {
  if (!session.value) return 'info'
  if (session.value.status === 'failed') return 'danger'
  if (progressPercent.value >= 100) return 'success'
  if (session.value.status === 'running') return 'warning'
  return 'info'
})

const gateTagLabel = computed(() => {
  if (!session.value) return ''
  if (session.value.status === 'failed') return '验证失败'
  if (progressPercent.value >= 100) return '门禁已达标 ✓'
  if (session.value.status === 'running') return '验证中'
  return '已停止'
})

async function loadSession() {
  loading.value = true
  try {
    const res = await sandboxApi.getSession(sessionId)
    session.value = res.data.data
    // running 状态启动定时器，否则清除
    if (session.value?.status === 'running') {
      if (!ticker) ticker = setInterval(() => { now.value = Date.now() }, 60_000)
    } else {
      clearTicker()
    }
  } finally {
    loading.value = false
  }
}

async function stopSession() {
  await sandboxApi.stopSession(sessionId)
  ElMessage.success('会话已停止')
  loadSession()
}

function clearTicker() {
  if (ticker) { clearInterval(ticker); ticker = null }
}

onMounted(loadSession)
onUnmounted(clearTicker)
</script>

<style scoped lang="scss">
.progress-panel {
  margin-bottom: 0;
}
.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.progress-hours {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.hours-value {
  font-size: 36px;
  font-weight: 700;
  color: #1890ff;
  line-height: 1;
}
.hours-unit {
  font-size: 14px;
  color: #606266;
}
.progress-hint {
  font-size: 12px;
  color: #909399;
}
.progress-footer {
  display: flex;
  gap: 24px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
.running-tip {
  color: #e6a23c;
}
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
