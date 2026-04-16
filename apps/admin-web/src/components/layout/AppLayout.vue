<template>
  <div class="app-layout" :class="{ 'sidebar-collapsed': layoutStore.sidebarCollapsed }">
    <AppSidebar />
    <div class="main-container">
      <AppHeader />
      <main class="page-content">
        <router-view />
      </main>
      <AppFooter />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import AppSidebar from './AppSidebar.vue'
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
import { useLayoutStore } from '@/stores/layout'
import { useAlarmStore } from '@/stores/alarm'

const layoutStore = useLayoutStore()
const alarmStore = useAlarmStore()

onMounted(() => {
  alarmStore.startSSE()
})

onUnmounted(() => {
  alarmStore.stopSSE()
})
</script>

<style scoped lang="scss">
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: margin-left 0.3s;
}

.page-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--tj-space-md);
  background: var(--tj-bg-page);
}
</style>
