<template>
  <div class="scene-history">
    <PageHeader title="配置历史" :description="`场景 ${sceneId} 的配置变更记录`">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <DataTable :data="history" :total="total" :loading="loading" v-model:page="page" v-model:size="size" @change="loadHistory">
      <el-table-column label="版本" prop="version" width="80" align="center">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column label="变更描述" prop="description" min-width="200" />
      <el-table-column label="操作人" prop="operator" width="120" />
      <el-table-column label="变更时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link size="small" @click="handleRollback(row.version)">回滚至此</el-button>
        </template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import DataTable from '@/components/common/DataTable.vue'
import { sceneApi } from '@/api/scene'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const sceneId = route.params.sceneId as string
const history = ref<unknown[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)

async function loadHistory() {
  loading.value = true
  try {
    const res = await sceneApi.getHistory(sceneId, { page: page.value, size: size.value })
    history.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

async function handleRollback(version: number) {
  await ElMessageBox.confirm(`确定要将场景回滚至 v${version} 吗？此操作将覆盖当前配置。`, '确认回滚', { type: 'warning' })
  await sceneApi.rollback(sceneId, { version })
  ElMessage.success('回滚成功')
  loadHistory()
}

onMounted(loadHistory)
</script>
