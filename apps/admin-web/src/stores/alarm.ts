import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AlarmRecord } from '@/types'
import { useAuthStore } from '@/stores/auth'
import { alarmApi } from '@/api/alarm'

export const useAlarmStore = defineStore('alarm', () => {
  // 实时告警队列（最多缓存 50 条）
  const realtimeAlarms = ref<AlarmRecord[]>([])
  // 未读计数
  const unreadCount = ref(0)
  // SSE 连接状态
  const connected = ref(false)
  let eventSource: EventSource | null = null

  const hasUnread = computed(() => unreadCount.value > 0)

  function addAlarm(alarm: AlarmRecord) {
    // Sandbox 告警不进入实时队列
    if (alarm.isSandbox) return
    realtimeAlarms.value.unshift(alarm)
    if (realtimeAlarms.value.length > 50) {
      realtimeAlarms.value = realtimeAlarms.value.slice(0, 50)
    }
    unreadCount.value++
  }

  function markAllRead() {
    unreadCount.value = 0
  }

  /** 初始化时拉取最近历史告警预填充列表（避免页面打开时一片空白） */
  async function loadRecentAlarms() {
    try {
      const res = await alarmApi.list({ page: 1, size: 20, isSandbox: false, sort: 'alarm_at:desc' })
      const items: AlarmRecord[] = res.data?.data?.items ?? []
      // 只填充不重复的（SSE 可能已推了部分）
      const existingIds = new Set(realtimeAlarms.value.map(a => a.alarmId))
      for (const alarm of items) {
        if (!existingIds.has(alarm.alarmId)) {
          realtimeAlarms.value.push(alarm)
        }
      }
      if (realtimeAlarms.value.length > 50) {
        realtimeAlarms.value = realtimeAlarms.value.slice(0, 50)
      }
    } catch {
      // 预填充失败不影响 SSE 正常工作
    }
  }

  function startSSE() {
    if (eventSource) return
    // 安全规范 S2-01：Token 只存内存，从 authStore 读取，禁止从 localStorage 读取
    // EventSource 不支持自定义 Header，通过 URL 参数传递（后端仅在 SSE 端点接受此方式）
    const token = useAuthStore().accessToken
    if (!token) return
    // 先拉历史数据预填充，再建立 SSE
    loadRecentAlarms()
    eventSource = new EventSource(`/api/v1/dashboard/alarms/realtime?token=${token}`)
    eventSource.onopen = () => {
      connected.value = true
    }
    eventSource.onmessage = (event) => {
      try {
        const alarm: AlarmRecord = JSON.parse(event.data)
        addAlarm(alarm)
      } catch {}
    }
    eventSource.onerror = () => {
      connected.value = false
      // 清空引用，确保 5 秒后重连时 startSSE 能正常建立新连接
      eventSource?.close()
      eventSource = null
      setTimeout(startSSE, 5000)
    }
  }

  function stopSSE() {
    eventSource?.close()
    eventSource = null
    connected.value = false
  }

  return {
    realtimeAlarms,
    unreadCount,
    connected,
    hasUnread,
    addAlarm,
    markAllRead,
    loadRecentAlarms,
    startSSE,
    stopSSE,
  }
})
