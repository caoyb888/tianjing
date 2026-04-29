<template>
  <div class="sandbox-list">
    <PageHeader title="Sandbox 实验会话" description="管理算法实验室推理会话，所有结果不触发生产告警">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="showCreate = true">创建会话</el-button>
      </template>
    </PageHeader>

    <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
      Sandbox 推理结果严格隔离，不触发生产告警，不写入生产数据库。所有标注框以虚线展示。
    </el-alert>

    <DataTable :data="sessions" :total="total" :loading="loading" v-model:page="page" v-model:size="size" @change="loadSessions">
      <el-table-column label="会话ID" prop="sessionId" width="200" />
      <el-table-column label="场景" prop="sceneId" width="180" />
      <el-table-column label="候选模型" prop="candidateModelId" width="200" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }"><StatusBadge :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="精确率" width="100">
        <template #default="{ row }">
          {{ row.precision !== undefined ? formatPercent(row.precision) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="召回率" width="100">
        <template #default="{ row }">
          {{ row.recall !== undefined ? formatPercent(row.recall) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/sandbox/sessions/${row.sessionId}`)">详情</el-button>
          <el-button v-if="row.status === 'COMPLETED'" link size="small" @click="$router.push(`/sandbox/sessions/${row.sessionId}/report`)">报告</el-button>
          <el-button v-if="row.status === 'RUNNING'" link size="small" type="warning" @click="stopSession(row.sessionId)">停止</el-button>
        </template>
      </el-table-column>
    </DataTable>

    <!-- 创建会话对话框 -->
    <el-dialog v-model="showCreate" title="创建实验会话" width="540px" @open="onDialogOpen">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px" title="双路推理说明">
        <template #default>
          生产路使用当前 PRODUCTION 版本推理；实验室路使用候选模型（不同置信度阈值）。
          Sandbox 拦截器保证实验室结果绝不触发生产告警（CLAUDE.md §11.1）。
        </template>
      </el-alert>
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="场景 ID" required>
          <el-select
            v-model="createForm.sceneId"
            filterable
            placeholder="请选择场景"
            style="width: 100%"
            :loading="scenesLoading"
          >
            <el-option
              v-for="scene in sceneOptions"
              :key="scene.sceneId"
              :label="`${scene.sceneId}（${scene.sceneName || scene.factory}）`"
              :value="scene.sceneId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="生产模型版本">
          <el-select
            v-model="createForm.productionModelVersionId"
            filterable
            clearable
            placeholder="PRODUCTION 状态版本（可留空自动选择）"
            style="width: 100%"
            :loading="modelsLoading"
          >
            <el-option
              v-for="mv in prodModelOptions"
              :key="mv.versionId"
              :label="`${mv.versionId}（${mv.pluginId}）`"
              :value="mv.versionId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="候选模型版本" required>
          <el-select
            v-model="createForm.experimentModelVersionId"
            filterable
            placeholder="请选择候选模型（STAGING / SANDBOX_VALIDATING）"
            style="width: 100%"
            :loading="modelsLoading"
          >
            <el-option
              v-for="mv in candidateModelOptions"
              :key="mv.versionId"
              :label="`${mv.versionId}（${mv.status}）`"
              :value="mv.versionId"
            >
              <span>{{ mv.versionId }}</span>
              <el-tag size="small" :type="mv.status === 'SANDBOX_VALIDATING' ? 'warning' : 'info'" style="margin-left: 8px">
                {{ mv.status }}
              </el-tag>
            </el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="createSession">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import DataTable from '@/components/common/DataTable.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { sandboxApi } from '@/api/sandbox'
import { sceneApi } from '@/api/scene'
import { modelApi } from '@/api/model'
import { formatDateTime, formatPercent } from '@/utils/format'
import type { SandboxSession } from '@/types'

const sessions = ref<SandboxSession[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const showCreate = ref(false)
const creating = ref(false)

const createForm = reactive({
  sceneId: '',
  productionModelVersionId: '',
  experimentModelVersionId: '',
})

// 下拉选项数据
const sceneOptions = ref<Array<{ sceneId: string; sceneName?: string; factory: string }>>([])
const allModelVersions = ref<Array<{ versionId: string; pluginId: string; status: string }>>([])
const scenesLoading = ref(false)
const modelsLoading = ref(false)

const prodModelOptions = computed(() =>
  allModelVersions.value.filter(mv => mv.status === 'PRODUCTION')
)
const candidateModelOptions = computed(() =>
  allModelVersions.value.filter(mv => mv.status === 'STAGING' || mv.status === 'SANDBOX_VALIDATING')
)

async function onDialogOpen() {
  createForm.sceneId = ''
  createForm.productionModelVersionId = ''
  createForm.experimentModelVersionId = ''
  scenesLoading.value = true
  modelsLoading.value = true
  try {
    const [scenesRes, modelsRes] = await Promise.all([
      sceneApi.list({ page: 1, size: 200 }),
      modelApi.list({ page: 1, size: 200 }),
    ])
    sceneOptions.value = scenesRes.data.data.items ?? []
    allModelVersions.value = modelsRes.data.data.items ?? []
    // 自动选择第一个 PRODUCTION 版本
    const firstProd = prodModelOptions.value[0]
    if (firstProd) createForm.productionModelVersionId = firstProd.versionId
  } finally {
    scenesLoading.value = false
    modelsLoading.value = false
  }
}

async function loadSessions() {
  loading.value = true
  try {
    const res = await sandboxApi.listSessions({ page: page.value, size: size.value })
    sessions.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

async function stopSession(sessionId: string) {
  await sandboxApi.stopSession(sessionId)
  ElMessage.success('会话已停止')
  loadSessions()
}

async function createSession() {
  if (!createForm.sceneId) { ElMessage.warning('请选择场景'); return }
  if (!createForm.experimentModelVersionId) { ElMessage.warning('请选择候选模型版本'); return }
  creating.value = true
  try {
    await sandboxApi.createSession({
      scene_id: createForm.sceneId,
      production_model_version_id: createForm.productionModelVersionId || undefined,
      experiment_model_version_id: createForm.experimentModelVersionId,
    })
    ElMessage.success('实验会话已创建，Sandbox 双路推理已启动')
    showCreate.value = false
    loadSessions()
  } finally {
    creating.value = false
  }
}

onMounted(loadSessions)
</script>
