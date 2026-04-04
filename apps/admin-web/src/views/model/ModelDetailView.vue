<template>
  <div class="model-detail" v-loading="loading">
    <PageHeader
      :title="`模型版本 ${versionId}`"
      description="模型版本详情、审核状态与四眼原则校验"
    >
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <!-- STAGING / SANDBOX_VALIDATING：提交审核 → REVIEWING -->
        <el-button
          v-if="model?.status === ModelStatus.STAGING || model?.status === ModelStatus.SANDBOX_VALIDATING"
          type="primary"
          :loading="promoting"
          @click="promote"
        >
          提交审核
        </el-button>
        <el-tooltip
          v-if="model?.status === ModelStatus.REVIEWING && canReview && isSameUser"
          content="四眼原则：审核人不能与提交人相同"
          placement="top"
        >
          <el-button type="success" disabled>审批通过</el-button>
        </el-tooltip>
        <el-button
          v-if="model?.status === ModelStatus.REVIEWING && canReview && !isSameUser"
          type="success"
          @click="approve(true)"
        >
          审批通过
        </el-button>
        <el-tooltip
          v-if="model?.status === ModelStatus.REVIEWING && canReview && isSameUser"
          content="四眼原则：审核人不能与提交人相同"
          placement="top"
        >
          <el-button type="danger" disabled>审批拒绝</el-button>
        </el-tooltip>
        <el-button
          v-if="model?.status === ModelStatus.REVIEWING && canReview && !isSameUser"
          type="danger"
          @click="approve(false)"
        >
          审批拒绝
        </el-button>
      </template>
    </PageHeader>

    <!-- 状态步骤条 -->
    <el-card shadow="never" style="margin-bottom: 16px" v-if="model">
      <ModelStatusStepper :status="model.status" />
    </el-card>

    <el-row :gutter="16" v-if="model">
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">版本信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="版本ID">{{ model.versionId }}</el-descriptions-item>
            <el-descriptions-item label="插件ID">{{ model.pluginId }}</el-descriptions-item>
            <el-descriptions-item label="版本号">{{ model.version }}</el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <el-tag :type="MODEL_STATUS_CONFIG[model.status as ModelStatus]?.type">
                {{ MODEL_STATUS_CONFIG[model.status as ModelStatus]?.label }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="提交人">{{ model.submittedBy }}</el-descriptions-item>
            <el-descriptions-item label="审核人">{{ model.approvedBy || '待审核' }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatDateTime(model.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">四眼原则</span></template>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
            模型审核须由与提交人不同的 MODEL_REVIEWER 完成（四眼原则）。
            审核人与提交人相同时，系统返回错误码 4004。
          </el-alert>
          <el-alert
            v-if="isSameUser && model?.status === ModelStatus.REVIEWING"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          >
            当前登录用户（{{ authStore.username }}）为该模型提交人，无法执行审批操作。
            请由其他 MODEL_REVIEWER 完成审批。
          </el-alert>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="Sandbox 会话">
              <el-button
                v-if="model.sandboxSessionId"
                link
                @click="$router.push(`/sandbox/sessions/${model.sandboxSessionId}/report`)"
              >
                查看 Sandbox 报告
              </el-button>
              <span v-else class="text-secondary">未关联</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import ModelStatusStepper from '@/components/business/ModelStatusStepper.vue'
import { modelApi } from '@/api/model'
import { MODEL_STATUS_CONFIG } from '@/constants'
import { UserRole, ModelStatus, type ModelVersion } from '@/types'
import { formatDateTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const versionId = route.params.versionId as string
const authStore = useAuthStore()
const model = ref<ModelVersion | null>(null)
const loading = ref(false)
const promoting = ref(false)
const canReview = computed(() => authStore.hasRole([UserRole.ADMIN, UserRole.MODEL_REVIEWER]))
// 四眼原则：审核人不能与提交人相同
const isSameUser = computed(
  () => !!model.value && model.value.submittedBy === authStore.username
)

async function loadModel() {
  loading.value = true
  try {
    const res = await modelApi.get(versionId)
    model.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function promote() {
  await ElMessageBox.confirm(
    '确认将此模型版本提交审核？提交后状态将变更为"待审核"，等待 MODEL_REVIEWER 审批。',
    '提交审核',
    { type: 'info', confirmButtonText: '确认提交', cancelButtonText: '取消' }
  )
  promoting.value = true
  try {
    await modelApi.promote(versionId)
    ElMessage.success('已提交审核，等待 MODEL_REVIEWER 审批')
    loadModel()
  } finally {
    promoting.value = false
  }
}

async function approve(approved: boolean) {
  const label = approved ? '通过' : '拒绝'
  const { value: comment } = await ElMessageBox.prompt(
    `请输入${label}说明（可选）`,
    `确认${label}`,
    { inputPlaceholder: '可选', confirmButtonText: '确定', cancelButtonText: '取消' }
  ).catch(() => ({ value: null }))
  if (comment === null) return
  await modelApi.approve(versionId, { approved, comment })
  ElMessage.success(`审批已${label}`)
  loadModel()
}

onMounted(loadModel)
</script>
<style scoped lang="scss">
.card-title { font-weight: 600; }
.text-secondary { color: #909399; font-size: 13px; }
</style>
