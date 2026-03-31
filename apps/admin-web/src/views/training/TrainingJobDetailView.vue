<template>
  <div class="job-detail" v-loading="loading">
    <PageHeader :title="`训练作业 ${jobId}`" description="训练进度与评估指标">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button
          v-if="job?.status === 'running' || job?.status === 'queued'"
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
            <el-descriptions-item label="场景">{{ job.sceneId }}</el-descriptions-item>
            <el-descriptions-item label="数据集">{{ job.datasetCode }}</el-descriptions-item>
            <el-descriptions-item label="状态"><StatusBadge :status="job.status" /></el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(job.createdAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="job.completedAt" label="完成时间">
              {{ formatDateTime(job.completedAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">训练进度</span></template>
          <div class="progress-section">
            <el-progress
              :percentage="job.progress"
              :stroke-width="16"
              :status="job.status === 'failed' ? 'exception' : job.status === 'completed' ? 'success' : undefined"
            />
            <div v-if="job.currentEpoch" class="epoch-text">
              Epoch {{ job.currentEpoch }} / {{ job.totalEpochs }}
            </div>
          </div>
          <el-divider v-if="job.metrics" />
          <el-descriptions v-if="job.metrics" :column="2" size="small">
            <el-descriptions-item label="mAP@50">
              {{ job.metrics.map50 ? (job.metrics.map50 * 100).toFixed(2) + '%' : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="Loss">
              {{ job.metrics.loss?.toFixed(4) || '-' }}
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
    if (['completed', 'failed', 'cancelled'].includes(job.value.status)) {
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
.progress-section { margin: 16px 0; }
.epoch-text { text-align: center; font-size: 13px; color: #909399; margin-top: 8px; }
.card-title { font-weight: 600; }
</style>
