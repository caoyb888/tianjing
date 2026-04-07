<template>
  <div class="screen">
    <!-- ===== 顶部标题栏 ===== -->
    <header class="header">
      <div class="header-left">
        <span class="logo-dot"></span>
        <span class="title">天柱·天镜 · 工业视觉 AI 推理平台</span>
      </div>
      <div class="header-center">
        <span class="subtitle">大屏监控中心</span>
      </div>
      <div class="header-right">
        <span class="time">{{ currentTime }}</span>
        <span class="date">{{ currentDate }}</span>
      </div>
    </header>

    <!-- ===== 筛选栏（新增） ===== -->
    <section class="filter-bar">
      <div class="filter-group">
        <span class="filter-label">🏭 厂部</span>
        <select v-model="filterFactory" class="filter-select">
          <option v-for="opt in FACTORY_OPTIONS" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </option>
        </select>
      </div>

      <div class="filter-group">
        <span class="filter-label">📍 场景</span>
        <select v-model="filterSceneId" class="filter-select" :disabled="sceneOptions.length === 0">
          <option value="">全部场景</option>
          <option v-for="scene in sceneOptions" :key="scene.sceneId" :value="scene.sceneId">
            {{ scene.sceneName }}
          </option>
        </select>
        <span v-if="sceneOptions.length === 0 && filterFactory" class="filter-hint">该厂部暂无活跃场景</span>
      </div>

      <div class="filter-group">
        <span class="filter-label">🔔 级别</span>
        <div class="level-tags">
          <button
            v-for="level in LEVEL_OPTIONS"
            :key="level.value"
            class="level-tag"
            :class="{ active: filterLevels.includes(level.value) }"
            :style="{ '--tag-color': level.color }"
            @click="toggleLevel(level.value)"
          >
            {{ level.label }}
          </button>
        </div>
      </div>

      <button class="reset-btn" @click="resetFilters">
        ↺ 重置筛选
      </button>
    </section>

    <!-- ===== 统计卡片行 ===== -->
    <section class="stat-row">
      <div class="stat-card" v-for="s in statCards" :key="s.key">
        <div class="stat-icon" :style="{ borderColor: s.color, color: s.color }">{{ s.icon }}</div>
        <div class="stat-body">
          <div class="stat-value" :style="{ color: s.color }">
            {{ overview[s.key] ?? '—' }}
          </div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
      <!-- 场景精度卡片（仅筛选场景时显示） -->
      <div v-if="filterSceneId" class="stat-card">
        <div class="stat-icon" style="border-color: #9254de; color: #9254de">📊</div>
        <div class="stat-body">
          <div class="stat-value" style="color: #9254de">
            {{ scenePrecision ?? '—' }}
          </div>
          <div class="stat-label">场景精度</div>
        </div>
      </div>
    </section>

    <!-- ===== 中间图表行 ===== -->
    <section class="chart-row">
      <!-- 推理趋势（左 2/3） -->
      <div class="panel panel-trend">
        <div class="panel-header">
          <span class="panel-title">推理量趋势（近 7 天）</span>
          <span class="panel-badge">{{ trendData.length }} 个数据点</span>
        </div>
        <div class="panel-body">
          <div ref="trendChartRef" class="echart-container"></div>
        </div>
      </div>

      <!-- 厂部热力柱图（新增，替换原饼图位置） -->
      <div class="panel panel-heatmap">
        <div class="panel-header">
          <span class="panel-title">各厂部告警分布（今日）</span>
        </div>
        <div class="panel-body">
          <div ref="heatmapChartRef" class="echart-container"></div>
        </div>
      </div>
    </section>

    <!-- 告警级别分布饼图（移至下方） -->
    <section class="pie-row">
      <div class="panel panel-pie">
        <div class="panel-header">
          <span class="panel-title">告警级别分布（今日）</span>
          <span v-if="hasFilter" class="filter-tag">已筛选</span>
        </div>
        <div class="panel-body">
          <div ref="pieChartRef" class="echart-container"></div>
        </div>
      </div>
    </section>

    <!-- ===== 实时告警表格 ===== -->
    <section class="alarm-section">
      <div class="panel">
        <div class="panel-header">
          <span class="panel-title">实时告警</span>
          <span class="panel-badge blink-badge" v-if="criticalCount > 0">
            CRITICAL × {{ criticalCount }}
          </span>
          <span class="refresh-hint">每 10 秒自动刷新</span>
        </div>
        <div class="panel-body table-body">
          <table class="alarm-table">
            <thead>
              <tr>
                <th style="width:90px">级别</th>
                <th style="width:90px">厂部</th>
                <th style="width:160px">场景</th>
                <th style="width:180px">异常类型</th>
                <th style="width:90px">置信度</th>
                <th style="width:150px">告警时间</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="alarm in recentAlarms"
                :key="alarm.alarmId"
                :class="[
                  'alarm-row',
                  `alarm-row--${alarm.alarmLevel.toLowerCase()}`,
                  { 'alarm-row--new': newAlarmIds.has(alarm.alarmId) }
                ]"
                @click="openAlarmDetail(alarm.alarmId)"
                style="cursor: pointer"
              >
                <td>
                  <span class="level-badge" :class="`level-badge--${alarm.alarmLevel.toLowerCase()}`">
                    {{ LEVEL_LABEL[alarm.alarmLevel] }}
                  </span>
                </td>
                <td>{{ FACTORY_LABEL[alarm.factory] ?? alarm.factory }}</td>
                <td class="scene-cell">{{ alarm.sceneName ?? alarm.sceneId }}</td>
                <td>{{ alarm.anomalyType }}</td>
                <td>{{ (alarm.confidence * 100).toFixed(1) }}%</td>
                <td>{{ formatTime(alarm.timestamp) }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="recentAlarms.length === 0" class="empty-tip">
            {{ hasFilter ? '当前筛选条件下暂无告警' : '暂无告警数据' }}
          </div>
        </div>
      </div>
    </section>

    <!-- 底部装饰线 -->
    <div class="footer-line"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import {
  monitorApi,
  FACTORY_OPTIONS,
  LEVEL_OPTIONS,
  type OverviewData,
  type TrendPoint,
  type AlarmItem,
  type SceneOption,
  type FactorySummary,
} from './api/index'

// ── 常量映射 ──────────────────────────────────────────────
const LEVEL_LABEL: Record<string, string> = {
  CRITICAL: '严重',
  WARNING:  '警告',
  INFO:     '信息',
}
const FACTORY_LABEL: Record<string, string> = {
  PELLET:    '球团厂',
  SINTER:    '烧结厂',
  STEEL:     '炼钢厂',
  SECTION:   '型钢厂',
  STRIP:     '带钢厂',
}

// ── 筛选状态 ──────────────────────────────────────────────
const filterFactory = ref<string>('')           // '' = 全部厂部
const filterSceneId = ref<string>('')           // '' = 全部场景
const filterLevels = ref<string[]>(['CRITICAL', 'WARNING', 'INFO'])  // 级别多选
const sceneOptions = ref<SceneOption[]>([])     // 场景下拉选项
const factorySummary = ref<FactorySummary[]>([]) // 厂部汇总数据
const scenePrecision = ref<string | null>(null) // 场景精度

// 计算属性：当前是否有筛选条件
const hasFilter = computed(() =>
  filterFactory.value !== '' || filterSceneId.value !== ''
)

// ── 响应式数据 ────────────────────────────────────────────
const currentTime = ref('')
const currentDate = ref('')
const overview    = ref<Partial<OverviewData>>({})
const trendData   = ref<TrendPoint[]>([])
const recentAlarms = ref<AlarmItem[]>([])
const newAlarmIds = ref<Set<string>>(new Set())  // 新告警ID集合（用于淡入动效）

const criticalCount = computed(
  () => recentAlarms.value.filter(a => a.alarmLevel === 'CRITICAL').length
)

const statCards = computed(() => [
  { key: 'active_scenes'    as keyof OverviewData, label: '运行场景', icon: '▶', color: '#36cfc9' },
  { key: 'online_devices'   as keyof OverviewData, label: '在线设备', icon: '📷', color: '#73d13d' },
  { key: 'today_alarms'     as keyof OverviewData, label: '今日告警', icon: '🔔', color: '#ff7a45' },
  { key: 'today_inferences' as keyof OverviewData, label: '今日推理量', icon: '⚡', color: '#597ef7' },
])

// ── 时钟 ──────────────────────────────────────────────────
let clockTimer: ReturnType<typeof setInterval> | null = null
function tickClock() {
  const now = dayjs()
  currentTime.value = now.format('HH:mm:ss')
  currentDate.value = now.format('YYYY-MM-DD ddd')
}

// ── ECharts ───────────────────────────────────────────────
const trendChartRef = ref<HTMLElement>()
const pieChartRef   = ref<HTMLElement>()
const heatmapChartRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null
let heatmapChart: echarts.ECharts | null = null

function initCharts() {
  if (trendChartRef.value) {
    trendChart = echarts.init(trendChartRef.value, 'dark')
  }
  if (pieChartRef.value) {
    pieChart = echarts.init(pieChartRef.value, 'dark')
  }
  if (heatmapChartRef.value) {
    heatmapChart = echarts.init(heatmapChartRef.value, 'dark')
  }
}

function updateTrendChart() {
  if (!trendChart) return
  trendChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: {
      data: ['推理量', '告警数'],
      textStyle: { color: '#8eb8e5' },
      top: 4,
    },
    grid: { left: 50, right: 24, top: 36, bottom: 32 },
    xAxis: {
      type: 'category',
      data: trendData.value.map(d => d.time),
      axisLine:  { lineStyle: { color: '#1a3a5c' } },
      axisLabel: { color: '#8eb8e5', fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#1a3a5c' } },
      axisLabel: { color: '#8eb8e5', fontSize: 12 },
    },
    series: [
      {
        name: '推理量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        data: trendData.value.map(d => d.count),
        lineStyle: { color: '#36cfc9', width: 2 },
        itemStyle: { color: '#36cfc9' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(54,207,201,0.3)' },
            { offset: 1, color: 'rgba(54,207,201,0.02)' },
          ]),
        },
      },
      {
        name: '告警数',
        type: 'bar',
        data: trendData.value.map(d => d.alarms || 0),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#ff7a45' },
            { offset: 1, color: 'rgba(255,122,69,0.2)' },
          ]),
          borderRadius: [3, 3, 0, 0],
        },
      },
    ],
  })
}

function updatePieChart() {
  if (!pieChart) return
  const critical = overview.value.critical_alarms ?? 0
  const warning  = overview.value.warning_alarms  ?? 0
  const info     = overview.value.info_alarms     ?? 0
  pieChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: {
      orient: 'vertical',
      right: 16,
      top: 'center',
      textStyle: { color: '#8eb8e5', fontSize: 13 },
    },
    series: [{
      name: '告警分布',
      type: 'pie',
      radius: ['45%', '72%'],
      center: ['42%', '50%'],
      avoidLabelOverlap: true,
      label: { show: true, color: '#8eb8e5', fontSize: 13, formatter: '{b}\n{c}次' },
      labelLine: { lineStyle: { color: '#8eb8e5' } },
      data: [
        { value: critical, name: '严重(CRITICAL)', itemStyle: { color: '#ff4d4f' } },
        { value: warning,  name: '警告(WARNING)',  itemStyle: { color: '#faad14' } },
        { value: info,     name: '信息(INFO)',     itemStyle: { color: '#597ef7' } },
      ],
    }],
  })
}

function updateHeatmapChart() {
  if (!heatmapChart) return
  
  const data = factorySummary.value
    .sort((a, b) => b.todayAlarms - a.todayAlarms)
    .map(item => ({
      name: item.factoryName,
      value: item.todayAlarms,
      itemStyle: {
        color: item.factory === filterFactory.value 
          ? '#36cfc9'  // 高亮当前选中的厂部
          : new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: 'rgba(54, 207, 201, 0.3)' },
              { offset: 1, color: 'rgba(54, 207, 201, 0.8)' },
            ]),
      },
    }))

  heatmapChart.setOption({
    backgroundColor: 'transparent',
    tooltip: { 
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: '{b}: {c} 条告警'
    },
    grid: { left: 80, right: 40, top: 20, bottom: 20 },
    xAxis: {
      type: 'value',
      splitLine: { show: false },
      axisLabel: { show: false },
    },
    yAxis: {
      type: 'category',
      data: data.map(d => d.name),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#8eb8e5', fontSize: 13 },
    },
    series: [{
      type: 'bar',
      data: data,
      barWidth: 16,
      label: {
        show: true,
        position: 'right',
        color: '#8eb8e5',
        formatter: '{c}',
      },
    }],
  })
}

// ── 筛选操作 ──────────────────────────────────────────────
function toggleLevel(level: string) {
  const idx = filterLevels.value.indexOf(level)
  if (idx > -1) {
    // 至少保留一个选中
    if (filterLevels.value.length > 1) {
      filterLevels.value.splice(idx, 1)
    }
  } else {
    filterLevels.value.push(level)
  }
}

function resetFilters() {
  filterFactory.value = ''
  filterSceneId.value = ''
  filterLevels.value = ['CRITICAL', 'WARNING', 'INFO']
}

function openAlarmDetail(alarmId: string) {
  // 新窗口打开管理后台告警详情页
  const baseUrl = import.meta.env.VITE_ADMIN_WEB_URL || '/admin'
  window.open(`${baseUrl}/alarms/${alarmId}`, '_blank')
}

// ── 数据拉取 ──────────────────────────────────────────────
async function loadSceneOptions() {
  try {
    const res = await monitorApi.getActiveScenes(filterFactory.value || undefined)
    sceneOptions.value = res.data.data.items
    // 如果当前选中的场景不在新列表中，重置场景选择
    if (filterSceneId.value && !sceneOptions.value.find(s => s.sceneId === filterSceneId.value)) {
      filterSceneId.value = ''
    }
  } catch (e) {
    sceneOptions.value = []
  }
}

async function loadFactorySummary() {
  try {
    const res = await monitorApi.getFactorySummary()
    factorySummary.value = res.data.data
    updateHeatmapChart()
  } catch (e) {
    factorySummary.value = []
  }
}

async function fetchOverview() {
  try {
    const res = await monitorApi.getOverview({
      factory: filterFactory.value || undefined,
      sceneId: filterSceneId.value || undefined,
    })
    overview.value = res.data.data
    updatePieChart()
  } catch {}
}

async function fetchTrend() {
  try {
    const res = await monitorApi.getInferenceTrend(7, {
      factory: filterFactory.value || undefined,
      sceneId: filterSceneId.value || undefined,
    })
    trendData.value = res.data.data
    updateTrendChart()
  } catch {}
}

async function fetchAlarms() {
  try {
    const res = await monitorApi.getRecentAlarms(15, {
      factory: filterFactory.value || undefined,
      sceneId: filterSceneId.value || undefined,
      levels: filterLevels.value,
    })
    const newItems = res.data.data.items
    
    // 检测新告警（对比前后数据差集）
    const oldIds = new Set(recentAlarms.value.map(a => a.alarmId))
    const newIds = new Set<string>()
    newItems.forEach((item: AlarmItem) => {
      if (!oldIds.has(item.alarmId)) {
        newIds.add(item.alarmId)
      }
    })
    
    // 如果有新告警，添加到新告警集合（触发淡入动效）
    if (newIds.size > 0 && oldIds.size > 0) {
      newIds.forEach(id => newAlarmIds.value.add(id))
      // 4秒后移除动画标记
      setTimeout(() => {
        newIds.forEach(id => newAlarmIds.value.delete(id))
      }, 4000)
    }
    
    recentAlarms.value = newItems
  } catch {}
}

// ── Watch 联动逻辑 ────────────────────────────────────────

// 厂部变化 → 重置场景 → 加载场景选项 → 刷新所有数据
watch(filterFactory, async () => {
  filterSceneId.value = ''
  await loadSceneOptions()
  await Promise.all([fetchOverview(), fetchTrend(), fetchAlarms()])
}, { flush: 'post' })

// 场景变化 → 刷新所有数据
watch(filterSceneId, async () => {
  await Promise.all([fetchOverview(), fetchTrend(), fetchAlarms()])
}, { flush: 'post' })

// 级别变化 → 仅刷新告警列表
watch(filterLevels, async () => {
  await fetchAlarms()
}, { flush: 'post', deep: true })

let overviewTimer: ReturnType<typeof setInterval> | null = null
let alarmTimer:   ReturnType<typeof setInterval> | null = null

function formatTime(ts: string) {
  return dayjs(ts).format('MM-DD HH:mm:ss')
}

// ── 生命周期 ──────────────────────────────────────────────
onMounted(async () => {
  tickClock()
  clockTimer = setInterval(tickClock, 1000)

  initCharts()
  window.addEventListener('resize', () => {
    trendChart?.resize()
    pieChart?.resize()
    heatmapChart?.resize()
  })

  // 初始化数据
  await loadSceneOptions()
  await loadFactorySummary()
  await Promise.all([fetchOverview(), fetchTrend(), fetchAlarms()])

  // 概览 + 趋势 + 厂部汇总 每 30 秒刷新
  overviewTimer = setInterval(async () => {
    await loadFactorySummary()
    await fetchOverview()
    await fetchTrend()
  }, 30_000)

  // 实时告警每 10 秒刷新
  alarmTimer = setInterval(fetchAlarms, 10_000)
})

onUnmounted(() => {
  if (clockTimer)   clearInterval(clockTimer)
  if (overviewTimer) clearInterval(overviewTimer)
  if (alarmTimer)   clearInterval(alarmTimer)
  trendChart?.dispose()
  pieChart?.dispose()
  heatmapChart?.dispose()
})
</script>

<style scoped lang="scss">
/* ── 大屏基础 1920×1080 ────────────────────────────────── */
.screen {
  width: 1920px;
  height: 1080px;
  background: #040b1a;
  color: #c8dff5;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  display: flex;
  flex-direction: column;
  padding: 0 24px 0;
  overflow: hidden;
  /* 装饰：顶部渐变光晕 */
  background-image:
    radial-gradient(ellipse 1200px 300px at 50% -60px, rgba(0, 120, 255, 0.12) 0%, transparent 100%);
}

/* ── 顶部标题栏 ────────────────────────────────────────── */
.header {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid rgba(0, 120, 255, 0.2);
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #36cfc9;
  box-shadow: 0 0 10px #36cfc9;
}

.title {
  font-size: 26px;
  font-weight: 700;
  background: linear-gradient(90deg, #5aadff, #36cfc9);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 2px;
}

.header-center .subtitle {
  font-size: 16px;
  color: #8eb8e5;
  letter-spacing: 4px;
  text-transform: uppercase;
}

.header-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.time {
  font-size: 28px;
  font-weight: 600;
  color: #5aadff;
  font-variant-numeric: tabular-nums;
  letter-spacing: 2px;
}

.date {
  font-size: 13px;
  color: #8eb8e5;
}

/* ── 筛选栏（新增） ─────────────────────────────────────── */
.filter-bar {
  height: 48px;
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 0 8px;
  margin: 8px 0 4px;
  border-bottom: 1px solid rgba(0, 120, 255, 0.15);
  flex-shrink: 0;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-label {
  font-size: 13px;
  color: #8eb8e5;
  white-space: nowrap;
}

.filter-select {
  min-width: 140px;
  height: 32px;
  padding: 0 12px;
  font-size: 13px;
  color: #c8dff5;
  background: rgba(5, 20, 50, 0.8);
  border: 1px solid rgba(0, 120, 255, 0.3);
  border-radius: 4px;
  cursor: pointer;
  outline: none;
  transition: border-color 0.2s;

  &:hover, &:focus {
    border-color: rgba(0, 120, 255, 0.6);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  option {
    background: #0a1a2a;
    color: #c8dff5;
  }
}

.filter-hint {
  font-size: 12px;
  color: #faad14;
}

.level-tags {
  display: flex;
  gap: 8px;
}

.level-tag {
  padding: 4px 12px;
  font-size: 12px;
  color: #8eb8e5;
  background: rgba(5, 20, 50, 0.8);
  border: 1px solid rgba(0, 120, 255, 0.3);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: var(--tag-color);
    color: var(--tag-color);
  }

  &.active {
    background: var(--tag-color);
    border-color: var(--tag-color);
    color: #fff;
  }
}

.reset-btn {
  margin-left: auto;
  padding: 6px 16px;
  font-size: 12px;
  color: #8eb8e5;
  background: rgba(5, 20, 50, 0.8);
  border: 1px solid rgba(0, 120, 255, 0.3);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    border-color: rgba(0, 120, 255, 0.6);
    color: #c8dff5;
  }
}

/* ── 统计卡片 ───────────────────────────────────────────── */
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin: 12px 0;
  flex-shrink: 0;
}

/* 有筛选场景时显示5个卡片 */
.stat-row:has(.stat-card:nth-child(5)) {
  grid-template-columns: repeat(5, 1fr);
}

.stat-card {
  background: rgba(5, 20, 50, 0.7);
  border: 1px solid rgba(0, 120, 255, 0.2);
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 2px;
    background: linear-gradient(90deg, transparent, var(--card-color, #36cfc9), transparent);
  }
}

.stat-icon {
  font-size: 28px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  border: 2px solid;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.stat-label {
  font-size: 13px;
  color: #8eb8e5;
  margin-top: 4px;
}

/* ── 图表行 ─────────────────────────────────────────────── */
.chart-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  flex: 0 0 290px;
}

/* ── 饼图行（新增） ─────────────────────────────────────── */
.pie-row {
  display: flex;
  gap: 16px;
  flex: 0 0 120px;
  margin-top: 12px;
}

.pie-row .panel {
  flex: 1;
}

/* ── 面板通用 ───────────────────────────────────────────── */
.panel {
  background: rgba(5, 20, 50, 0.7);
  border: 1px solid rgba(0, 120, 255, 0.2);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid rgba(0, 120, 255, 0.15);
  flex-shrink: 0;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #c8dff5;
}

.panel-badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(54, 207, 201, 0.15);
  color: #36cfc9;
  border: 1px solid rgba(54, 207, 201, 0.3);
}

.blink-badge {
  background: rgba(255, 77, 79, 0.15);
  color: #ff4d4f;
  border-color: rgba(255, 77, 79, 0.4);
  animation: badge-blink 1.5s ease-in-out infinite;
}

.filter-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(54, 207, 201, 0.2);
  color: #36cfc9;
  border: 1px solid rgba(54, 207, 201, 0.4);
}

.refresh-hint {
  margin-left: auto;
  font-size: 12px;
  color: #4a6b8a;
}

.panel-body {
  flex: 1;
  overflow: hidden;
}

.echart-container {
  width: 100%;
  height: 100%;
}

/* ── 实时告警 ───────────────────────────────────────────── */
.alarm-section {
  flex: 1;
  margin: 12px 0 12px;
  min-height: 0;

  .panel {
    height: 100%;
  }
}

.table-body {
  overflow-y: auto;
  padding: 0;
}

.alarm-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;

  thead tr {
    background: rgba(0, 60, 120, 0.4);
    color: #8eb8e5;
    font-size: 13px;
    font-weight: 400;
  }

  th, td {
    padding: 10px 16px;
    text-align: left;
    border-bottom: 1px solid rgba(0, 80, 160, 0.2);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 200px;
  }

  tbody tr {
    transition: background 0.2s;
    
    &:hover {
      background: rgba(0, 60, 120, 0.25);
    }
  }
}

/* CRITICAL 告警行 3 秒闪烁 */
@keyframes row-flash {
  0%, 100% { background-color: transparent; }
  50%       { background-color: rgba(255, 77, 79, 0.15); }
}

.alarm-row--critical {
  animation: row-flash 1s ease-in-out 3;
}

/* T4-2: 新告警淡入动效（从顶部滑入） */
@keyframes slide-in {
  0% {
    opacity: 0;
    transform: translateY(-20px);
    background-color: rgba(54, 207, 201, 0.3);
  }
  50% {
    background-color: rgba(54, 207, 201, 0.1);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
    background-color: transparent;
  }
}

.alarm-row--new {
  animation: slide-in 0.4s ease-out;
}

/* 级别标签 */
.level-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;

  &--critical {
    background: rgba(255, 77, 79, 0.2);
    color: #ff4d4f;
    border: 1px solid rgba(255, 77, 79, 0.4);
  }
  &--warning {
    background: rgba(250, 173, 20, 0.15);
    color: #faad14;
    border: 1px solid rgba(250, 173, 20, 0.3);
  }
  &--info {
    background: rgba(89, 126, 247, 0.15);
    color: #597ef7;
    border: 1px solid rgba(89, 126, 247, 0.3);
  }
}

.scene-cell {
  color: #36cfc9;
  font-size: 13px;
}

.empty-tip {
  text-align: center;
  padding: 40px;
  color: #4a6b8a;
  font-size: 14px;
}

/* ── 底部装饰线 ─────────────────────────────────────────── */
.footer-line {
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(54, 207, 201, 0.4), rgba(89, 126, 247, 0.4), transparent);
  flex-shrink: 0;
}

/* ── 徽标闪烁动画 ───────────────────────────────────────── */
@keyframes badge-blink {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.4; }
}
</style>
