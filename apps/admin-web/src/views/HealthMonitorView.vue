<template>
  <div class="health-monitor">
    <PageHeader title="感知健康看板" description="摄像头实时状态总览与7日健康趋势">
      <template #actions>
        <el-select v-model="filterFactory" placeholder="全部厂部" clearable style="width: 140px" @change="loadOverview">
          <el-option v-for="(v, k) in FACTORY_CONFIG" :key="k" :label="v.label" :value="k" />
        </el-select>
        <el-button :icon="Refresh" :loading="overviewLoading" @click="loadOverview">刷新</el-button>
      </template>
    </PageHeader>

    <!-- 汇总统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card stat-total">
          <div class="stat-value">{{ summary.total }}</div>
          <div class="stat-label">摄像头总数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card stat-online">
          <div class="stat-value">{{ summary.online }}</div>
          <div class="stat-label">在线 ONLINE</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card stat-degraded">
          <div class="stat-value">{{ summary.degraded }}</div>
          <div class="stat-label">降质 DEGRADED</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card stat-offline">
          <div class="stat-value">{{ summary.offline }}</div>
          <div class="stat-label">离线 OFFLINE</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 摄像头状态网格 -->
    <el-card shadow="never" class="camera-grid-card">
      <template #header>
        <span class="card-title">摄像头状态一览</span>
        <el-radio-group v-model="gridStatus" size="small" style="margin-left: 16px" @change="loadOverview">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="ONLINE">在线</el-radio-button>
          <el-radio-button value="DEGRADED">降质</el-radio-button>
          <el-radio-button value="OFFLINE">离线</el-radio-button>
        </el-radio-group>
      </template>

      <div v-loading="overviewLoading" class="camera-grid">
        <div
          v-for="cam in cameras"
          :key="cam.cameraId"
          class="camera-card"
          :class="statusClass(cam.status)"
          @click="selectCamera(cam)"
        >
          <div class="cam-header">
            <el-icon class="cam-icon"><VideoCamera /></el-icon>
            <span class="cam-id">{{ cam.cameraId }}</span>
          </div>
          <div class="cam-score">
            <el-progress
              :percentage="cam.healthScore"
              :color="scoreColor(cam.healthScore)"
              :stroke-width="8"
              :show-text="false"
            />
            <span class="score-num" :style="{ color: scoreColor(cam.healthScore) }">{{ cam.healthScore }}</span>
          </div>
          <div class="cam-meta">
            <span>{{ FACTORY_CONFIG[cam.factory as Factory]?.label || cam.factory }}</span>
            <el-tag :type="statusTagType(cam.status)" size="small" effect="plain">{{ cam.status }}</el-tag>
          </div>
          <!-- 离线摄像头红色高亮故障类型 -->
          <el-tooltip v-if="cam.status === 'OFFLINE' && cam.faultType" :content="cam.faultType" placement="top">
            <div class="cam-fault">{{ cam.faultType }}</div>
          </el-tooltip>
        </div>

        <el-empty v-if="!overviewLoading && cameras.length === 0" description="暂无摄像头数据" />
      </div>
    </el-card>

    <!-- 历史健康趋势 -->
    <el-card shadow="never" class="history-card" v-if="selectedCamera">
      <template #header>
        <span class="card-title">健康趋势 — {{ selectedCamera.cameraId }}</span>
        <el-radio-group v-model="historyDays" size="small" style="margin-left: 16px" @change="loadHistory">
          <el-radio-button :value="1">24h</el-radio-button>
          <el-radio-button :value="3">3天</el-radio-button>
          <el-radio-button :value="7">7天</el-radio-button>
        </el-radio-group>
        <el-button
          v-if="selectedCamera.status === 'OFFLINE'"
          type="danger"
          size="small"
          style="margin-left: auto"
          @click="submitRepair"
        >
          提交维修工单
        </el-button>
      </template>
      <DeviceHealthChart :data="historyData" :loading="historyLoading" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, VideoCamera } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import DeviceHealthChart from '@/components/charts/DeviceHealthChart.vue'
import { healthApi } from '@/api/health'
import { FACTORY_CONFIG } from '@/constants'
import type { Factory } from '@/types'

interface CameraStatus {
  cameraId: string
  factory: string
  sceneId: string
  status: 'ONLINE' | 'DEGRADED' | 'OFFLINE'
  healthScore: number
  faultType?: string
  lastCheckTime: string
}

const filterFactory = ref('')
const gridStatus = ref('')
const overviewLoading = ref(false)
const cameras = ref<CameraStatus[]>([])
const selectedCamera = ref<CameraStatus | null>(null)
const historyDays = ref(7)
const historyLoading = ref(false)
const historyData = ref<unknown[]>([])

const summary = reactive({ total: 0, online: 0, degraded: 0, offline: 0 })

function statusClass(status: string) {
  return {
    'cam-online': status === 'ONLINE',
    'cam-degraded': status === 'DEGRADED',
    'cam-offline': status === 'OFFLINE',
  }
}

function statusTagType(status: string) {
  const map: Record<string, 'success' | 'warning' | 'danger'> = {
    ONLINE: 'success',
    DEGRADED: 'warning',
    OFFLINE: 'danger',
  }
  return map[status] || 'info'
}

function scoreColor(score: number) {
  if (score >= 80) return '#52c41a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    const res = await healthApi.getCameraOverview({
      factory: filterFactory.value || undefined,
      status: gridStatus.value || undefined,
    })
    cameras.value = res.data.data.items || []
    const s = res.data.data.summary || {}
    summary.total = s.total || cameras.value.length
    summary.online = s.online || cameras.value.filter((c) => c.status === 'ONLINE').length
    summary.degraded = s.degraded || cameras.value.filter((c) => c.status === 'DEGRADED').length
    summary.offline = s.offline || cameras.value.filter((c) => c.status === 'OFFLINE').length
  } catch {
    ElMessage.error('加载摄像头状态失败')
  } finally {
    overviewLoading.value = false
  }
}

async function loadHistory() {
  if (!selectedCamera.value) return
  historyLoading.value = true
  try {
    const res = await healthApi.getCameraHistory(selectedCamera.value.cameraId, {
      days: historyDays.value,
    })
    historyData.value = res.data.data.items || []
  } catch {
    ElMessage.error('加载健康历史失败')
  } finally {
    historyLoading.value = false
  }
}

function selectCamera(cam: CameraStatus) {
  selectedCamera.value = cam
  loadHistory()
}

async function submitRepair() {
  if (!selectedCamera.value) return
  const { value: faultType } = await ElMessageBox.prompt(
    '请输入故障类型描述',
    '提交维修工单',
    { inputPlaceholder: '如：镜头污染、连接断开、电源故障', confirmButtonText: '提交', cancelButtonText: '取消' }
  ).catch(() => ({ value: null }))
  if (!faultType) return
  await healthApi.submitRepairTicket(selectedCamera.value.cameraId, {
    fault_type: faultType,
    description: `摄像头 ${selectedCamera.value.cameraId} 离线，故障类型：${faultType}`,
  })
  ElMessage.success('维修工单已提交')
}

onMounted(loadOverview)
</script>

<style scoped lang="scss">
.health-monitor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-row {
  margin-bottom: 0;
}

.stat-card {
  text-align: center;
  padding: 8px 0;

  .stat-value {
    font-size: 32px;
    font-weight: 700;
    line-height: 1.2;
  }

  .stat-label {
    font-size: 12px;
    color: #606266;
    margin-top: 4px;
  }

  &.stat-total .stat-value { color: #303133; }
  &.stat-online .stat-value { color: #52c41a; }
  &.stat-degraded .stat-value { color: #e6a23c; }
  &.stat-offline .stat-value { color: #f56c6c; }
}

.card-title { font-weight: 600; }

.camera-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
  min-height: 120px;
}

.camera-card {
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 12px;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;

  &:hover {
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  }

  &.cam-online {
    border-color: #b7eb8f;
    background: #f6ffed;
  }

  &.cam-degraded {
    border-color: #ffe58f;
    background: #fffbe6;
  }

  &.cam-offline {
    border-color: #ffa39e;
    background: #fff1f0;
    animation: offline-pulse 2s ease-in-out infinite;
  }
}

@keyframes offline-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 108, 108, 0.2); }
  50% { box-shadow: 0 0 0 4px rgba(245, 108, 108, 0.1); }
}

.cam-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;

  .cam-icon { color: #606266; font-size: 16px; }
  .cam-id { font-size: 12px; font-weight: 600; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}

.cam-score {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;

  .el-progress { flex: 1; }
  .score-num { font-size: 13px; font-weight: 700; min-width: 28px; text-align: right; }
}

.cam-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #909399;
}

.cam-fault {
  margin-top: 4px;
  font-size: 11px;
  color: #f56c6c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.history-card {
  :deep(.el-card__header) {
    display: flex;
    align-items: center;
  }
}
</style>
