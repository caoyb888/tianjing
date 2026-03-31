<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  data: Record<string, number>
  loading?: boolean
}>()

const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  updateChart()
}

function updateChart() {
  if (!chart) return
  const critical = props.data?.['critical_alarms'] || 0
  const warning = props.data?.['warning_alarms'] || 0
  const info = props.data?.['info_alarms'] || 0
  const option = {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, orient: 'horizontal' },
    series: [
      {
        type: 'pie',
        radius: ['45%', '70%'],
        center: ['50%', '45%'],
        data: [
          { value: critical, name: '严重', itemStyle: { color: '#f56c6c' } },
          { value: warning, name: '警告', itemStyle: { color: '#e6a23c' } },
          { value: info, name: '信息', itemStyle: { color: '#909399' } },
        ],
        label: { show: false },
        emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      },
    ],
  }
  chart.setOption(option)
}

watch(() => props.data, updateChart, { deep: true })

onMounted(() => {
  initChart()
  window.addEventListener('resize', () => chart?.resize())
})

onUnmounted(() => {
  chart?.dispose()
  window.removeEventListener('resize', () => chart?.resize())
})
</script>

<style scoped lang="scss">
.chart-container {
  width: 100%;
  height: 100%;
  min-height: 280px;
}
</style>
