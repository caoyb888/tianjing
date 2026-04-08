/**
 * 标注审核键盘快捷键
 * 规范：标注审核工具开发计划 V1.0 §4.4
 */
import { onMounted, onUnmounted } from 'vue'

export interface HotkeyHandlers {
  /** 上一帧 A / ← */
  onPrevFrame?: () => void
  /** 下一帧 D / → */
  onNextFrame?: () => void
  /** 通过当前帧 Enter */
  onApprove?: () => void
  /** 拒绝当前帧 R */
  onReject?: () => void
  /** 删除选中框 Delete */
  onDeleteBox?: () => void
  /** 取消当前绘制/取消选中 Esc */
  onCancel?: () => void
}

export function useAnnotationHotkeys(handlers: HotkeyHandlers) {
  const handleKeydown = (e: KeyboardEvent) => {
    // 忽略输入框中的按键
    const target = e.target as HTMLElement
    if (
      target.tagName === 'INPUT' ||
      target.tagName === 'TEXTAREA' ||
      target.isContentEditable
    ) {
      return
    }

    const key = e.key

    switch (key) {
      case 'ArrowLeft':
      case 'a':
      case 'A':
        e.preventDefault()
        handlers.onPrevFrame?.()
        break
      case 'ArrowRight':
      case 'd':
      case 'D':
        e.preventDefault()
        handlers.onNextFrame?.()
        break
      case 'Enter':
        e.preventDefault()
        handlers.onApprove?.()
        break
      case 'r':
      case 'R':
        e.preventDefault()
        handlers.onReject?.()
        break
      case 'Delete':
      case 'Backspace':
        e.preventDefault()
        handlers.onDeleteBox?.()
        break
      case 'Escape':
        e.preventDefault()
        handlers.onCancel?.()
        break
    }
  }

  onMounted(() => {
    window.addEventListener('keydown', handleKeydown)
  })

  onUnmounted(() => {
    window.removeEventListener('keydown', handleKeydown)
  })

  return {
    // 可以手动触发
    trigger: handleKeydown
  }
}
