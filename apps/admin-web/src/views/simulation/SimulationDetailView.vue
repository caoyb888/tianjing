<template>
  <div class="simulation-detail" v-loading="loading">
    <PageHeader :title="`仿真任务 ${taskId}`" description="仿真推理进度与结果">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button
          v-if="task?.status === 'RUNNING' || task?.status === 'PENDING'"
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
            <el-descriptions-item label="状态"><StatusBadge :status="task.status" /></el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(task.createdAt) }}</el-descriptions-item>
            <el-descriptions-item v-if="task.finishedAt" label="完成时间">
              {{ formatDateTime(task.finishedAt) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">推理进度</span></template>
          <div style="padding: 20px 0">
            <el-progress
              :percentage="task.status === 'COMPLETED' ? 100 : task.status === 'FAILED' ? 100 : task.progress ?? 0"
              :stroke-width="20"
              :status="task.status === 'FAILED' ? 'exception' : task.status === 'COMPLETED' ? 'success' : undefined"
            />
          </div>
          <el-result
            v-if="task.status === 'FAILED'"
            icon="error"
            title="仿真失败"
            :sub-title="task.errorMsg || '请检查视频文件格式或联系管理员'"
          />
        </el-card>
      </el-col>
    </el-row>

    <!-- 视频列表 -->
    <el-card shadow="never" style="margin-top: 16px" v-if="task">
      <template #header>
        <div style="display:flex; align-items:center; justify-content:space-between">
          <span class="card-title">视频列表（{{ (task.videos ?? []).length }} 个）</span>
          <el-button
            v-if="task.status === 'PENDING' || task.status === 'RUNNING'"
            size="small"
            :icon="Plus"
            @click="showAddVideo = true"
          >追加视频</el-button>
        </div>
      </template>
      <el-table :data="task.videos ?? []" size="small" style="width: 100%">
        <el-table-column label="序号" prop="sortOrder" width="60" />
        <el-table-column label="文件名" prop="videoName" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.label === 'NORMAL' ? 'success' : row.label === 'ABNORMAL' ? 'danger' : 'info'"
            >{{ labelText(row.label) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusBadge :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="帧数" prop="totalFrames" width="80" />
        <el-table-column label="告警帧" prop="matchedAlarms" width="80" />
        <el-table-column label="错误" prop="errorMsg" min-width="120" show-overflow-tooltip />
      </el-table>
    </el-card>

    <!-- 追加视频对话框 -->
    <el-dialog v-model="showAddVideo" title="追加视频" width="480px">
      <el-form label-width="100px">
        <el-form-item label="视频标注">
          <el-select v-model="addVideoForm.label" style="width: 100%">
            <el-option label="混合（默认）" value="MIXED" />
            <el-option label="正常录像" value="NORMAL" />
            <el-option label="异常/缺陷" value="ABNORMAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="视频文件">
          <el-upload
            :before-upload="handleAddVideoUpload"
            :show-file-list="false"
            accept="video/*"
            drag
          >
            <div v-if="!addVideoForm.url" class="upload-placeholder">
              <el-icon class="upload-icon"><Upload /></el-icon>
              <div>点击或拖拽上传视频</div>
            </div>
            <div v-else class="upload-done">
              <el-icon><VideoPlay /></el-icon>
              <span>{{ addVideoForm.name }} 上传成功</span>
            </div>
          </el-upload>
          <el-progress v-if="addVideoProgress > 0 && addVideoProgress < 100" :percentage="addVideoProgress" style="margin-top: 6px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddVideo = false">取消</el-button>
        <el-button type="primary" :loading="addingVideo" :disabled="!addVideoForm.url" @click="submitAddVideo">确认追加</el-button>
      </template>
    </el-dialog>

    <!-- 导出训练数据集区域（仿真完成后显示） -->
    <el-card
      v-if="task?.status === 'COMPLETED'"
      shadow="never"
      style="margin-top: 16px"
    >
      <template #header>
        <span class="card-title">导出为训练数据集</span>
      </template>

      <!-- 导出成功横幅 -->
      <el-alert
        v-if="exportStatus?.status === 'EXPORTED'"
        type="success"
        :closable="false"
        style="margin-bottom: 16px"
      >
        <template #default>
          <div style="display:flex; align-items:center; justify-content:space-between">
            <span>
              已生成数据集版本 <strong>{{ exportStatus.datasetVersionId }}</strong>，
              共 {{ exportStatus.exportedFrames }} 帧 / {{ exportStatus.annotationCount }} 个标注框
            </span>
            <el-button type="primary" size="small" @click="goToSubmitJob">
              提交训练作业 →
            </el-button>
          </div>
        </template>
      </el-alert>

      <!-- 导出失败提示 -->
      <el-alert
        v-else-if="exportStatus?.status === 'EXPORT_FAILED'"
        type="error"
        :title="`导出失败：${exportStatus.errorMsg || '未知错误'}`"
        :closable="false"
        style="margin-bottom: 16px"
      />

      <!-- 导出中进度条 -->
      <div v-if="exportStatus?.status === 'EXPORTING'" style="margin-bottom: 16px">
        <div style="margin-bottom: 8px; color: #606266; font-size: 14px">正在导出数据集...</div>
        <el-progress :percentage="exportStatus.progress ?? 0" :stroke-width="12" status="striped" striped striped-flow />
      </div>

      <!-- 导出表单（未导出或导出失败时显示） -->
      <el-form
        v-if="!exportStatus || exportStatus.status === 'EXPORT_FAILED' || exportStatus.status === 'PENDING'"
        :model="exportForm"
        label-width="120px"
        style="max-width: 520px"
      >
        <el-form-item label="数据集版本" required>
          <el-select
            v-model="exportForm.datasetVersionId"
            placeholder="选择未冻结的数据集版本"
            filterable
            style="width: 100%"
            :loading="datasetVersionsLoading"
          >
            <el-option
              v-for="v in datasetVersions"
              :key="v.versionId"
              :label="`${v.datasetCode} · ${v.versionTag}`"
              :value="v.versionId"
              :disabled="v.isFrozen"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="置信度阈值">
          <el-input-number
            v-model="exportForm.confThreshold"
            :min="0" :max="1" :step="0.05" :precision="2"
            style="width: 160px"
          />
          <span style="margin-left: 8px; color: #909399; font-size: 13px">低于此值的检测框不纳入标注</span>
        </el-form-item>
        <el-form-item label="包含负样本">
          <el-checkbox v-model="exportForm.includeNegatives">
            包含无检测结果的帧（负样本）
          </el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="exportLoading"
            :disabled="!exportForm.datasetVersionId"
            @click="startExport"
          >
            一键导出
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Upload, VideoPlay } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { simulationApi } from '@/api/simulation'
import { trainingApi } from '@/api/training'
import { formatDateTime } from '@/utils/format'
import type { SimulationTask } from '@/types'

interface DatasetVersion {
  versionId: string
  versionTag: string
  datasetCode: string
  isFrozen: boolean
}

interface ExportStatus {
  status: string
  progress: number
  totalFrames: number
  exportedFrames: number
  annotationCount: number
  datasetVersionId: string | null
  minioPath: string | null
  errorMsg: string | null
}

const route = useRoute()
const router = useRouter()
const taskId = route.params.taskId as string
const task = ref<SimulationTask | null>(null)
const loading = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

// 追加视频相关状态
const showAddVideo = ref(false)
const addingVideo = ref(false)
const addVideoProgress = ref(0)
const addVideoForm = reactive({ url: '', name: '', label: 'MIXED' })

// 导出相关状态
const exportStatus = ref<ExportStatus | null>(null)
const exportLoading = ref(false)
const datasetVersions = ref<DatasetVersion[]>([])
const datasetVersionsLoading = ref(false)
const exportForm = reactive({
  datasetVersionId: '',
  confThreshold: 0.7,
  includeNegatives: true,
})
let exportPollTimer: ReturnType<typeof setInterval> | null = null

async function loadTask() {
  if (!task.value) loading.value = true
  try {
    const res = await simulationApi.get(taskId)
    task.value = res.data.data
    if (['COMPLETED', 'FAILED'].includes(task.value?.status ?? '')) {
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

function labelText(label: string) {
  if (label === 'NORMAL') return '正常录像'
  if (label === 'ABNORMAL') return '异常/缺陷'
  return '混合'
}

async function handleAddVideoUpload(file: File) {
  if (!task.value?.sceneId) return false
  addVideoProgress.value = 0
  try {
    const res = await simulationApi.uploadVideo(file, task.value.sceneId, (p) => { addVideoProgress.value = p })
    addVideoForm.url = res.data.data.url
    addVideoForm.name = file.name
    ElMessage.success('视频上传成功')
  } catch {
    ElMessage.error('上传失败，请重试')
  }
  return false
}

async function submitAddVideo() {
  if (!addVideoForm.url) return
  addingVideo.value = true
  try {
    await simulationApi.addVideo(taskId, addVideoForm.url, addVideoForm.label)
    ElMessage.success('视频已追加')
    showAddVideo.value = false
    addVideoForm.url = ''
    addVideoForm.name = ''
    addVideoForm.label = 'MIXED'
    loadTask()
  } finally {
    addingVideo.value = false
  }
}

async function loadDatasetVersions() {
  datasetVersionsLoading.value = true
  try {
    const res = await trainingApi.listDatasetVersions()
    datasetVersions.value = res.data.data ?? []
  } finally {
    datasetVersionsLoading.value = false
  }
}

async function loadExportStatus() {
  try {
    const res = await simulationApi.getExportStatus(taskId)
    exportStatus.value = res.data.data
    if (exportStatus.value?.status === 'EXPORTING') {
      startExportPolling()
    } else {
      stopExportPolling()
    }
  } catch {
    // 忽略状态查询失败
  }
}

async function startExport() {
  if (!exportForm.datasetVersionId) return
  exportLoading.value = true
  try {
    await simulationApi.exportDataset(taskId, {
      datasetVersionId: exportForm.datasetVersionId,
      confThreshold: exportForm.confThreshold,
      includeNegatives: exportForm.includeNegatives,
    })
    ElMessage.success('导出任务已启动，请等待...')
    exportStatus.value = { status: 'EXPORTING', progress: 0, totalFrames: 0, exportedFrames: 0, annotationCount: 0, datasetVersionId: exportForm.datasetVersionId, minioPath: null, errorMsg: null }
    startExportPolling()
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(msg || '导出失败，请稍后重试')
  } finally {
    exportLoading.value = false
  }
}

function startExportPolling() {
  if (exportPollTimer) return
  exportPollTimer = setInterval(async () => {
    await loadExportStatus()
    if (exportStatus.value?.status !== 'EXPORTING') {
      stopExportPolling()
    }
  }, 3000)
}

function stopExportPolling() {
  if (exportPollTimer) { clearInterval(exportPollTimer); exportPollTimer = null }
}

function goToSubmitJob() {
  router.push({ path: '/training/jobs', query: { datasetVersionId: exportForm.datasetVersionId } })
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

// 仿真完成后加载数据集版本列表和已有导出状态
watch(() => task.value?.status, (status) => {
  if (status === 'COMPLETED' || status === 'completed') {
    loadDatasetVersions()
    loadExportStatus()
  }
})

onMounted(() => {
  loadTask()
  pollTimer = setInterval(loadTask, 10000)
})
onUnmounted(() => {
  stopPolling()
  stopExportPolling()
})
</script>

<style scoped lang="scss">
.card-title { font-weight: 600; }
.upload-placeholder, .upload-done {
  padding: 16px;
  text-align: center;
  color: #909399;
}
.upload-icon {
  font-size: 30px;
  margin-bottom: 6px;
}
</style>
