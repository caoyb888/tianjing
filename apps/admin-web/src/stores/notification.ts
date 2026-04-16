import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'

interface Notification {
  id: string
  type: 'success' | 'warning' | 'info' | 'error'
  title: string
  message?: string
  duration?: number
}

export const useNotificationStore = defineStore('notification', () => {
  const queue = ref<Notification[]>([])

  function notify(notification: Omit<Notification, 'id'>) {
    const id = Date.now().toString()
    const item = { ...notification, id }
    queue.value.push(item)

    if (notification.title && notification.message) {
      ElNotification({
        title: notification.title,
        message: notification.message,
        type: notification.type,
        duration: notification.duration ?? 4500,
      })
    } else {
      ElMessage({
        message: notification.title,
        type: notification.type,
        duration: notification.duration ?? 3000,
      })
    }

    // 自动清理
    setTimeout(() => {
      const idx = queue.value.findIndex((n) => n.id === id)
      if (idx !== -1) queue.value.splice(idx, 1)
    }, (notification.duration ?? 4500) + 100)
  }

  function success(message: string) {
    notify({ type: 'success', title: message })
  }

  function error(message: string) {
    notify({ type: 'error', title: message })
  }

  function warning(message: string) {
    notify({ type: 'warning', title: message })
  }

  function info(message: string) {
    notify({ type: 'info', title: message })
  }

  return { queue, notify, success, error, warning, info }
})
