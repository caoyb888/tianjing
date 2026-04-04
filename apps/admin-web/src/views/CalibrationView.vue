<template>
  <div class="calibration-view">
    <PageHeader
      title="在线标定工具"
      :description="`场景 ${sceneId} — 在画面上点选两个已知距离的参考点，计算像素比例`"
    >
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSave" @click="saveCalibration">
          保存标定
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <!-- 画布区 -->
      <el-col :lg="16" :md="24">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span class="card-title">参考点选取</span>
              <el-tag :type="stepTagType" size="small">{{ stepHint }}</el-tag>
            </div>
          </template>

          <div class="canvas-wrap" @click="onCanvasClick">
            <canvas ref="canvasRef" class="calib-canvas" />
            <div v-if="!imageLoaded" class="canvas-empty">
              <el-empty description="暂无摄像头截帧，Sprint 5 硬件部署后自动加载" />
            </div>
          </div>

          <div class="canvas-controls">
            <el-button size="small" @click="resetPoints">重置点位</el-button>
            <span v-if="pt1" class="point-info">P1 ({{ pt1.x }}, {{ pt1.y }})</span>
            <span v-if="pt2" class="point-info">P2 ({{ pt2.x }}, {{ pt2.y }})</span>
            <span v-if="pixelDistance > 0" class="point-info dist">
              像素距离 {{ pixelDistance }} px
            </span>
          </div>
        </el-card>
      </el-col>

      <!-- 参数面板 -->
      <el-col :lg="8" :md="24">
        <el-card shadow="never">
          <template #header><span class="card-title">标定参数</span></template>
          <el-form :model="form" label-width="100px" size="small">
            <el-form-item label="摄像头编码" required>
              <el-select
                v-model="form.device_code"
                filterable
                allow-create
                placeholder="选择或输入设备编码"
                style="width:100%"
              >
                <el-option
                  v-for="opt in cameraOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="参考长度">
              <el-input-number
                v-model="form.ref_length_mm"
                :min="0.1"
                :precision="2"
                style="width:100%"
              />
              <div class="form-hint">两点之间的实际物理距离（mm）</div>
            </el-form-item>
            <el-form-item v-if="scaleMmPerPx" label="计算结果">
              <el-statistic :value="scaleMmPerPx" :precision="4" suffix=" mm/px" />
              <div class="form-hint">= {{ form.ref_length_mm }} mm ÷ {{ pixelDistance }} px</div>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 历史记录 -->
        <el-card shadow="never" style="margin-top:16px">
          <template #header><span class="card-title">历史标定记录</span></template>
          <div v-for="record in history" :key="(record as any).calibrationId" class="history-item">
            <div class="history-row">
              <el-tag v-if="(record as any).isActive" type="success" size="small">当前</el-tag>
              <span class="history-scale">{{ Number((record as any).scaleMmPerPx).toFixed(4) }} mm/px</span>
            </div>
            <div class="history-detail">
              P1({{ (record as any).pixelP1X }},{{ (record as any).pixelP1Y }})
              → P2({{ (record as any).pixelP2X }},{{ (record as any).pixelP2Y }})
              | {{ (record as any).refDistanceMm }} mm / {{ (record as any).pixelDistance }} px
            </div>
            <div class="history-time">{{ formatDateTime((record as any).createdAt) }}</div>
          </div>
          <EmptyState v-if="!history.length" description="暂无历史标定" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { calibrationApi } from '@/api/calibration'
import { deviceApi } from '@/api/device'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const sceneId = route.params.sceneId as string

const canvasRef = ref<HTMLCanvasElement>()
const imageLoaded = ref(false)
const saving = ref(false)
const history = ref<unknown[]>([])

const cameraOptions = ref<{ label: string; value: string }[]>([])

async function loadCameraOptions() {
  try {
    const res = await deviceApi.list({ size: 200 })
    const items: any[] = res.data?.data?.items ?? res.data?.items ?? []
    cameraOptions.value = items.map((d: any) => ({
      label: `${d.deviceName ?? d.device_name}（${d.deviceCode ?? d.device_code}）`,
      value: d.deviceCode ?? d.device_code,
    }))
  } catch {
    // 加载失败时仍可手动输入
  }
}

const pt1 = ref<{ x: number; y: number } | null>(null)
const pt2 = ref<{ x: number; y: number } | null>(null)

const form = reactive({ device_code: '', ref_length_mm: 100 })

// 像素距离
const pixelDistance = computed(() => {
  if (!pt1.value || !pt2.value) return 0
  const dx = pt2.value.x - pt1.value.x
  const dy = pt2.value.y - pt1.value.y
  return Math.round(Math.sqrt(dx * dx + dy * dy))
})

// 预览比例值
const scaleMmPerPx = computed(() =>
  pixelDistance.value > 0 ? (form.ref_length_mm / pixelDistance.value).toFixed(4) : null
)

const canSave = computed(() =>
  !!pt1.value && !!pt2.value && pixelDistance.value >= 2 && form.device_code.trim() !== ''
)

// 步骤提示
const stepHint = computed(() => {
  if (!pt1.value) return '第 1 步：点击选取参考点 P1'
  if (!pt2.value) return '第 2 步：点击选取参考点 P2'
  return '两点已选取，填写参数后保存'
})
const stepTagType = computed(() =>
  !pt1.value || !pt2.value ? 'warning' : 'success'
)

function getCanvasPos(e: MouseEvent): { x: number; y: number } {
  const rect = canvasRef.value!.getBoundingClientRect()
  const scaleX = canvasRef.value!.width / rect.width
  const scaleY = canvasRef.value!.height / rect.height
  return {
    x: Math.round((e.clientX - rect.left) * scaleX),
    y: Math.round((e.clientY - rect.top) * scaleY),
  }
}

function onCanvasClick(e: MouseEvent) {
  const pos = getCanvasPos(e)
  if (!pt1.value) {
    pt1.value = pos
  } else if (!pt2.value) {
    pt2.value = pos
  }
  drawCanvas()
}

function drawCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  const drawPoint = (p: { x: number; y: number }, label: string, color: string) => {
    ctx.beginPath()
    ctx.arc(p.x, p.y, 6, 0, Math.PI * 2)
    ctx.fillStyle = color
    ctx.fill()
    ctx.strokeStyle = '#fff'
    ctx.lineWidth = 2
    ctx.stroke()
    ctx.fillStyle = '#fff'
    ctx.font = 'bold 13px monospace'
    ctx.fillText(label, p.x + 10, p.y - 6)
  }

  if (pt1.value) drawPoint(pt1.value, 'P1', '#1890ff')

  if (pt1.value && pt2.value) {
    // 连线
    ctx.beginPath()
    ctx.moveTo(pt1.value.x, pt1.value.y)
    ctx.lineTo(pt2.value.x, pt2.value.y)
    ctx.strokeStyle = '#faad14'
    ctx.lineWidth = 2
    ctx.setLineDash([6, 3])
    ctx.stroke()
    ctx.setLineDash([])
    drawPoint(pt2.value, 'P2', '#52c41a')
  }
}

function resetPoints() {
  pt1.value = null
  pt2.value = null
  const canvas = canvasRef.value
  if (canvas) canvas.getContext('2d')!.clearRect(0, 0, canvas.width, canvas.height)
}

async function saveCalibration() {
  if (!pt1.value || !pt2.value) return
  saving.value = true
  try {
    await calibrationApi.submit(sceneId, {
      device_code: form.device_code,
      ref_length_mm: form.ref_length_mm,
      pt1_x: pt1.value.x,
      pt1_y: pt1.value.y,
      pt2_x: pt2.value.x,
      pt2_y: pt2.value.y,
    })
    ElMessage.success(`标定已保存，比例 ${scaleMmPerPx.value} mm/px`)
    resetPoints()
    loadHistory()
  } catch {
    ElMessage.error('保存失败，请检查参数后重试')
  } finally {
    saving.value = false
  }
}

async function loadHistory() {
  try {
    const res = await calibrationApi.getHistory(sceneId, { size: 5 })
    history.value = res.data?.data?.items ?? []
  } catch {
    history.value = []
  }
}

onMounted(() => {
  if (canvasRef.value) {
    canvasRef.value.width = 800
    canvasRef.value.height = 450
  }
  loadHistory()
  loadCameraOptions()
})
</script>

<style scoped lang="scss">
.canvas-wrap {
  position: relative;
  background: #1a1a1a;
  border-radius: 4px;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: crosshair;
}
.calib-canvas {
  display: block;
  max-width: 100%;
}
.canvas-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.canvas-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
  flex-wrap: wrap;
}
.point-info {
  font-size: 12px;
  font-family: monospace;
  color: #1890ff;
  &.dist { color: #faad14; }
}
.form-hint { font-size: 11px; color: #c0c4cc; margin-top: 2px; }
.card-title { font-weight: 600; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.history-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
  &:last-child { border: none; }
}
.history-row { display: flex; align-items: center; gap: 8px; margin-bottom: 2px; }
.history-scale { font-size: 13px; font-weight: 600; font-family: monospace; }
.history-detail { font-size: 11px; color: #606266; font-family: monospace; }
.history-time { font-size: 11px; color: #909399; margin-top: 2px; }
</style>
