<template>
  <div class="audit-view">
    <PageHeader title="数据同步审计" description="生产→训练环境的数据脱敏传输审计日志">
    </PageHeader>

    <SearchBar @search="loadLogs" @reset="resetSearch">
      <el-form-item label="时间范围">
        <el-date-picker v-model="query.dateRange" type="daterange" range-separator="至" start-placeholder="开始" end-placeholder="结束" style="width: 240px" />
      </el-form-item>
      <el-form-item label="操作人">
        <el-input v-model="query.operator" placeholder="操作人账号" style="width: 160px" clearable />
      </el-form-item>
    </SearchBar>

    <DataTable
      :data="logs"
      :total="total"
      :loading="loading"
      v-model:page="query.page"
      v-model:size="query.size"
      :row-class-name="rowClassName"
      @change="loadLogs"
    >
      <el-table-column label="同步批次ID" prop="sync_batch_id" width="200" />
      <el-table-column label="数据集大小" width="120">
        <template #default="{ row }">{{ formatFileSize(row.dataset_size_mb * 1024 * 1024) }}</template>
      </el-table-column>
      <el-table-column label="图像帧数" prop="frame_count" width="100" />
      <el-table-column label="校验和" width="140">
        <template #default="{ row }">
          <el-tag :type="row.checksum_passed === false ? 'danger' : 'success'" size="small">
            {{ row.checksum_passed === false ? '校验失败' : '校验通过' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="脱敏状态" width="110">
        <template #default="{ row }">
          <el-tag :type="row.anonymized ? 'success' : 'danger'" size="small">
            {{ row.anonymized ? '已脱敏' : '未脱敏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="传输方式" prop="transfer_method" width="100" />
      <el-table-column label="操作人" prop="operator" width="120" />
      <el-table-column label="同步时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.sync_time) }}</template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SearchBar from '@/components/common/SearchBar.vue'
import DataTable from '@/components/common/DataTable.vue'
import { auditApi } from '@/api/audit'
import { formatDateTime, formatFileSize } from '@/utils/format'
import dayjs from 'dayjs'

const logs = ref<unknown[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 20, dateRange: null as [Date, Date] | null, operator: '' })

async function loadLogs() {
  loading.value = true
  try {
    const res = await auditApi.getDataSyncLogs({
      page: query.page, size: query.size,
      operator: query.operator || undefined,
      startTime: query.dateRange ? dayjs(query.dateRange[0]).toISOString() : undefined,
      endTime: query.dateRange ? dayjs(query.dateRange[1]).endOf('day').toISOString() : undefined,
    })
    logs.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

function resetSearch() { query.dateRange = null; query.operator = ''; query.page = 1; loadLogs() }

// 校验和失败的行整行红色高亮
function rowClassName({ row }: { row: Record<string, unknown> }) {
  return row.checksum_passed === false ? 'row-checksum-error' : ''
}

onMounted(loadLogs)
</script>

<style>
/* 需要全局样式（非 scoped）才能作用于 el-table 内部的 tr */
.row-checksum-error {
  background-color: #fff2f0 !important;
  color: #cf1322;
}
.row-checksum-error:hover > td {
  background-color: #ffe7e6 !important;
}
</style>
