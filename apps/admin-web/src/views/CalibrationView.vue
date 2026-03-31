<template>
  <div class="calibration-view">
    <PageHeader
      title="在线标定工具"
      :description="`场景 ${sceneId} — 配置摄像头 ROI 区域与坐标系`"
    >
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" :loading="saving" @click="saveCalibration">保存标定</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :lg="16" :md="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">ROI 区域设置</span>
              <el-tag type="warning" size="small">Sprint 5 后支持实时帧，当前使用 MinIO 截帧</el-tag>
            </div>
          </template>

          <div class="calibration-canvas-wrap">
            <canvas
              ref="canvasRef"
              class="calibration-canvas"
              @mousedown="startDraw"
              @mousemove="onMouseMove"
              @mouseup="endDraw"
            />
            <div v-if="!imageLoaded" class="canvas-placeholder">
              <el-empty description="暂无摄像头截帧，Sprint 5 硬件部署后自动加载" />
            </div>
          </div>

          <div class="canvas-controls">
            <el-button size="small" @click="clearCanvas">清除 ROI</el-button>
            <span v-if="roi.w > 0" class="roi-info">
              ROI: ({{ roi.x }}, {{ roi.y }}) {{ roi.w }} × {{ roi.h }}
            </span>
          </div>
        </el-card>
      </el-col>

      <el-col :lg="8" :md="24">
        <el-card shadow="never">
          <template #header><span class="card-title">标定参数</span></template>
          <el-form :model="calibForm" label-width="100px" size="small">
            <el-form-item label="ROI X">
              <el-input-number v-model="roi.x" :min="0" style="width:100%" />
            </el-form-item>
            <el-form-item label="ROI Y">
              <el-input-number v-model="roi.y" :min="0" style="width:100%" />
            </el-form-item>
            <el-form-item label="ROI 宽度">
              <el-input-number v-model="roi.w" :min="1" style="width:100%" />
            </el-form-item>
            <el-form-item label="ROI 高度">
              <el-input-number v-model="roi.h" :min="1" style="width:100%" />
            </el-form-item>
            <el-form-item label="像素比例">
              <el-input-number
                v-model="calibForm.pixel_per_mm"
                :min="0.0001"
                :precision="4"
                style="width:100%"
              />
              <div class="form-hint">mm/pixel（尺寸测量场景使用）</div>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="calibForm.comment" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" style="margin-top: 16px">
          <template #header><span class="card-title">历史标定记录</span></template>
          <div v-for="record in history" :key="(record as any).id" class="history-item">
            <div class="history-roi">
              ROI: ({{ (record as any).roi?.x }}, {{ (record as any).roi?.y }})
              {{ (record as any).roi?.w }} × {{ (record as any).roi?.h }}
            </div>
            <div class="history-time">{{ formatDateTime((record as any).created_at) }}</div>
          </div>
          <EmptyState v-if="!history.length" description="暂无历史标定" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { calibrationApi } from '@/api/calibration'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const sceneId = route.params.sceneId as string
const canvasRef = ref<HTMLCanvasElement>()
const imageLoaded = ref(false)
const saving = ref(false)
const history = ref<unknown[]>([])

const roi = reactive({ x: 0, y: 0, w: 0, h: 0 })
const calibForm = reactive({ pixel_per_mm: 1.0, comment: '' })

let isDrawing = false
let startX = 0
let startY = 0

function getCanvasPos(e: MouseEvent): { x: number; y: number } {
  const rect = canvasRef.value!.getBoundingClientRect()
  return { x: e.clientX - rect.left, y: e.clientY - rect.top }
}

function startDraw(e: MouseEvent) {
  isDrawing = true
  const pos = getCanvasPos(e)
  startX = pos.x
  startY = pos.y
}

function onMouseMove(e: MouseEvent) {
  if (!isDrawing || !canvasRef.value) return
  const pos = getCanvasPos(e)
  const ctx = canvasRef.value.getContext('2d')!
  ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
  ctx.strokeStyle = '#1890ff'
  ctx.lineWidth = 2
  ctx.setLineDash([])
  ctx.strokeRect(startX, startY, pos.x - startX, pos.y - startY)
  ctx.fillStyle = 'rgba(24,144,255,0.1)'
  ctx.fillRect(startX, startY, pos.x - startX, pos.y - startY)
}

function endDraw(e: MouseEvent) {
  if (!isDrawing) return
  isDrawing = false
  const pos = getCanvasPos(e)
  roi.x = Math.round(Math.min(startX, pos.x))
  roi.y = Math.round(Math.min(startY, pos.y))
  roi.w = Math.round(Math.abs(pos.x - startX))
  roi.h = Math.round(Math.abs(pos.y - startY))
}

function clearCanvas() {
  if (!canvasRef.value) return
  canvasRef.value.getContext('2d')!.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
  roi.x = 0; roi.y = 0; roi.w = 0; roi.h = 0
}

async function saveCalibration() {
  saving.value = true
  try {
    await calibrationApi.submit(sceneId, { roi: { ...roi }, ...calibForm })
    ElMessage.success('标定已保存')
    loadHistory()
  } finally {
    saving.value = false
  }
}

async function loadHistory() {
  const res = await calibrationApi.getHistory(sceneId, { size: 5 })
  history.value = res.data.data.items
}

onMounted(() => {
  if (canvasRef.value) {
    canvasRef.value.width = 800
    canvasRef.value.height = 450
  }
  loadHistory()
})
</script>

<style scoped lang="scss">
.calibration-canvas-wrap {
  position: relative;
  background: #1a1a1a;
  border-radius: 4px;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.calibration-canvas {
  cursor: crosshair;
  display: block;
  max-width: 100%;
}
.canvas-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.canvas-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.roi-info {
  font-size: 13px;
  color: #1890ff;
  font-family: monospace;
}
.history-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
  &:last-child { border: none; }
}
.history-roi { font-size: 13px; font-family: monospace; }
.history-time { font-size: 12px; color: #909399; margin-top: 2px; }
.form-hint { font-size: 11px; color: #c0c4cc; margin-top: 2px; }
.card-title { font-weight: 600; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
</style>
