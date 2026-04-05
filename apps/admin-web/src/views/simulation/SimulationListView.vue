<template>
  <div class="simulation-list">
    <PageHeader title="仿真任务" description="使用录制视频文件仿真推理管道，验证算法效果">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="showCreate = true">新建仿真任务</el-button>
      </template>
    </PageHeader>

    <DataTable :data="tasks" :total="total" :loading="loading" v-model:page="page" v-model:size="size" @change="loadTasks">
      <el-table-column label="任务ID" prop="taskId" width="200" />
      <el-table-column label="场景" prop="sceneId" width="180" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusBadge :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="进度" min-width="140">
        <template #default="{ row }">
          <el-progress v-if="row.status === 'RUNNING'" :percentage="50" :stroke-width="8" :striped="true" striped-flow />
          <span v-else-if="row.status === 'COMPLETED'" style="color: #67c23a">100%</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/simulations/${row.taskId}`)">详情</el-button>
          <el-button v-if="row.status === 'RUNNING' || row.status === 'PENDING'" link size="small" type="warning" @click="cancelTask(row.taskId)">取消</el-button>
        </template>
      </el-table-column>
    </DataTable>

    <!-- 新建任务对话框 -->
    <el-dialog v-model="showCreate" title="新建仿真任务" width="560px">
      <el-form :model="createForm" label-width="120px">
        <el-form-item label="目标场景 ID" required>
          <el-input v-model="createForm.sceneId" placeholder="如：SCENE-SINTER-005" />
        </el-form-item>
        <el-form-item label="视频文件">
          <el-upload
            :before-upload="handleVideoUpload"
            :show-file-list="false"
            accept="video/*"
            drag
          >
            <div v-if="!uploadedVideoUrl" class="upload-placeholder">
              <el-icon class="upload-icon"><Upload /></el-icon>
              <div>点击或拖拽上传视频文件</div>
              <div class="upload-hint">支持 MP4、AVI、MKV，文件大小 ≤ 2GB</div>
            </div>
            <div v-else class="upload-done">
              <el-icon><VideoPlay /></el-icon>
              <span>视频上传成功</span>
            </div>
          </el-upload>
          <el-progress v-if="uploadProgress > 0 && uploadProgress < 100" :percentage="uploadProgress" style="margin-top: 8px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" :disabled="!uploadedVideoUrl" @click="createTask">创建任务</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Upload, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import DataTable from '@/components/common/DataTable.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { simulationApi } from '@/api/simulation'
import { formatDateTime } from '@/utils/format'
import type { SimulationTask } from '@/types'

const tasks = ref<SimulationTask[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const showCreate = ref(false)
const creating = ref(false)
const uploadProgress = ref(0)
const uploadedVideoUrl = ref('')

const createForm = reactive({ sceneId: '', videoUrl: '' })

async function loadTasks() {
  loading.value = true
  try {
    const res = await simulationApi.list({ page: page.value, size: size.value })
    tasks.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

async function handleVideoUpload(file: File) {
  uploadProgress.value = 0
  try {
    const res = await simulationApi.uploadVideo(file, createForm.sceneId, (p) => { uploadProgress.value = p })
    uploadedVideoUrl.value = res.data.data.url
    createForm.videoUrl = res.data.data.url
    ElMessage.success('视频上传成功')
  } catch {
    ElMessage.error('上传失败，请重试')
  }
  return false // 阻止自动上传
}

async function createTask() {
  creating.value = true
  try {
    await simulationApi.create(createForm)
    ElMessage.success('仿真任务已创建')
    showCreate.value = false
    uploadedVideoUrl.value = ''
    loadTasks()
  } finally {
    creating.value = false
  }
}

async function cancelTask(taskId: string) {
  await simulationApi.cancel(taskId)
  ElMessage.success('任务已取消')
  loadTasks()
}

onMounted(loadTasks)
</script>

<style scoped lang="scss">
.upload-placeholder, .upload-done {
  padding: 20px;
  text-align: center;
  color: #909399;
}
.upload-icon {
  font-size: 36px;
  margin-bottom: 8px;
}
.upload-hint {
  font-size: 12px;
  margin-top: 4px;
}
</style>
