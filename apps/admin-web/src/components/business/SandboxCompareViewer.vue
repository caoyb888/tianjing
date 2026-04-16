<template>
  <div class="sandbox-compare-viewer">
    <el-row :gutter="8">
      <el-col :span="12">
        <div class="panel-label">
          <el-tag type="success" effect="plain" size="small">生产推理 · 实线框</el-tag>
        </div>
        <canvas ref="prodCanvas" class="compare-canvas" />
      </el-col>
      <el-col :span="12">
        <div class="panel-label">
          <el-tag type="warning" effect="plain" size="small">Sandbox 推理 · 虚线框</el-tag>
        </div>
        <canvas ref="sandboxCanvas" class="compare-canvas" />
      </el-col>
    </el-row>
    <div v-if="!imageUrl" class="compare-empty">暂无对比帧数据</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'

interface DetectionBox {
  x1: number
  y1: number
  x2: number
  y2: number
  class_name?: string
  confidence?: number
}

const props = defineProps<{
  imageUrl: string
  productionDetections?: DetectionBox[]
  sandboxDetections?: DetectionBox[]
}>()

const prodCanvas = ref<HTMLCanvasElement>()
const sandboxCanvas = ref<HTMLCanvasElement>()

function drawDetections(
  canvas: HTMLCanvasElement,
  img: HTMLImageElement,
  boxes: DetectionBox[],
  dashed: boolean
) {
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const maxW = canvas.parentElement?.clientWidth || 300
  const scale = maxW / img.naturalWidth
  canvas.width = maxW
  canvas.height = img.naturalHeight * scale

  ctx.drawImage(img, 0, 0, canvas.width, canvas.height)

  boxes.forEach((box) => {
    const x = box.x1 * scale
    const y = box.y1 * scale
    const w = (box.x2 - box.x1) * scale
    const h = (box.y2 - box.y1) * scale

    ctx.save()
    ctx.strokeStyle = dashed ? '#faad14' : '#52c41a'
    ctx.lineWidth = 2
    ctx.setLineDash(dashed ? [6, 4] : [])
    ctx.strokeRect(x, y, w, h)

    const label = box.class_name || ''
    const conf = box.confidence != null ? ` ${(box.confidence * 100).toFixed(1)}%` : ''
    const text = `${label}${conf}`.trim()
    if (text) {
      ctx.font = '11px sans-serif'
      const tw = ctx.measureText(text).width
      ctx.fillStyle = dashed ? 'rgba(250,173,20,0.85)' : 'rgba(82,196,26,0.85)'
      ctx.fillRect(x, y - 16, tw + 6, 16)
      ctx.fillStyle = '#fff'
      ctx.setLineDash([])
      ctx.fillText(text, x + 3, y - 3)
    }
    ctx.restore()
  })
}

function render() {
  if (!props.imageUrl) return
  const img = new Image()
  img.crossOrigin = 'anonymous'
  img.onload = () => {
    if (prodCanvas.value) {
      drawDetections(prodCanvas.value, img, props.productionDetections || [], false)
    }
    if (sandboxCanvas.value) {
      drawDetections(sandboxCanvas.value, img, props.sandboxDetections || [], true)
    }
  }
  img.src = props.imageUrl
}

watch(
  () => [props.imageUrl, props.productionDetections, props.sandboxDetections],
  render,
  { deep: true }
)

const resizeObserver = new ResizeObserver(render)

onMounted(() => {
  if (prodCanvas.value?.parentElement) resizeObserver.observe(prodCanvas.value.parentElement)
  render()
})

onUnmounted(() => {
  resizeObserver.disconnect()
})
</script>

<style scoped lang="scss">
.sandbox-compare-viewer {
  width: 100%;
  position: relative;
}

.panel-label {
  text-align: center;
  margin-bottom: 6px;
}

.compare-canvas {
  display: block;
  width: 100%;
  background: #0a0a0a;
  border-radius: 4px;
}

.compare-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 40px 0;
  font-size: 14px;
}
</style>
