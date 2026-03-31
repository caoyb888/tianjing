<template>
  <div class="simulation-detail" v-loading="loading">
    <PageHeader :title="`仿真任务 ${taskId}`" description="仿真推理进度与结果">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button
          v-if="task?.status === 'running' || task?.status === 'pending'"
          type="warning"
          @click="cancelTask"
        >
          取消任务
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" v-if="task">
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">任务信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="任务ID">{{ task.taskId }}</el-descriptions-item>
            <el-descriptions-item label="场景">{{ task.sceneId }}</el-descriptions-item>
            <el-descriptions-item label="视频文件">
              <el-text truncated style="max-width: 220px">{{ task.videoUrl }}</el-text>
            </el-descriptions-item>
            <el-descriptions-item label="状态"><StatusBadge :status="task.status" /></el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(task.createdAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="task.completedAt" label="完成时间">
              {{ formatDateTime(task.completedAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">推理进度</span></template>
          <div style="padding: 20px 0">
            <el-progress
              :percentage="task.progress"
              :stroke-width="20"
              :status="task.status === 'failed' ? 'exception' : task.status === 'completed' ? 'success' : undefined"
            />
          </div>
          <el-result
            v-if="task.status === 'completed'"
            icon="success"
            title="仿真完成"
            sub-title="可前往 Sandbox 实验会话查看推理结果"
          />
          <el-result
            v-else-if="task.status === 'failed'"
            icon="error"
            title="仿真失败"
            sub-title="请检查视频文件格式或联系管理员"
          />
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
import { simulationApi } from '@/api/simulation'
import { formatDateTime } from '@/utils/format'
import type { SimulationTask } from '@/types'

const route = useRoute()
const taskId = route.params.taskId as string
const task = ref<SimulationTask | null>(null)
const loading = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function loadTask() {
  if (!task.value) loading.value = true
  try {
    const res = await simulationApi.get(taskId)
    task.value = res.data.data
    if (['completed', 'failed', 'cancelled'].includes(task.value.status)) {
      stopPolling()
    }
  } finally {
    loading.value = false
  }
}

async function cancelTask() {
  await simulationApi.cancel(taskId)
  ElMessage.success('任务已取消')
  loadTask()
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

onMounted(() => {
  loadTask()
  pollTimer = setInterval(loadTask, 3000)
})
onUnmounted(stopPolling)
</script>
<style scoped lang="scss">.card-title { font-weight: 600; }</style>
