import { ref, onUnmounted } from 'vue'

export function useSSE(url: string, onMessage: (data: unknown) => void) {
  const connected = ref(false)
  let eventSource: EventSource | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null

  function connect() {
    if (eventSource) return
    eventSource = new EventSource(url)
    eventSource.onopen = () => { connected.value = true }
    eventSource.onmessage = (event) => {
      try { onMessage(JSON.parse(event.data)) } catch {}
    }
    eventSource.onerror = () => {
      connected.value = false
      eventSource?.close()
      eventSource = null
      reconnectTimer = setTimeout(connect, 5000)
    }
  }

  function disconnect() {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    eventSource?.close()
    eventSource = null
    connected.value = false
  }

  onUnmounted(disconnect)
  return { connected, connect, disconnect }
}
