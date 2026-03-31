<template>
  <div class="system-logs">
    <PageHeader title="操作日志" description="系统关键操作的审计日志">
    </PageHeader>

    <SearchBar @search="loadLogs" @reset="resetSearch">
      <el-form-item label="操作人">
        <el-input v-model="query.operator" placeholder="用户名" style="width: 160px" clearable />
      </el-form-item>
      <el-form-item label="操作类型">
        <el-input v-model="query.action" placeholder="操作类型" style="width: 160px" clearable />
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker v-model="query.dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" style="width: 240px" />
      </el-form-item>
    </SearchBar>

    <DataTable :data="logs" :total="total" :loading="loading" v-model:page="query.page" v-model:size="query.size" @change="loadLogs">
      <el-table-column label="操作人" prop="operator" width="120" />
      <el-table-column label="操作类型" prop="action" width="160" />
      <el-table-column label="操作对象" prop="target" min-width="200" />
      <el-table-column label="操作结果" width="100">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'" size="small">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SearchBar from '@/components/common/SearchBar.vue'
import DataTable from '@/components/common/DataTable.vue'
import { systemApi } from '@/api/system'
import { formatDateTime } from '@/utils/format'
import dayjs from 'dayjs'

const logs = ref<unknown[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 20, operator: '', action: '', dateRange: null as [Date, Date] | null })

async function loadLogs() {
  loading.value = true
  try {
    const res = await systemApi.getOperationLogs({
      page: query.page, size: query.size,
      operator: query.operator || undefined,
      action: query.action || undefined,
      startTime: query.dateRange ? dayjs(query.dateRange[0]).toISOString() : undefined,
      endTime: query.dateRange ? dayjs(query.dateRange[1]).endOf('day').toISOString() : undefined,
    })
    logs.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

function resetSearch() { query.operator = ''; query.action = ''; query.dateRange = null; query.page = 1; loadLogs() }
onMounted(loadLogs)
</script>
