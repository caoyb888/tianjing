<template>
  <div class="job-detail" v-loading="loading">
    <PageHeader :title="`训练作业 ${jobId}`" description="训练进度与评估指标">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button
          v-if="job?.status === 'RUNNING' || job?.status === 'PENDING'"
          type="warning"
          @click="cancelJob"
        >
          取消作业
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" v-if="job">
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">作业信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="作业ID">{{ job.jobId }}</el-descriptions-item>
            <el-descriptions-item label="插件ID">{{ job.pluginId }}</el-descriptions-item>
            <el-descriptions-item label="数据集版本">{{ job.datasetVersionId }}</el-descriptions-item>
            <el-descriptions-item label="触发方式">{{ job.triggerType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态"><StatusBadge :status="job.status" /></el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(job.createdAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="job.startedAt" label="开始时间">
              {{ formatDateTime(job.startedAt) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="job.finishedAt" label="完成时间">
              {{ formatDateTime(job.finishedAt) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="job.errorMsg" label="错误信息">
              <span class="text-danger">{{ job.errorMsg }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">训练指标</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="最佳 Epoch">
              {{ job.bestEpoch ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="最佳 mAP@50">
              {{ job.bestMap50 != null ? (job.bestMap50 * 100).toFixed(2) + '%' : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="最佳 mAP@50-95">
              {{ job.bestMap5095 != null ? (job.bestMap5095 * 100).toFixed(2) + '%' : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="MLflow Run ID">
              <span v-if="job.mlflowRunId" class="mono">{{ job.mlflowRunId }}</span>
              <span v-else class="text-secondary">训练完成后自动填写</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { trainingApi } from '@/api/training'
import { formatDateTime } from '@/utils/format'
import type { TrainingJob } from '@/types'

const route = useRoute()
const jobId = route.params.jobId as string
const job = ref<TrainingJob | null>(null)
const loading = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function loadJob() {
  if (!job.value) loading.value = true
  try {
    const res = await trainingApi.getJob(jobId)
    job.value = res.data.data
    if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(job.value?.status ?? '')) {
      stopPolling()
    }
  } finally {
    loading.value = false
  }
}

async function cancelJob() {
  await trainingApi.cancelJob(jobId)
  ElMessage.success('作业已取消')
  loadJob()
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

onMounted(() => {
  loadJob()
  pollTimer = setInterval(loadJob, 5000)
})
onUnmounted(stopPolling)
</script>

<style scoped lang="scss">
.card-title { font-weight: 600; }
.text-secondary { color: #909399; font-size: 13px; }
.text-danger { color: #f56c6c; font-size: 13px; }
.mono { font-family: monospace; font-size: 12px; }
</style>
