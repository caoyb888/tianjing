<template>
  <div class="live-panel">
    <!-- 标题栏 -->
    <div class="live-header">
      <span class="live-dot" :class="connState"></span>
      <span class="live-title">实时推理画面</span>
      <span class="scene-tag">{{ currentSceneId || '等待画面...' }}</span>
      <span class="backend-tag" v-if="backend">{{ backend.toUpperCase() }}</span>
    </div>

    <!-- 画布区域 -->
    <div class="canvas-wrap" ref="wrapRef">
      <canvas ref="canvasRef" class="infer-canvas" />
      <!-- 无画面占位 -->
      <div v-if="!hasFrame" class="no-frame">
        <div class="no-frame-icon">📷</div>
        <div class="no-frame-text">等待推理帧...</div>
        <div class="no-frame-sub">请确认视频注入服务已启动</div>
      </div>
    </div>

    <!-- 底部统计栏 -->
    <div class="live-stats">
      <div class="stat-item">
        <span class="stat-k">FPS</span>
        <span class="stat-v" :class="{ warn: fps < 2 }">{{ fps.toFixed(1) }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-k">推理</span>
        <span class="stat-v">{{ inferMs.toFixed(1) }} ms</span>
      </div>
      <div class="stat-item">
        <span class="stat-k">端到端</span>
        <span class="stat-v">{{ dispatchMs.toFixed(1) }} ms</span>
      </div>
      <div class="stat-item">
        <span class="stat-k">检测框</span>
        <span class="stat-v detect-count" :class="{ has_detect: detectionCount > 0 }">
          {{ detectionCount }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'

// ── 连接状态类型 ──────────────────────────────────────────
type ConnState = 'connecting' | 'connected' | 'disconnected'

// ── Props ─────────────────────────────────────────────────
const props = withDefaults(defineProps<{
  token?: string   // JWT token，用于 SSE 订阅鉴权
}>(), {
  token: '',
})

// ── 模板引用 ──────────────────────────────────────────────
const canvasRef = ref<HTMLCanvasElement>()
const wrapRef   = ref<HTMLDivElement>()

// ── 响应式状态 ────────────────────────────────────────────
const connState      = ref<ConnState>('connecting')
const hasFrame       = ref(false)
const currentSceneId = ref('')
const backend        = ref('')
const inferMs        = ref(0)
const dispatchMs     = ref(0)
const detectionCount = ref(0)
const fps            = ref(0)

// ── FPS 计算 ──────────────────────────────────────────────
let frameTimestamps: number[] = []

function updateFps() {
  const now = Date.now()
  frameTimestamps.push(now)
  // 保留最近 2 秒内的时间戳
  frameTimestamps = frameTimestamps.filter(t => now - t < 2000)
  fps.value = frameTimestamps.length / 2
}

// ── Canvas 渲染 ───────────────────────────────────────────

/** 将 minio://bucket/path 转换为 /minio-frames/bucket/path */
function minioToHttp(url: string): string {
  if (!url) return ''
  return url.replace('minio://', '/minio-frames/')
}

/** 绘制单帧：先画图像，再叠加检测框 */
function renderFrame(imageUrl: string, detections: Detection[]) {
  const canvas = canvasRef.value
  const wrap   = wrapRef.value
  if (!canvas || !wrap) return

  const img = new Image()
  img.crossOrigin = 'anonymous'
  img.onload = () => {
    // 适应容器宽高，保持 16:9 比例
    const wrapW = wrap.clientWidth
    const wrapH = wrap.clientHeight
    const scaleX = wrapW / img.naturalWidth
    const scaleY = wrapH / img.naturalHeight
    const scale  = Math.min(scaleX, scaleY)
    const drawW  = img.naturalWidth  * scale
    const drawH  = img.naturalHeight * scale
    const offsetX = (wrapW - drawW) / 2
    const offsetY = (wrapH - drawH) / 2

    canvas.width  = wrapW
    canvas.height = wrapH

    const ctx = canvas.getContext('2d')!
    ctx.clearRect(0, 0, wrapW, wrapH)
    ctx.drawImage(img, offsetX, offsetY, drawW, drawH)

    // 绘制检测框
    for (const det of detections) {
      drawBbox(ctx, det, scale, offsetX, offsetY)
    }
  }
  img.src = minioToHttp(imageUrl)
}

interface Detection {
  class_name?: string
  confidence?: number
  bbox?: { x1: number; y1: number; x2: number; y2: number }
}

/** 按置信度区间选色 */
function bboxColor(conf: number): string {
  if (conf >= 0.85) return '#ff4d4f'   // 高置信度 → 红
  if (conf >= 0.60) return '#faad14'   // 中置信度 → 橙
  return '#36cfc9'                      // 低置信度 → 青
}

function drawBbox(
  ctx: CanvasRenderingContext2D,
  det: Detection,
  scale: number,
  offsetX: number,
  offsetY: number
) {
  const bbox = det.bbox
  if (!bbox) return
  const conf  = det.confidence ?? 0
  const label = `${det.class_name ?? '?'} ${(conf * 100).toFixed(0)}%`
  const color = bboxColor(conf)

  const x = bbox.x1 * scale + offsetX
  const y = bbox.y1 * scale + offsetY
  const w = (bbox.x2 - bbox.x1) * scale
  const h = (bbox.y2 - bbox.y1) * scale

  // 检测框
  ctx.strokeStyle = color
  ctx.lineWidth   = 2
  ctx.strokeRect(x, y, w, h)

  // 标签背景
  ctx.font = 'bold 12px monospace'
  const textW = ctx.measureText(label).width + 8
  ctx.fillStyle = color
  ctx.fillRect(x, y - 18, textW, 18)

  // 标签文字
  ctx.fillStyle = '#fff'
  ctx.fillText(label, x + 4, y - 4)
}

// ── SSE 连接 ──────────────────────────────────────────────
let sse: EventSource | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

function connect() {
  const url = props.token
    ? `/api/v1/dashboard/infer/live-stream?token=${props.token}`
    : '/api/v1/dashboard/infer/live-stream'

  connState.value = 'connecting'
  sse = new EventSource(url)

  sse.addEventListener('connected', () => {
    connState.value = 'connected'
  })

  sse.addEventListener('frame', (e: MessageEvent) => {
    try {
      const data = JSON.parse(e.data)
      hasFrame.value       = true
      currentSceneId.value = data.scene_id ?? ''
      backend.value        = data.backend  ?? ''
      inferMs.value        = data.inference_time_ms   ?? 0
      dispatchMs.value     = data.dispatcher_total_ms ?? 0
      detectionCount.value = (data.detections ?? []).length
      updateFps()
      renderFrame(data.image_url, data.detections ?? [])
    } catch (err) {
      console.warn('[InferLivePanel] 解析帧数据失败', err)
    }
  })

  sse.onerror = () => {
    connState.value = 'disconnected'
    sse?.close()
    sse = null
    // 3 秒后自动重连
    reconnectTimer = setTimeout(connect, 3000)
  }
}

function disconnect() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
  sse?.close()
  sse = null
}

// ── 生命周期 ──────────────────────────────────────────────
onMounted(() => connect())
onUnmounted(() => disconnect())

watch(() => props.token, () => {
  disconnect()
  connect()
})
</script>

<style scoped lang="scss">
.live-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: rgba(5, 20, 50, 0.85);
  border: 1px solid rgba(0, 120, 255, 0.2);
  border-radius: 8px;
  overflow: hidden;
}

/* ── 标题栏 ─────────────────────────────────────────────── */
.live-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-bottom: 1px solid rgba(0, 120, 255, 0.15);
  flex-shrink: 0;
  height: 36px;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &.connecting    { background: #faad14; animation: pulse 1.2s ease-in-out infinite; }
  &.connected     { background: #52c41a; box-shadow: 0 0 6px #52c41a; }
  &.disconnected  { background: #ff4d4f; }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.3; }
}

.live-title {
  font-size: 14px;
  font-weight: 600;
  color: #c8dff5;
}

.scene-tag {
  font-size: 12px;
  color: #36cfc9;
  padding: 1px 8px;
  border: 1px solid rgba(54, 207, 201, 0.3);
  border-radius: 10px;
  background: rgba(54, 207, 201, 0.08);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.backend-tag {
  font-size: 11px;
  color: #8eb8e5;
  padding: 1px 6px;
  border: 1px solid rgba(0, 120, 255, 0.2);
  border-radius: 4px;
  background: rgba(0, 40, 100, 0.4);
  letter-spacing: 1px;
}

/* ── 画布区域 ────────────────────────────────────────────── */
.canvas-wrap {
  flex: 1;
  position: relative;
  min-height: 0;
  background: #020810;
  overflow: hidden;
}

.infer-canvas {
  display: block;
  width: 100%;
  height: 100%;
}

/* 无画面占位 */
.no-frame {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  pointer-events: none;
}

.no-frame-icon {
  font-size: 36px;
  opacity: 0.3;
}

.no-frame-text {
  font-size: 14px;
  color: #4a6b8a;
}

.no-frame-sub {
  font-size: 12px;
  color: #2a4060;
}

/* ── 底部统计栏 ──────────────────────────────────────────── */
.live-stats {
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 6px 12px;
  border-top: 1px solid rgba(0, 120, 255, 0.12);
  flex-shrink: 0;
  height: 38px;
  background: rgba(0, 10, 30, 0.5);
}

.stat-item {
  display: flex;
  align-items: baseline;
  gap: 5px;
}

.stat-k {
  font-size: 11px;
  color: #4a6b8a;
  letter-spacing: 0.5px;
}

.stat-v {
  font-size: 16px;
  font-weight: 700;
  color: #5aadff;
  font-variant-numeric: tabular-nums;

  &.warn        { color: #faad14; }
  &.has_detect  { color: #ff7a45; }
}
</style>
