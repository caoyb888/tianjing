<template>
  <div class="alarm-image-viewer" ref="containerRef">
    <canvas ref="canvasRef" class="viewer-canvas" />
    <div v-if="!imageUrl" class="viewer-empty">暂无图像</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'

interface BBox {
  x1: number
  y1: number
  x2: number
  y2: number
  label?: string
  confidence?: number
  class_name?: string
}

const props = defineProps<{
  imageUrl: string
  detections?: BBox[]
  isSandbox?: boolean
}>()

const containerRef = ref<HTMLDivElement>()
const canvasRef = ref<HTMLCanvasElement>()
let imgEl: HTMLImageElement | null = null

function drawCanvas() {
  const canvas = canvasRef.value
  const container = containerRef.value
  if (!canvas || !container || !imgEl) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  // 适应容器宽度，保持原始宽高比
  const maxW = container.clientWidth || 600
  const scale = maxW / imgEl.naturalWidth
  const drawW = imgEl.naturalWidth * scale
  const drawH = imgEl.naturalHeight * scale

  canvas.width = drawW
  canvas.height = drawH

  ctx.drawImage(imgEl, 0, 0, drawW, drawH)

  const bboxes = props.detections || []
  bboxes.forEach((box) => {
    const x = box.x1 * scale
    const y = box.y1 * scale
    const w = (box.x2 - box.x1) * scale
    const h = (box.y2 - box.y1) * scale

    ctx.save()
    ctx.strokeStyle = props.isSandbox ? '#faad14' : '#f5222d'
    ctx.lineWidth = 2

    if (props.isSandbox) {
      // Sandbox 用虚线框（规范要求：禁止用实线标注框）
      ctx.setLineDash([6, 4])
    } else {
      ctx.setLineDash([])
    }

    ctx.strokeRect(x, y, w, h)

    // 标注文字背景
    const label = box.class_name || box.label || ''
    const conf = box.confidence != null ? `${(box.confidence * 100).toFixed(1)}%` : ''
    const text = conf ? `${label} ${conf}` : label
    if (text) {
      ctx.font = '12px sans-serif'
      const textW = ctx.measureText(text).width
      ctx.fillStyle = props.isSandbox ? 'rgba(250,173,20,0.85)' : 'rgba(245,34,34,0.85)'
      ctx.fillRect(x, y - 18, textW + 8, 18)
      ctx.fillStyle = '#fff'
      ctx.setLineDash([])
      ctx.fillText(text, x + 4, y - 4)
    }

    ctx.restore()
  })
}

function loadImage() {
  if (!props.imageUrl) return
  imgEl = new Image()
  imgEl.crossOrigin = 'anonymous'
  imgEl.onload = drawCanvas
  imgEl.src = props.imageUrl
}

watch(() => [props.imageUrl, props.detections, props.isSandbox], loadImage, { deep: true })

const resizeObserver = new ResizeObserver(drawCanvas)

onMounted(() => {
  if (containerRef.value) resizeObserver.observe(containerRef.value)
  loadImage()
})

onUnmounted(() => {
  resizeObserver.disconnect()
})
</script>

<style scoped lang="scss">
.alarm-image-viewer {
  width: 100%;
  position: relative;
  background: #0a0a0a;
  border-radius: 4px;
  overflow: hidden;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.viewer-canvas {
  display: block;
  max-width: 100%;
}

.viewer-empty {
  color: #c0c4cc;
  font-size: 14px;
  padding: 60px 0;
}
</style>
