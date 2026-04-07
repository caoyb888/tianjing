<template>
  <div class="drift-monitor">
    <PageHeader title="模型漂移监测" description="监控生产模型精度趋势，自动触发重训练">
    </PageHeader>

    <!-- 精度趋势图 -->
    <el-card shadow="never" class="chart-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">精度趋势（最近 30 天）</span>
          <el-select v-model="selectedScene" placeholder="选择场景" clearable style="width: 220px" @change="loadMetrics">
            <el-option label="全部场景" value="" />
            <el-option v-for="s in sceneOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </div>
      </template>
      <ModelAccuracyChart :data="metricsData" :threshold="0.85" />
    </el-card>

    <!-- 各场景精度状态表 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="card-header">
          <span class="card-title">各场景精度状态</span>
        </div>
      </template>
      <el-table :data="sceneMetrics" v-loading="loading">
        <el-table-column label="场景ID" prop="scene_id" width="200" />
        <el-table-column label="最新精确率" width="120">
          <template #default="{ row }">
            <span :class="row.precision < 0.85 ? 'text-danger' : 'text-success'">
              {{ formatPercent(row.precision) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最新召回率" width="120">
          <template #default="{ row }">{{ formatPercent(row.recall) }}</template>
        </el-table-column>
        <el-table-column label="低于阈值天数" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.below_threshold_days >= 3" type="danger" size="small">
              {{ row.below_threshold_days }} 天
            </el-tag>
            <span v-else>{{ row.below_threshold_days }} 天</span>
          </template>
        </el-table-column>
        <el-table-column label="是否漂移" width="100">
          <template #default="{ row }">
            <el-tag :type="row.is_drifting ? 'danger' : 'success'" size="small">
              {{ row.is_drifting ? '漂移中' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              v-permission="['ADMIN']"
              link size="small" type="warning"
              :disabled="!row.is_drifting"
              @click="triggerRetrain(row.scene_id)"
            >
              触发重训练
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import ModelAccuracyChart from '@/components/charts/ModelAccuracyChart.vue'
import { driftApi } from '@/api/drift'
import { formatPercent } from '@/utils/format'

const loading = ref(false)
const selectedScene = ref('')
const metricsData = ref<Array<{ date: string; precision: number; recall: number }>>([])
const sceneMetrics = ref<unknown[]>([])
const sceneOptions = ref<string[]>([])

async function loadMetrics() {
  loading.value = true
  try {
    const res = await driftApi.getMetrics({ sceneId: selectedScene.value || undefined })
    metricsData.value = res.data.data.trend || []
    sceneMetrics.value = res.data.data.scenes || []
    // 首次加载时从返回结果提取场景选项
    if (sceneOptions.value.length === 0) {
      sceneOptions.value = (res.data.data.scenes as Array<{ scene_id: string }>)
        .map(s => s.scene_id)
    }
  } finally {
    loading.value = false
  }
}

async function triggerRetrain(sceneId: string) {
  await ElMessageBox.confirm(`确定要手动触发场景 ${sceneId} 的重训练吗？`, '确认', { type: 'warning' })
  await driftApi.triggerRetrain({ sceneId })
  ElMessage.success('重训练作业已提交')
}

onMounted(loadMetrics)
</script>

<style scoped lang="scss">
.chart-card {
  border: 1px solid var(--tj-border-light);
  box-shadow: var(--tj-shadow-card);

  :deep(.el-card__header) {
    border-bottom-color: var(--tj-border-light);
  }

  :deep(.el-card__body) { height: 320px; padding: 16px; }
}
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-title { font-size: 15px; font-weight: 600; color: var(--tj-text-primary); }
.text-success { color: var(--tj-success); font-weight: 600; }
.text-danger { color: var(--tj-critical); font-weight: 600; }
</style>
