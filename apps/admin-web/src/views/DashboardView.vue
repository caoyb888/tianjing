<template>
  <div class="dashboard">
    <PageHeader title="数据看板" description="生产推理总览、实时告警统计与趋势分析" />

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6" v-for="stat in stats" :key="stat.key">
        <el-card class="stat-card" shadow="never">
          <div class="stat-content">
            <div class="stat-icon" :style="{ background: stat.color }">
              <el-icon><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">
                <el-skeleton-item v-if="loading" variant="text" style="width: 80px" />
                <span v-else>{{ stat.value }}</span>
              </div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="16" class="chart-row">
      <!-- 推理趋势 -->
      <el-col :lg="16" :md="24">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">推理量趋势（近 7 天）</span>
              <el-select v-model="trendScene" placeholder="全部场景" clearable size="small" style="width: 160px">
                <el-option label="全部场景" value="" />
              </el-select>
            </div>
          </template>
          <InferenceTrendChart :data="trendData" :loading="loading" />
        </el-card>
      </el-col>

      <!-- 告警分布 -->
      <el-col :lg="8" :md="24">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span class="card-title">告警级别分布</span>
          </template>
          <AlarmHeatmapChart :data="overview" :loading="loading" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 实时告警 -->
    <el-card shadow="never" class="realtime-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">实时告警</span>
          <el-badge :value="alarmStore.unreadCount" :max="99">
            <el-button size="small" @click="alarmStore.markAllRead">全部已读</el-button>
          </el-badge>
        </div>
      </template>

      <el-table :data="alarmStore.realtimeAlarms" size="small" max-height="300" :row-class-name="alarmRowClass">
        <el-table-column label="级别" width="80">
          <template #default="{ row }">
            <StatusBadge :status="row.alarmLevel" :map="alarmLevelMap" />
          </template>
        </el-table-column>
        <el-table-column label="异常类型" prop="anomalyType" />
        <el-table-column label="场景" prop="sceneId" width="160" />
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">{{ formatConfidence(row.confidence) }}</template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.timestamp) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link size="small" @click="$router.push(`/alarms/${row.alarmId}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <EmptyState v-if="alarmStore.realtimeAlarms.length === 0" description="暂无实时告警" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Film, VideoCamera, Bell, DataAnalysis } from '@element-plus/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import InferenceTrendChart from '@/components/charts/InferenceTrendChart.vue'
import AlarmHeatmapChart from '@/components/charts/AlarmHeatmapChart.vue'
import { useAlarmStore } from '@/stores/alarm'
import { dashboardApi } from '@/api/dashboard'
import { AlarmLevel } from '@/types'
import { formatDateTime, formatConfidence } from '@/utils/format'

const alarmStore = useAlarmStore()
const loading = ref(false)
const overview = ref<Record<string, number>>({})
const trendData = ref<unknown[]>([])
const trendScene = ref('')

function alarmRowClass({ row }: { row: { alarmLevel: AlarmLevel } }) {
  return row.alarmLevel === AlarmLevel.CRITICAL ? 'alarm-row-critical' : ''
}

const alarmLevelMap = {
  [AlarmLevel.CRITICAL]: { label: '严重', type: 'danger' as const },
  [AlarmLevel.WARNING]: { label: '警告', type: 'warning' as const },
  [AlarmLevel.INFO]: { label: '信息', type: 'info' as const },
}

const stats = computed(() => [
  {
    key: 'active_scenes',
    label: '运行场景',
    value: overview.value['active_scenes'] ?? '-',
    icon: 'Film',
    color: '#e8f4ff',
  },
  {
    key: 'online_devices',
    label: '在线设备',
    value: overview.value['online_devices'] ?? '-',
    icon: 'VideoCamera',
    color: '#f0f9eb',
  },
  {
    key: 'today_alarms',
    label: '今日告警',
    value: overview.value['today_alarms'] ?? '-',
    icon: 'Bell',
    color: '#fdf6ec',
  },
  {
    key: 'today_inferences',
    label: '今日推理量',
    value: overview.value['today_inferences'] ?? '-',
    icon: 'DataAnalysis',
    color: '#f4f0ff',
  },
])

async function loadData() {
  loading.value = true
  try {
    const [overviewRes, trendRes] = await Promise.all([
      dashboardApi.getOverview(),
      dashboardApi.getInferenceTrend({ days: 7 }),
    ])
    overview.value = overviewRes.data.data
    // inference-trend 直接返回趋势数组
    trendData.value = Array.isArray(trendRes.data.data)
      ? trendRes.data.data
      : trendRes.data.data?.trend ?? []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.dashboard {
  max-width: 1400px;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  border: none;
  box-shadow: var(--tj-shadow-card);
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: var(--tj-shadow-hover);
  }

  :deep(.el-card__body) {
    padding: 20px;
  }
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: var(--tj-radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  color: var(--tj-primary);
  flex-shrink: 0;
}

.stat-value {
  font-size: 32px;
  font-weight: 800;
  color: var(--tj-text-primary);
  line-height: 1;
  margin-bottom: 6px;
}

.stat-label {
  font-size: var(--tj-font-sm);
  color: var(--tj-text-secondary);
  font-weight: 500;
}

.chart-row {
  margin-bottom: 16px;
}

.chart-card {
  height: 360px;
  border: 1px solid var(--tj-border-light);
  box-shadow: var(--tj-shadow-card);

  :deep(.el-card__header) {
    border-bottom-color: var(--tj-border-light);
    padding: 14px 20px;
  }

  :deep(.el-card__body) {
    height: calc(100% - 56px);
    padding: 16px;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--tj-text-primary);
}

.realtime-card {
  :deep(.el-card__body) {
    padding: 0;
  }
}

/* S2-12：CRITICAL 告警行 3 秒闪烁提示 */
@keyframes alarm-flash {
  0%, 100% { background-color: transparent; }
  50%       { background-color: rgba(245, 108, 108, 0.18); }
}

:deep(.alarm-row-critical) {
  animation: alarm-flash 1s ease-in-out 3;
}
</style>
