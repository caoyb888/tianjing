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
      <el-table-column label="算法插件" prop="pluginId" width="200" />
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
        <template #default="{ row }">{{ formatDateTime(row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/sandbox/sessions/${row.sessionId}`)">详情</el-button>
          <el-button v-if="row.status === 'completed'" link size="small" @click="$router.push(`/sandbox/sessions/${row.sessionId}/report`)">报告</el-button>
          <el-button v-if="row.status === 'running'" link size="small" type="warning" @click="stopSession(row.sessionId)">停止</el-button>
        </template>
      </el-table-column>
    </DataTable>

    <!-- 创建会话对话框 -->
    <el-dialog v-model="showCreate" title="创建实验会话" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="场景 ID" required>
          <el-input v-model="createForm.sceneId" placeholder="如：SCENE-SINTER-005" />
        </el-form-item>
        <el-form-item label="算法插件 ID" required>
          <el-input v-model="createForm.pluginId" placeholder="如：ATOM-DETECT-YOLO-V1" />
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
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import DataTable from '@/components/common/DataTable.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { sandboxApi } from '@/api/sandbox'
import { formatDateTime, formatPercent } from '@/utils/format'
import type { SandboxSession } from '@/types'

const sessions = ref<SandboxSession[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const showCreate = ref(false)
const creating = ref(false)

const createForm = reactive({ sceneId: '', pluginId: '' })

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
  creating.value = true
  try {
    await sandboxApi.createSession(createForm)
    ElMessage.success('会话已创建')
    showCreate.value = false
    loadSessions()
  } finally {
    creating.value = false
  }
}

onMounted(loadSessions)
</script>
