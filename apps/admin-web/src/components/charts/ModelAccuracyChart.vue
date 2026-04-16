<template>
  <div ref="chartRef" class="chart-container" />
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'

interface MetricPoint {
  date: string
  precision: number
  recall: number
}

const props = defineProps<{
  data: MetricPoint[]
  threshold?: number
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
  const threshold = props.threshold ?? 0.85
  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['精确率', '召回率'] },
    grid: { left: 50, right: 20, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: props.data.map((d) => d.date) },
    yAxis: { type: 'value', min: 0, max: 1, axisLabel: { formatter: (v: number) => `${(v * 100).toFixed(0)}%` } },
    series: [
      {
        name: '精确率',
        type: 'line',
        data: props.data.map((d) => d.precision),
        lineStyle: { color: '#1890ff' },
        itemStyle: { color: '#1890ff' },
        markLine: {
          data: [{ yAxis: threshold, name: '阈值线', lineStyle: { color: '#f56c6c', type: 'dashed' } }],
          label: { formatter: `阈值 ${(threshold * 100).toFixed(0)}%` },
        },
      },
      {
        name: '召回率',
        type: 'line',
        data: props.data.map((d) => d.recall),
        lineStyle: { color: '#52c41a' },
        itemStyle: { color: '#52c41a' },
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
