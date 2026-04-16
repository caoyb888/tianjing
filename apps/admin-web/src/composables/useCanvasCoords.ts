/**
 * 标注画布坐标换算工具
 * 原图分辨率（1920×1080）↔ 显示尺寸之间的双向换算
 */
import { ref, computed, type Ref } from 'vue'

export interface CanvasCoordOptions {
  /** 原图宽度（默认 1920） */
  imageWidth?: number
  /** 原图高度（默认 1080） */
  imageHeight?: number
}

export interface BBox {
  x1: number
  y1: number
  x2: number
  y2: number
}

export function useCanvasCoords(
  containerRef: Ref<HTMLElement | null>,
  options: CanvasCoordOptions = {}
) {
  const { imageWidth = 1920, imageHeight = 1080 } = options

  /** 显示区域尺寸 */
  const displayWidth = ref(0)
  const displayHeight = ref(0)

  /** 缩放比例（原图坐标 → 显示坐标） */
  const scaleX = computed(() => {
    if (displayWidth.value === 0) return 1
    return displayWidth.value / imageWidth
  })

  const scaleY = computed(() => {
    if (displayHeight.value === 0) return 1
    return displayHeight.value / imageHeight
  })

  /**
   * 计算显示区域尺寸（在图像加载完成后调用）
   */
  function calculateDisplaySize() {
    const container = containerRef.value
    if (!container) return

    const rect = container.getBoundingClientRect()
    // 保持宽高比
    const imageRatio = imageWidth / imageHeight
    const containerRatio = rect.width / rect.height

    if (containerRatio > imageRatio) {
      // 容器更宽，以高度为基准
      displayHeight.value = rect.height
      displayWidth.value = rect.height * imageRatio
    } else {
      // 容器更高或等比，以宽度为基准
      displayWidth.value = rect.width
      displayHeight.value = rect.width / imageRatio
    }
  }

  /**
   * 原图坐标 → 显示坐标
   */
  function toDisplayX(x: number): number {
    return Math.round(x * scaleX.value)
  }

  function toDisplayY(y: number): number {
    return Math.round(y * scaleY.value)
  }

  function toDisplayWidth(bbox: BBox): number {
    return Math.round((bbox.x2 - bbox.x1) * scaleX.value)
  }

  function toDisplayHeight(bbox: BBox): number {
    return Math.round((bbox.y2 - bbox.y1) * scaleY.value)
  }

  /**
   * 显示坐标 → 原图坐标
   */
  function toOriginalX(x: number): number {
    return Math.round(x / scaleX.value)
  }

  function toOriginalY(y: number): number {
    return Math.round(y / scaleY.value)
  }

  /**
   * 将显示坐标系的 bbox 转换为原图坐标
   */
  function toOriginalBBox(displayBBox: {
    x: number
    y: number
    width: number
    height: number
  }): BBox {
    const x1 = toOriginalX(displayBBox.x)
    const y1 = toOriginalY(displayBBox.y)
    const x2 = toOriginalX(displayBBox.x + displayBBox.width)
    const y2 = toOriginalY(displayBBox.y + displayBBox.height)
    return { x1, y1, x2, y2 }
  }

  /**
   * 将原图坐标系的 bbox 转换为显示坐标
   */
  function toDisplayBBox(bbox: BBox): {
    x: number
    y: number
    width: number
    height: number
  } {
    return {
      x: toDisplayX(bbox.x1),
      y: toDisplayY(bbox.y1),
      width: toDisplayWidth(bbox),
      height: toDisplayHeight(bbox)
    }
  }

  /**
   * 获取 8 个缩放手柄的位置
   */
  function getHandles(bbox: BBox) {
    const { x, y, width, height } = toDisplayBBox(bbox)
    const handleSize = 8
    const offset = handleSize / 2

    return [
      { x: x - offset, y: y - offset, cursor: 'nw-resize', position: 'nw' },
      { x: x + width / 2 - offset, y: y - offset, cursor: 'n-resize', position: 'n' },
      { x: x + width - offset, y: y - offset, cursor: 'ne-resize', position: 'ne' },
      { x: x + width - offset, y: y + height / 2 - offset, cursor: 'e-resize', position: 'e' },
      { x: x + width - offset, y: y + height - offset, cursor: 'se-resize', position: 'se' },
      { x: x + width / 2 - offset, y: y + height - offset, cursor: 's-resize', position: 's' },
      { x: x - offset, y: y + height - offset, cursor: 'sw-resize', position: 'sw' },
      { x: x - offset, y: y + height / 2 - offset, cursor: 'w-resize', position: 'w' }
    ]
  }

  return {
    // 尺寸
    displayWidth,
    displayHeight,
    scaleX,
    scaleY,
    // 方法
    calculateDisplaySize,
    // 原图 → 显示
    toDisplayX,
    toDisplayY,
    toDisplayWidth,
    toDisplayHeight,
    toDisplayBBox,
    // 显示 → 原图
    toOriginalX,
    toOriginalY,
    toOriginalBBox,
    // 手柄
    getHandles
  }
}
