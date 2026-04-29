<template>
  <div class="sandbox-detail" v-loading="loading">
    <PageHeader title="实验会话详情" :description="`会话 ID：${sessionId}`">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-if="session?.status === 'RUNNING'" type="warning" @click="stopSession">停止会话</el-button>
        <el-button v-if="session?.status === 'COMPLETED'" type="primary" @click="$router.push(`/sandbox/sessions/${sessionId}/report`)">查看对比报告</el-button>
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
        :striped="session.status === 'RUNNING'"
        :striped-flow="session.status === 'RUNNING'"
        :duration="10"
      />
      <div class="progress-footer">
        <span>开始：{{ formatDateTime(session.startAt) }}</span>
        <span v-if="session.endAt">结束：{{ formatDateTime(session.endAt) }}</span>
        <span v-else-if="session.status === 'RUNNING'" class="running-tip">运行中（每分钟自动刷新）</span>
      </div>
    </el-card>

    <el-row :gutter="16" v-if="session" style="margin-top: 16px">
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">基本信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="会话ID">{{ session.sessionId }}</el-descriptions-item>
            <el-descriptions-item label="场景">{{ session.sceneId }}</el-descriptions-item>
            <el-descriptions-item label="生产模型">{{ session.prodModelId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="候选模型">{{ session.candidateModelId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态"><StatusBadge :status="session.status" /></el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatDateTime(session.startAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="session.endAt" label="结束时间">{{ formatDateTime(session.endAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">精度指标</span></template>
          <div v-if="session.totalFrames" class="metric-grid">
            <div class="metric-item">
              <div class="metric-value">{{ session.totalFrames }}</div>
              <div class="metric-label">处理帧数</div>
            </div>
            <div class="metric-item">
              <div class="metric-value">{{ session.totalAnomalyFrames ?? 0 }}</div>
              <div class="metric-label">异常帧数</div>
            </div>
          </div>
          <EmptyState v-else description="实验进行中，待完成后查看精度指标" />
        </el-card>
      </el-col>
    </el-row>

    <!-- SandboxCompareViewer：双路检测框叠加对比（S3-06 验收标准） -->
    <el-card shadow="never" style="margin-top: 16px" v-if="session">
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between">
          <span class="card-title">双路推理实时对比</span>
          <div style="display: flex; gap: 8px; align-items: center">
            <el-tag v-if="liveConnected" type="success" size="small">● 实时接收中</el-tag>
            <el-tag v-else type="info" size="small">等待 Sandbox 帧...</el-tag>
            <el-tag type="danger" size="small">SANDBOX — 绝不触发告警</el-tag>
          </div>
        </div>
      </template>
      <SandboxCompareViewer
        :image-url="compareImageUrl"
        :production-detections="prodDetections"
        :sandbox-detections="sandboxDetections"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SandboxCompareViewer from '@/components/business/SandboxCompareViewer.vue'
import { sandboxApi } from '@/api/sandbox'
import { formatDateTime, formatPercent } from '@/utils/format'
import type { SandboxSession } from '@/types'

// Sandbox 转正门禁：连续验证 ≥ 48h（CLAUDE.md §11.3）
const GATE_HOURS = 48

interface DetectionBox {
  x1: number; y1: number; x2: number; y2: number
  class_name?: string; confidence?: number
}

const route = useRoute()
const sessionId = route.params.sessionId as string
const session = ref<SandboxSession | null>(null)
const loading = ref(false)
// 当前时间，running 状态下每分钟更新，驱动 elapsedHours 重新计算
const now = ref(Date.now())
let ticker: ReturnType<typeof setInterval> | null = null

// SandboxCompareViewer 数据（SSE 实时接收）
const compareImageUrl = ref('')
const prodDetections = ref<DetectionBox[]>([])
const sandboxDetections = ref<DetectionBox[]>([])
const liveConnected = ref(false)
let sseSource: EventSource | null = null

/** 已运行小时数（整数，最大 48） */
const elapsedHours = computed(() => {
  if (!session.value) return 0
  const start = new Date(session.value.startAt).getTime()
  const end = session.value.endAt
    ? new Date(session.value.endAt).getTime()
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
  if (session.value.status === 'FAILED') return 'exception'
  if (progressPercent.value >= 100) return 'success'
  return undefined
})

/** 门禁标签文案与颜色 */
const gateTagType = computed(() => {
  if (!session.value) return 'info'
  if (session.value.status === 'FAILED') return 'danger'
  if (progressPercent.value >= 100) return 'success'
  if (session.value.status === 'RUNNING') return 'warning'
  return 'info'
})

const gateTagLabel = computed(() => {
  if (!session.value) return ''
  if (session.value.status === 'FAILED') return '验证失败'
  if (progressPercent.value >= 100) return '门禁已达标 ✓'
  if (session.value.status === 'RUNNING') return '验证中'
  return '已停止'
})

async function loadSession() {
  loading.value = true
  try {
    const res = await sandboxApi.getSession(sessionId)
    session.value = res.data.data
    // running 状态启动定时器 + SSE compare-live
    if (session.value?.status === 'RUNNING') {
      if (!ticker) ticker = setInterval(() => { now.value = Date.now() }, 60_000)
      startCompareLive()
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
  closeCompareLive()
  loadSession()
}

function startCompareLive() {
  if (sseSource) return
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  // compare-live SSE（由 compare-dashboard-service 提供）
  sseSource = new EventSource(`${baseUrl}/sandbox/sessions/${sessionId}/compare-live`)
  sseSource.addEventListener('connected', () => { liveConnected.value = true })
  sseSource.addEventListener('sandbox-frame', (e: MessageEvent) => {
    try {
      const data = JSON.parse(e.data)
      if (data.image_url) compareImageUrl.value = `/minio-frames/${data.image_url.replace('minio://', '')}`
      sandboxDetections.value = (data.sandbox_detections ?? []).map((d: Record<string, unknown>) => ({
        x1: (d.bbox as Record<string, number>)?.x1 ?? 0,
        y1: (d.bbox as Record<string, number>)?.y1 ?? 0,
        x2: (d.bbox as Record<string, number>)?.x2 ?? 0,
        y2: (d.bbox as Record<string, number>)?.y2 ?? 0,
        class_name: d.class_name as string,
        confidence: d.confidence as number,
      }))
    } catch { /* 忽略解析失败 */ }
  })
  sseSource.onerror = () => { liveConnected.value = false }
}

function closeCompareLive() {
  sseSource?.close()
  sseSource = null
  liveConnected.value = false
}

function clearTicker() {
  if (ticker) { clearInterval(ticker); ticker = null }
}

onMounted(loadSession)
onUnmounted(() => { clearTicker(); closeCompareLive() })
</script>

<style scoped lang="scss">
// Sandbox 警示横幅（橙色条，区别于生产环境）
.sandbox-banner {
  background: linear-gradient(90deg, #E6A23C, #F5A623);
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  padding: 8px 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
  border-radius: var(--tj-radius-sm);
}

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
  color: var(--tj-primary);
  line-height: 1;
}
.hours-unit {
  font-size: 14px;
  color: var(--tj-text-regular);
}
.progress-hint {
  font-size: 12px;
  color: var(--tj-text-secondary);
}
.progress-footer {
  display: flex;
  gap: 24px;
  margin-top: 8px;
  font-size: 12px;
  color: var(--tj-text-secondary);
}
.running-tip {
  color: var(--tj-warning);
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
  color: var(--tj-primary);
}
.metric-label {
  font-size: 13px;
  color: var(--tj-text-secondary);
  margin-top: 4px;
}
.card-title { font-weight: 600; }

// 注意：.sandbox-bbox 虚线框样式在 index.scss 中，此处不得触碰
</style>
