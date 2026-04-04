<template>
  <div class="training-job-list">
    <PageHeader title="训练作业" description="管理模型训练任务">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="showSubmit = true">提交训练作业</el-button>
      </template>
    </PageHeader>

    <DataTable :data="jobs" :total="total" :loading="loading" v-model:page="page" v-model:size="size" @change="loadJobs">
      <el-table-column label="作业ID" prop="jobId" width="220" />
      <el-table-column label="插件ID" prop="pluginId" width="200" />
      <el-table-column label="数据集版本" prop="datasetVersionId" width="180" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusBadge :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="最佳 mAP@50" width="120" align="right">
        <template #default="{ row }">
          {{ row.bestMap50 != null ? (row.bestMap50 * 100).toFixed(1) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="触发方式" width="110">
        <template #default="{ row }">{{ row.triggerType || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/training/jobs/${row.jobId}`)">详情</el-button>
          <el-button
            v-if="row.status === 'RUNNING' || row.status === 'PENDING'"
            link size="small" type="warning"
            @click="cancelJob(row.jobId)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </DataTable>

    <!-- 提交作业对话框 -->
    <el-dialog v-model="showSubmit" title="提交训练作业" width="520px">
      <el-form :model="submitForm" label-width="110px">
        <el-form-item label="插件 ID" required>
          <el-input v-model="submitForm.pluginId" placeholder="如：ATOM-DETECT-YOLO-V1" />
        </el-form-item>
        <el-form-item label="数据集版本 ID" required>
          <el-input v-model="submitForm.datasetVersionId" placeholder="如：DS-SINTER-FIRE-001-v1.0" />
        </el-form-item>
        <el-form-item label="训练轮次">
          <el-input-number v-model="submitForm.epochs" :min="1" :max="300" style="width: 100%" />
        </el-form-item>
        <el-form-item label="GPU 数量">
          <el-input-number v-model="submitForm.gpuCount" :min="1" :max="8" style="width: 100%" />
          <div class="form-hint">测试环境 CPU 模式下此字段忽略，保持默认值 1 即可</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmit = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitJob">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import DataTable from '@/components/common/DataTable.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { trainingApi } from '@/api/training'
import { formatDateTime } from '@/utils/format'
import type { TrainingJob } from '@/types'

const jobs = ref<TrainingJob[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const showSubmit = ref(false)
const submitting = ref(false)

const submitForm = reactive({
  pluginId: '',
  datasetVersionId: '',
  epochs: 10,
  gpuCount: 1,
})

async function loadJobs() {
  loading.value = true
  try {
    const res = await trainingApi.listJobs({ page: page.value, size: size.value })
    jobs.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

async function cancelJob(jobId: string) {
  await trainingApi.cancelJob(jobId)
  ElMessage.success('作业已取消')
  loadJobs()
}

async function submitJob() {
  if (!submitForm.pluginId || !submitForm.datasetVersionId) {
    ElMessage.warning('插件 ID 和数据集版本 ID 为必填项')
    return
  }
  submitting.value = true
  try {
    await trainingApi.submitJob({
      plugin_id: submitForm.pluginId,
      dataset_version_id: submitForm.datasetVersionId,
      gpu_count: submitForm.gpuCount,
      train_config_json: { epochs: submitForm.epochs, img_size: 640, batch_size: 8 },
    })
    ElMessage.success('训练作业已提交')
    showSubmit.value = false
    submitForm.pluginId = ''
    submitForm.datasetVersionId = ''
    submitForm.epochs = 10
    submitForm.gpuCount = 1
    loadJobs()
  } finally {
    submitting.value = false
  }
}

onMounted(loadJobs)
</script>

<style scoped lang="scss">
.form-hint { font-size: 12px; color: #909399; margin-top: 4px; line-height: 1.4; }
</style>
