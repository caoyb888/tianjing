<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  data: unknown[]
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
  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['推理量', '告警数'] },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      data: (props.data as Array<{ time: string }>).map((d) => d.time),
      axisLabel: { fontSize: 11 },
    },
    yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
    series: [
      {
        name: '推理量',
        type: 'line',
        smooth: true,
        data: (props.data as Array<{ count: number }>).map((d) => d.count),
        areaStyle: { opacity: 0.1 },
        lineStyle: { color: '#1890ff' },
        itemStyle: { color: '#1890ff' },
      },
      {
        name: '告警数',
        type: 'bar',
        data: (props.data as Array<{ alarms: number }>).map((d) => d.alarms || 0),
        itemStyle: { color: '#ff4d4f', borderRadius: [2, 2, 0, 0] },
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
