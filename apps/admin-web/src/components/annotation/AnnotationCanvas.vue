<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useCanvasCoords, type BBox } from '@/composables/useCanvasCoords'

/**
 * 检测框数据结构
 */
export interface Detection {
  id: string
  classId: number
  className: string
  confidence: number
  bbox: BBox
  isNew?: boolean
}

interface Props {
  /** MinIO 图像地址 */
  frameUrl: string
  /** 当前帧标注框（含原始 + 校正） */
  detections: Detection[]
  /** 类别名 → 颜色映射，用于标签背景着色 */
  classColors?: Record<string, string>
  /** 原图宽（默认 1920） */
  imageWidth?: number
  /** 原图高（默认 1080） */
  imageHeight?: number
  /** 是否只读模式 */
  readonly?: boolean
}

interface Emits {
  (e: 'update:detections', detections: Detection[]): void
  (e: 'box-selected', boxId: string | null): void
}

const props = withDefaults(defineProps<Props>(), {
  imageWidth: 1920,
  imageHeight: 1080,
  readonly: false,
  classColors: () => ({})
})

const emit = defineEmits<Emits>()

// ============================================================================
// Refs
// ============================================================================

const wrapperRef = ref<HTMLElement | null>(null)
const svgRef = ref<SVGSVGElement | null>(null)
const imageRef = ref<HTMLImageElement | null>(null)
const isImageLoaded = ref(false)

/** 当前选中的框ID */
const selectedId = ref<string | null>(null)

/** 是否正在绘制新框 */
const isDrawing = ref(false)

/** 正在绘制的临时框（显示坐标） */
const drawingRect = ref<{ x: number; y: number; width: number; height: number } | null>(null)

/** 拖拽状态 */
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0 })
const dragBoxStart = ref<BBox | null>(null)
const resizeHandle = ref<string | null>(null)

// ============================================================================
// 坐标换算
// ============================================================================

const coords = useCanvasCoords(wrapperRef, {
  imageWidth: props.imageWidth,
  imageHeight: props.imageHeight
})

/** 图像加载完成后计算显示尺寸 */
const onImageLoad = () => {
  isImageLoaded.value = true
  nextTick(() => {
    coords.calculateDisplaySize()
  })
}

// 监听窗口变化重新计算尺寸
window.addEventListener('resize', coords.calculateDisplaySize)

// ============================================================================
// 框列表（内部可修改的副本）
// ============================================================================

const boxes = computed({
  get: () => props.detections,
  set: (val) => emit('update:detections', val)
})

const selectedBox = computed(() => {
  if (!selectedId.value) return null
  return boxes.value.find((b) => b.id === selectedId.value) || null
})

// ============================================================================
// 事件处理
// ============================================================================

/** 获取鼠标在 SVG 坐标系中的位置（相对于 SVG 左上角） */
function getMousePos(e: MouseEvent): { x: number; y: number } {
  const rect = svgRef.value?.getBoundingClientRect()
  if (!rect) return { x: 0, y: 0 }
  return {
    x: e.clientX - rect.left,
    y: e.clientY - rect.top
  }
}

/** 判断点是否在框内 */
function isPointInBox(x: number, y: number, bbox: BBox): boolean {
  const displayBox = coords.toDisplayBBox(bbox)
  return (
    x >= displayBox.x &&
    x <= displayBox.x + displayBox.width &&
    y >= displayBox.y &&
    y <= displayBox.y + displayBox.height
  )
}

/** 鼠标按下 - 开始绘制或选中 */
function onMouseDown(e: MouseEvent) {
  if (props.readonly) return

  const pos = getMousePos(e)

  // 检查是否点击了某个框
  let clickedBox: Detection | null = null
  for (const box of boxes.value) {
    if (isPointInBox(pos.x, pos.y, box.bbox)) {
      clickedBox = box
      break
    }
  }

  if (clickedBox) {
    // 选中已有框
    selectedId.value = clickedBox.id
    emit('box-selected', clickedBox.id)
    isDragging.value = true
    dragStart.value = pos
    dragBoxStart.value = { ...clickedBox.bbox }
  } else {
    // 空白处开始绘制新框
    selectedId.value = null
    emit('box-selected', null)
    isDrawing.value = true
    drawingRect.value = {
      x: pos.x,
      y: pos.y,
      width: 0,
      height: 0
    }
  }
}

/** 鼠标移动 - 绘制中或拖拽中 */
function onMouseMove(e: MouseEvent) {
  if (props.readonly) return
  const pos = getMousePos(e)

  if (isDrawing.value && drawingRect.value) {
    // 正在绘制新框
    const rect = drawingRect.value
    rect.width = pos.x - rect.x
    rect.height = pos.y - rect.y
  } else if (isDragging.value && selectedBox.value && dragBoxStart.value) {
    // 正在拖拽移动框
    const dx = coords.toOriginalX(pos.x) - coords.toOriginalX(dragStart.value.x)
    const dy = coords.toOriginalY(pos.y) - coords.toOriginalY(dragStart.value.y)

    const newBox: BBox = {
      x1: dragBoxStart.value.x1 + dx,
      y1: dragBoxStart.value.y1 + dy,
      x2: dragBoxStart.value.x2 + dx,
      y2: dragBoxStart.value.y2 + dy
    }

    // 更新框位置
    const idx = boxes.value.findIndex((b) => b.id === selectedBox.value!.id)
    if (idx >= 0) {
      const newBoxes = [...boxes.value]
      newBoxes[idx] = { ...newBoxes[idx], bbox: newBox }
      boxes.value = newBoxes
    }
  }
}

/** 鼠标释放 - 完成绘制 */
function onMouseUp() {
  if (props.readonly) return

  if (isDrawing.value && drawingRect.value) {
    // 完成绘制新框
    const rect = drawingRect.value
    // 确保宽高为正
    let { x, y, width, height } = rect
    if (width < 0) {
      x += width
      width = -width
    }
    if (height < 0) {
      y += height
      height = -height
    }

    // 最小框大小限制（20px）
    if (width > 20 && height > 20) {
      const bbox = coords.toOriginalBBox({ x, y, width, height })
      const newBox: Detection = {
        id: `box_${Date.now()}`,
        classId: 0,
        className: 'object',
        confidence: 1.0,
        bbox,
        isNew: true
      }
      boxes.value = [...boxes.value, newBox]
      selectedId.value = newBox.id
      emit('box-selected', newBox.id)
    }

    isDrawing.value = false
    drawingRect.value = null
  }

  isDragging.value = false
  dragBoxStart.value = null
}

/** 选中框 */
function selectBox(id: string) {
  selectedId.value = id
  emit('box-selected', id)
}

/** 删除选中框 */
function deleteSelectedBox() {
  if (!selectedId.value) return
  boxes.value = boxes.value.filter((b) => b.id !== selectedId.value)
  selectedId.value = null
  emit('box-selected', null)
}

/** 取消选中/绘制 */
function cancel() {
  if (isDrawing.value) {
    isDrawing.value = false
    drawingRect.value = null
  } else {
    selectedId.value = null
    emit('box-selected', null)
  }
}

/** 更新框的类别 */
function updateBoxClass(id: string, classId: number, className: string) {
  const idx = boxes.value.findIndex((b) => b.id === id)
  if (idx >= 0) {
    const newBoxes = [...boxes.value]
    newBoxes[idx] = { ...newBoxes[idx], classId, className }
    boxes.value = newBoxes
  }
}

// ============================================================================
// 暴露方法
// ============================================================================

defineExpose({
  deleteSelectedBox,
  cancel,
  updateBoxClass,
  selectedId
})
</script>

<template>
  <div ref="wrapperRef" class="canvas-wrapper">
    <!-- 图像层 -->
    <img
      ref="imageRef"
      :src="frameUrl"
      class="frame-image"
      @load="onImageLoad"
      draggable="false"
    />

    <!-- SVG 覆盖层 -->
    <svg
      v-if="isImageLoaded"
      ref="svgRef"
      class="annotation-layer"
      :width="coords.displayWidth.value"
      :height="coords.displayHeight.value"
      @mousedown="onMouseDown"
      @mousemove="onMouseMove"
      @mouseup="onMouseUp"
      @mouseleave="onMouseUp"
    >
      <!-- 已有标注框 -->
      <g
        v-for="box in boxes"
        :key="box.id"
        :class="['box-group', { selected: box.id === selectedId }]"
        @mousedown.stop="selectBox(box.id)"
      >
        <!-- 框（按类别着色，选中态保持蓝色） -->
        <rect
          :x="coords.toDisplayX(box.bbox.x1)"
          :y="coords.toDisplayY(box.bbox.y1)"
          :width="coords.toDisplayWidth(box.bbox)"
          :height="coords.toDisplayHeight(box.bbox)"
          :class="['bbox-rect', { modified: box.isNew }]"
          :style="box.id !== selectedId ? { stroke: props.classColors[box.className] ?? '#67c23a' } : {}"
        />

        <!-- 标签文字背景（按类别着色） -->
        <rect
          :x="coords.toDisplayX(box.bbox.x1)"
          :y="coords.toDisplayY(box.bbox.y1) - 20"
          :width="box.className.length * 12 + 8"
          height="20"
          class="label-bg"
          :style="{ fill: props.classColors[box.className] ?? '#67c23a' }"
        />

        <!-- 标签文字 -->
        <text
          :x="coords.toDisplayX(box.bbox.x1) + 4"
          :y="coords.toDisplayY(box.bbox.y1) - 5"
          class="label-text"
        >
          {{ box.className }}
        </text>

        <!-- 8个缩放手柄（仅在选中时显示且非只读） -->
        <template v-if="box.id === selectedId && !readonly">
          <circle
            v-for="(handle, idx) in coords.getHandles(box.bbox)"
            :key="idx"
            :cx="handle.x + 4"
            :cy="handle.y + 4"
            r="4"
            class="resize-handle"
            @mousedown.stop
          />
        </template>
      </g>

      <!-- 正在绘制的新框（虚线） -->
      <rect
        v-if="isDrawing && drawingRect"
        :x="drawingRect.x"
        :y="drawingRect.y"
        :width="Math.abs(drawingRect.width)"
        :height="Math.abs(drawingRect.height)"
        class="drawing-rect"
      />
    </svg>

    <!-- 加载状态 -->
    <div v-if="!isImageLoaded" class="loading-overlay">
      <el-icon class="loading-icon"><Loading /></el-icon>
      <span>加载中...</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.canvas-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #1a1a1a;
  overflow: hidden;
}

.frame-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
}

.annotation-layer {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  cursor: crosshair;
  user-select: none;
}

.box-group {
  cursor: pointer;

  &.selected .bbox-rect {
    stroke: #409eff;
    stroke-width: 3;
  }
}

.bbox-rect {
  fill: transparent;
  stroke: #67c23a;
  stroke-width: 2;

  &.modified {
    stroke: #e6a23c;
  }
}

.label-bg {
  fill: #67c23a;
  fill-opacity: 0.8;
}

.label-text {
  fill: white;
  font-size: 12px;
  font-weight: bold;
  pointer-events: none;
}

.resize-handle {
  fill: #409eff;
  stroke: white;
  stroke-width: 1;
  cursor: pointer;

  &:hover {
    fill: #66b1ff;
    r: 5;
  }
}

.drawing-rect {
  fill: rgba(64, 158, 255, 0.1);
  stroke: #409eff;
  stroke-width: 2;
  stroke-dasharray: 5 5;
}

.loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
  gap: 8px;
}

.loading-icon {
  font-size: 32px;
  animation: rotate 1s linear infinite;
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
