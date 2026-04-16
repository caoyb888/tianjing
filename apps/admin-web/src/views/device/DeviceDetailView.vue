<template>
  <div class="device-detail" v-loading="loading">
    <PageHeader :title="device?.deviceName || '设备详情'" :description="`设备编码：${deviceCode}`">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button @click="$router.push(`/devices/${deviceCode}/health`)">健康历史</el-button>
        <el-button type="primary" @click="$router.push(`/devices/${deviceCode}/edit`)">编辑</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" v-if="device">
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">设备信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="设备编码">{{ device.deviceCode }}</el-descriptions-item>
            <el-descriptions-item label="设备名称">{{ device.deviceName }}</el-descriptions-item>
            <el-descriptions-item label="关联场景">{{ device.sceneId }}</el-descriptions-item>
            <el-descriptions-item label="所属厂部">{{ FACTORY_CONFIG[device.factory]?.label }}</el-descriptions-item>
            <el-descriptions-item label="IP 地址">{{ device.ipAddress }}</el-descriptions-item>
            <el-descriptions-item label="当前状态"><StatusBadge :status="device.status" /></el-descriptions-item>
            <el-descriptions-item label="最后心跳">{{ formatDateTime(device.lastHeartbeat) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">视频预览（Sprint 5 上线后可用）</span></template>
          <el-empty description="实时视频流将在 Sprint 5 硬件部署后启用">
            <el-button disabled>查看实时流（未开放）</el-button>
          </el-empty>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { deviceApi } from '@/api/device'
import { FACTORY_CONFIG } from '@/constants'
import { formatDateTime } from '@/utils/format'
import type { DeviceInfo } from '@/types'

const route = useRoute()
const deviceCode = route.params.deviceCode as string
const device = ref<DeviceInfo | null>(null)
const loading = ref(false)

async function loadDevice() {
  loading.value = true
  try {
    const res = await deviceApi.get(deviceCode)
    device.value = res.data.data
  } finally {
    loading.value = false
  }
}

onMounted(loadDevice)
</script>
<style scoped lang="scss">.card-title { font-weight: 600; }</style>
