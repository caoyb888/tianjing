<template>
  <div class="device-health">
    <PageHeader title="设备健康历史" :description="`设备 ${deviceCode} 的图像质量监测记录`">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <DataTable :data="records" :total="total" :loading="loading" v-model:page="page" v-model:size="size" @change="loadHistory">
      <el-table-column label="采样时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.ts) }}</template>
      </el-table-column>
      <el-table-column label="健康评分" width="120">
        <template #default="{ row }">
          <el-progress :percentage="row.health_score" :color="scoreColor(row.health_score)" :stroke-width="10" />
        </template>
      </el-table-column>
      <el-table-column label="亮度" prop="brightness" width="80" />
      <el-table-column label="清晰度" prop="sharpness" width="90" />
      <el-table-column label="遮挡比例" width="100">
        <template #default="{ row }">{{ (row.occlusion_ratio * 100).toFixed(1) }}%</template>
      </el-table-column>
      <el-table-column label="问题描述" prop="issue_description" min-width="160" />
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import DataTable from '@/components/common/DataTable.vue'
import { deviceApi } from '@/api/device'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const deviceCode = route.params.deviceCode as string
const records = ref<unknown[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)

function scoreColor(score: number) {
  if (score >= 80) return '#52c41a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

async function loadHistory() {
  loading.value = true
  try {
    const res = await deviceApi.getHealthHistory(deviceCode, { page: page.value, size: size.value })
    records.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

onMounted(loadHistory)
</script>
