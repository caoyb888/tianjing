<template>
  <div class="device-list">
    <PageHeader title="设备管理" description="管理工业摄像头和边缘设备">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="$router.push('/devices/new')">注册设备</el-button>
      </template>
    </PageHeader>

    <SearchBar @search="loadDevices" @reset="resetSearch">
      <el-form-item label="厂部">
        <el-select v-model="query.factory" placeholder="全部厂部" clearable style="width: 140px">
          <el-option v-for="(cfg, key) in FACTORY_CONFIG" :key="key" :label="cfg.label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px">
          <el-option label="在线" value="online" />
          <el-option label="离线" value="offline" />
          <el-option label="告警" value="warning" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="设备编码 / 名称" style="width: 200px" clearable />
      </el-form-item>
    </SearchBar>

    <DataTable
      :data="devices"
      :total="total"
      :loading="loading"
      v-model:page="query.page"
      v-model:size="query.size"
      @change="loadDevices"
    >
      <el-table-column label="设备编码" prop="deviceCode" width="160" />
      <el-table-column label="设备名称" prop="deviceName" min-width="150">
        <template #default="{ row }">
          <el-button link @click="$router.push(`/devices/${row.deviceCode}`)">{{ row.deviceName }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="关联场景" prop="sceneId" width="180" />
      <el-table-column label="厂部" width="100">
        <template #default="{ row }">{{ FACTORY_CONFIG[row.factory]?.label }}</template>
      </el-table-column>
      <el-table-column label="IP 地址" prop="ipAddress" width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusBadge :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="最后心跳" width="170">
        <template #default="{ row }">{{ formatDateTime(row.lastHeartbeat) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/devices/${row.deviceCode}`)">详情</el-button>
          <el-button link size="small" @click="$router.push(`/devices/${row.deviceCode}/health`)">健康历史</el-button>
        </template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SearchBar from '@/components/common/SearchBar.vue'
import DataTable from '@/components/common/DataTable.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { deviceApi } from '@/api/device'
import { FACTORY_CONFIG } from '@/constants'
import { formatDateTime } from '@/utils/format'
import type { DeviceInfo } from '@/types'

const devices = ref<DeviceInfo[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({ page: 1, size: 20, factory: '', status: '', keyword: '' })

async function loadDevices() {
  loading.value = true
  try {
    const res = await deviceApi.list({
      page: query.page, size: query.size,
      factory: query.factory || undefined,
      status: query.status || undefined,
      keyword: query.keyword || undefined,
    })
    devices.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  query.factory = ''; query.status = ''; query.keyword = ''; query.page = 1
  loadDevices()
}

onMounted(loadDevices)
</script>

<style scoped lang="scss">
// 设备健康度进度条颜色使用 Token
:deep(.el-progress-bar__inner) {
  background-color: var(--tj-primary);
}
</style>
