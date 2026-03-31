import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useLayoutStore = defineStore(
  'layout',
  () => {
    // 侧边栏收缩状态
    const sidebarCollapsed = ref(false)
    // 当前主题
    const theme = ref<'light' | 'dark'>('light')
    // 标签页列表
    const tabs = ref<Array<{ path: string; title: string; closable: boolean }>>([])

    function toggleSidebar() {
      sidebarCollapsed.value = !sidebarCollapsed.value
    }

    function toggleTheme() {
      theme.value = theme.value === 'light' ? 'dark' : 'light'
      document.documentElement.classList.toggle('dark', theme.value === 'dark')
    }

    function addTab(tab: { path: string; title: string }) {
      const exists = tabs.value.find((t) => t.path === tab.path)
      if (!exists) {
        tabs.value.push({ ...tab, closable: tab.path !== '/dashboard' })
      }
    }

    function removeTab(path: string) {
      const idx = tabs.value.findIndex((t) => t.path === path)
      if (idx !== -1) tabs.value.splice(idx, 1)
    }

    return {
      sidebarCollapsed,
      theme,
      tabs,
      toggleSidebar,
      toggleTheme,
      addTab,
      removeTab,
    }
  },
  {
    persist: {
      paths: ['sidebarCollapsed', 'theme'],
    },
  }
)
