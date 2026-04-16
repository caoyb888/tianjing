<template>
  <div ref="chartRef" class="device-health-chart" />
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, MarkLineComponent, CanvasRenderer])

interface HealthDataPoint {
  ts: string
  health_score: number
  brightness: number
  sharpness: number
  occlusion_ratio: number
}

const props = defineProps<{
  data: HealthDataPoint[]
  loading?: boolean
}>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

function buildOption() {
  const timestamps = props.data.map((d) => d.ts.slice(0, 16))
  const healthScores = props.data.map((d) => d.health_score)
  const sharpness = props.data.map((d) => d.sharpness)
  const brightness = props.data.map((d) => d.brightness)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: unknown[]) => {
        const lines = (params as Array<{ seriesName: string; value: number; color: string }>).map(
          (p) => `<span style="color:${p.color}">●</span> ${p.seriesName}: <b>${p.value}</b>`
        )
        return `${(params as Array<{ axisValueLabel: string }>)[0].axisValueLabel}<br/>${lines.join('<br/>')}`
      },
    },
    legend: {
      data: ['健康评分', '清晰度', '亮度'],
      bottom: 0,
    },
    grid: { left: 50, right: 30, top: 16, bottom: 40 },
    xAxis: {
      type: 'category',
      data: timestamps,
      axisLabel: { rotate: 30, fontSize: 11 },
    },
    yAxis: [
      {
        type: 'value',
        name: '评分',
        min: 0,
        max: 100,
        axisLabel: { formatter: '{value}' },
      },
      {
        type: 'value',
        name: '亮度',
        min: 0,
        max: 255,
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '健康评分',
        type: 'line',
        data: healthScores,
        smooth: true,
        lineStyle: { width: 2 },
        itemStyle: { color: '#52c41a' },
        areaStyle: { color: 'rgba(82, 196, 26, 0.08)' },
        markLine: {
          silent: true,
          lineStyle: { color: '#f56c6c', type: 'dashed' },
          data: [{ yAxis: 60, name: '预警线' }],
          label: { position: 'insideEndTop', formatter: '预警线(60)' },
        },
      },
      {
        name: '清晰度',
        type: 'line',
        data: sharpness,
        smooth: true,
        lineStyle: { width: 1.5, type: 'dashed' },
        itemStyle: { color: '#1890ff' },
        showSymbol: false,
      },
      {
        name: '亮度',
        type: 'line',
        yAxisIndex: 1,
        data: brightness,
        smooth: true,
        lineStyle: { width: 1.5, type: 'dotted' },
        itemStyle: { color: '#fa8c16' },
        showSymbol: false,
      },
    ],
  }
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  chart.setOption(buildOption())
}

watch(
  () => props.data,
  () => {
    if (chart) chart.setOption(buildOption(), { notMerge: true })
  },
  { deep: true }
)

watch(
  () => props.loading,
  (loading) => {
    if (!chart) return
    if (loading) chart.showLoading()
    else chart.hideLoading()
  }
)

const resizeObserver = new ResizeObserver(() => chart?.resize())

onMounted(() => {
  initChart()
  if (chartRef.value) resizeObserver.observe(chartRef.value)
})

onUnmounted(() => {
  resizeObserver.disconnect()
  chart?.dispose()
})
</script>

<style scoped lang="scss">
.device-health-chart {
  width: 100%;
  height: 280px;
}
</style>
