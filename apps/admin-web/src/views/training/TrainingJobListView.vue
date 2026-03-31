<template>
  <div class="training-job-list">
    <PageHeader title="训练作业" description="管理模型训练任务">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="showSubmit = true">提交训练作业</el-button>
      </template>
    </PageHeader>

    <DataTable :data="jobs" :total="total" :loading="loading" v-model:page="page" v-model:size="size" @change="loadJobs">
      <el-table-column label="作业ID" prop="jobId" width="200" />
      <el-table-column label="场景" prop="sceneId" width="180" />
      <el-table-column label="数据集" prop="datasetCode" width="180" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusBadge :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="进度" min-width="140">
        <template #default="{ row }">
          <div v-if="row.status === 'running'">
            <el-progress :percentage="row.progress" :stroke-width="8" />
            <div class="epoch-info">Epoch {{ row.currentEpoch }}/{{ row.totalEpochs }}</div>
          </div>
          <span v-else>{{ row.progress }}%</span>
        </template>
      </el-table-column>
      <el-table-column label="mAP@50" width="100">
        <template #default="{ row }">
          {{ row.metrics?.map50 ? (row.metrics.map50 * 100).toFixed(1) + '%' : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/training/jobs/${row.jobId}`)">详情</el-button>
          <el-button v-if="row.status === 'running' || row.status === 'queued'" link size="small" type="warning" @click="cancelJob(row.jobId)">取消</el-button>
        </template>
      </el-table-column>
    </DataTable>

    <!-- 提交作业对话框 -->
    <el-dialog v-model="showSubmit" title="提交训练作业" width="500px">
      <el-form :model="submitForm" label-width="100px">
        <el-form-item label="场景 ID" required>
          <el-input v-model="submitForm.sceneId" placeholder="如：SCENE-SINTER-005" />
        </el-form-item>
        <el-form-item label="数据集编号" required>
          <el-input v-model="submitForm.datasetCode" placeholder="如：DS-SINTER-001" />
        </el-form-item>
        <el-form-item label="训练轮次">
          <el-input-number v-model="submitForm.epochs" :min="10" :max="300" style="width: 100%" />
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

const submitForm = reactive({ sceneId: '', datasetCode: '', epochs: 100 })

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
  submitting.value = true
  try {
    await trainingApi.submitJob(submitForm)
    ElMessage.success('训练作业已提交')
    showSubmit.value = false
    loadJobs()
  } finally {
    submitting.value = false
  }
}

onMounted(loadJobs)
</script>

<style scoped lang="scss">
.epoch-info { font-size: 11px; color: #909399; margin-top: 2px; }
</style>
