<template>
  <div class="alarm-list">
    <PageHeader title="告警管理" description="查看和处理所有生产告警记录">
    </PageHeader>

    <SearchBar @search="loadAlarms" @reset="resetSearch">
      <el-form-item label="告警级别">
        <el-select v-model="query.level" placeholder="全部级别" clearable style="width: 120px">
          <el-option label="严重" value="CRITICAL" />
          <el-option label="警告" value="WARNING" />
          <el-option label="信息" value="INFO" />
        </el-select>
      </el-form-item>
      <el-form-item label="厂部">
        <el-select v-model="query.factory" placeholder="全部厂部" clearable style="width: 140px">
          <el-option v-for="(cfg, key) in FACTORY_CONFIG" :key="key" :label="cfg.label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="query.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item label="仅看生产">
        <el-switch v-model="query.productionOnly" />
      </el-form-item>
    </SearchBar>

    <DataTable
      :data="alarms"
      :total="total"
      :loading="loading"
      v-model:page="query.page"
      v-model:size="query.size"
      @change="loadAlarms"
      :row-class-name="alarmRowClass"
    >
      <el-table-column label="级别" width="90">
        <template #default="{ row }">
          <el-tag :type="levelType(row.alarmLevel)" size="small">{{ row.alarmLevel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="异常类型" prop="anomalyType" min-width="140" />
      <el-table-column label="场景" prop="sceneId" width="180" />
      <el-table-column label="厂部" width="90">
        <template #default="{ row }">{{ FACTORY_CONFIG[row.factory]?.label }}</template>
      </el-table-column>
      <el-table-column label="置信度" width="90">
        <template #default="{ row }">{{ formatConfidence(row.confidence) }}</template>
      </el-table-column>
      <el-table-column label="来源" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.isSandbox" type="warning" size="small" effect="plain">Sandbox</el-tag>
          <el-tag v-else type="success" size="small" effect="plain">生产</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处置状态" width="100">
        <template #default="{ row }">
          <StatusBadge
            :status="row.feedbackStatus || 'pending'"
            :map="feedbackStatusMap"
          />
        </template>
      </el-table-column>
      <el-table-column label="告警时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.timestamp) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/alarms/${row.alarmId}`)">详情</el-button>
        </template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SearchBar from '@/components/common/SearchBar.vue'
import DataTable from '@/components/common/DataTable.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { alarmApi } from '@/api/alarm'
import { FACTORY_CONFIG } from '@/constants'
import { AlarmLevel, type AlarmRecord } from '@/types'
import { formatDateTime, formatConfidence } from '@/utils/format'
import dayjs from 'dayjs'

const alarms = ref<AlarmRecord[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  page: 1,
  size: 20,
  level: '',
  factory: '',
  dateRange: null as [Date, Date] | null,
  productionOnly: false,
})

const feedbackStatusMap = {
  pending: { label: '待处置', type: 'info' as const },
  confirmed: { label: '已确认', type: 'success' as const },
  rejected: { label: '已驳回', type: 'warning' as const },
}

function levelType(level: AlarmLevel) {
  const map = { [AlarmLevel.CRITICAL]: 'danger', [AlarmLevel.WARNING]: 'warning', [AlarmLevel.INFO]: 'info' }
  return (map[level] || 'info') as 'danger' | 'warning' | 'info'
}

function alarmRowClass({ row }: { row: AlarmRecord }) {
  if (row.alarmLevel === AlarmLevel.CRITICAL) return 'row-critical'
  if (row.alarmLevel === AlarmLevel.WARNING) return 'row-warning'
  return ''
}

async function loadAlarms() {
  loading.value = true
  try {
    const res = await alarmApi.list({
      page: query.page,
      size: query.size,
      level: query.level || undefined,
      factory: query.factory || undefined,
      isSandbox: query.productionOnly ? false : undefined,
      startTime: query.dateRange ? dayjs(query.dateRange[0]).toISOString() : undefined,
      endTime: query.dateRange ? dayjs(query.dateRange[1]).endOf('day').toISOString() : undefined,
    })
    alarms.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  query.level = ''
  query.factory = ''
  query.dateRange = null
  query.productionOnly = false
  query.page = 1
  loadAlarms()
}

onMounted(loadAlarms)
</script>

<style scoped lang="scss">
// CRITICAL 告警行背景（对应 el-table 的 row-class-name）
:deep(.row-critical td) {
  background-color: var(--tj-critical-bg) !important;
}
:deep(.row-critical:hover td) {
  background-color: #FFE0DE !important;
}
:deep(.row-warning td) {
  background-color: var(--tj-warning-bg) !important;
}
</style>
