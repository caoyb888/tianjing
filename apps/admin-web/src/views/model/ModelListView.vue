<template>
  <div class="model-list">
    <PageHeader title="模型版本管理" description="管理算法模型的版本、审核与发布">
      <template #actions>
        <PermissionButton type="primary" :icon="Plus" :roles="[UserRole.ADMIN, UserRole.SCENE_EDITOR]" hide-if-no-permission @click="showSubmit = true">
          提交新模型
        </PermissionButton>
      </template>
    </PageHeader>

    <DataTable :data="models" :total="total" :loading="loading" v-model:page="page" v-model:size="size" @change="loadModels">
      <el-table-column label="版本ID" prop="versionId" width="200" />
      <el-table-column label="插件ID" prop="pluginId" width="200" />
      <el-table-column label="版本号" prop="version" width="100" />
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="MODEL_STATUS_CONFIG[row.status as ModelStatus]?.type" size="small">
            {{ MODEL_STATUS_CONFIG[row.status as ModelStatus]?.label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交人" prop="submittedBy" width="120" />
      <el-table-column label="审核人" prop="approvedBy" width="120">
        <template #default="{ row }">{{ row.approvedBy || '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/models/${row.versionId}`)">详情</el-button>
          <el-button
            v-if="row.status === ModelStatus.REVIEWING && canReview"
            link size="small" type="success"
            @click="reviewModel(row.versionId, true)"
          >
            审批通过
          </el-button>
        </template>
      </el-table-column>
    </DataTable>

    <!-- 提交新模型对话框 -->
    <el-dialog v-model="showSubmit" title="提交新模型版本" width="500px">
      <el-form :model="submitForm" label-width="110px">
        <el-form-item label="插件 ID" required>
          <el-input v-model="submitForm.pluginId" placeholder="如：ATOM-DETECT-YOLO-V1" />
        </el-form-item>
        <el-form-item label="版本号" required>
          <el-input v-model="submitForm.version" placeholder="如：1.3.0" />
        </el-form-item>
        <el-form-item label="产物路径" required>
          <el-input v-model="submitForm.modelArtifactUrl" placeholder="minio://tianjing-models-staging/..." />
        </el-form-item>
        <el-form-item label="MLflow Run ID">
          <el-input v-model="submitForm.mlflowRunId" placeholder="可选，训练完成后自动填写" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSubmit = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitModel">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import DataTable from '@/components/common/DataTable.vue'
import PermissionButton from '@/components/common/PermissionButton.vue'
import { modelApi } from '@/api/model'
import { MODEL_STATUS_CONFIG } from '@/constants'
import { UserRole, ModelStatus, type ModelVersion } from '@/types'
import { formatDateTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const models = ref<ModelVersion[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const showSubmit = ref(false)
const submitting = ref(false)

const canReview = computed(() => authStore.hasRole([UserRole.ADMIN, UserRole.MODEL_REVIEWER]))
const submitForm = reactive({ pluginId: '', version: '', modelArtifactUrl: '', mlflowRunId: '' })

async function loadModels() {
  loading.value = true
  try {
    const res = await modelApi.list({ page: page.value, size: size.value })
    models.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

async function reviewModel(versionId: string, approved: boolean) {
  const action = approved ? '批准' : '拒绝'
  const comment = await ElMessageBox.prompt(`请输入${action}说明（可选）`, `确认${action}`, {
    inputPlaceholder: '可选',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  }).then(({ value }) => value).catch(() => null)
  if (comment === null) return
  await modelApi.approve(versionId, { approved, comment })
  ElMessage.success(`模型已${action}`)
  loadModels()
}

async function submitModel() {
  submitting.value = true
  try {
    await modelApi.submit({
      plugin_id:          submitForm.pluginId,
      version:            submitForm.version,
      model_artifact_url: submitForm.modelArtifactUrl,
      mlflow_run_id:      submitForm.mlflowRunId || undefined,
    })
    ElMessage.success('模型已提交审核')
    showSubmit.value = false
    Object.assign(submitForm, { pluginId: '', version: '', modelArtifactUrl: '', mlflowRunId: '' })
    loadModels()
  } finally {
    submitting.value = false
  }
}

onMounted(loadModels)
</script>
