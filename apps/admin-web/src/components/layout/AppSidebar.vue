<template>
  <aside class="app-sidebar" :class="{ collapsed: layoutStore.sidebarCollapsed }">
    <!-- Logo -->
    <div class="sidebar-logo">
      <div class="logo-icon">天镜</div>
      <span v-if="!layoutStore.sidebarCollapsed" class="logo-text">天柱·天镜</span>
    </div>

    <!-- 导航菜单 -->
    <el-scrollbar class="sidebar-scroll">
      <el-menu
        :default-active="activeMenu"
        :collapse="layoutStore.sidebarCollapsed"
        :collapse-transition="false"
        router
        background-color="#0D1B2E"
        text-color="rgba(255,255,255,.72)"
        active-text-color="#E8F0FE"
        class="sidebar-menu"
      >
        <template v-for="item in visibleMenus" :key="item.path || item.title">
          <!-- 有子菜单 -->
          <el-sub-menu v-if="item.children" :index="item.title!">
            <template #title>
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              <el-icon><component :is="child.icon" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>

          <!-- 无子菜单 -->
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-scrollbar>

    <!-- 收缩按钮 -->
    <div class="collapse-btn" @click="layoutStore.toggleSidebar">
      <el-icon>
        <component :is="layoutStore.sidebarCollapsed ? 'Expand' : 'Fold'" />
      </el-icon>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useLayoutStore } from '@/stores/layout'
import { useAuthStore } from '@/stores/auth'
import { SIDEBAR_MENUS } from '@/constants'
import type { UserRole } from '@/types'

const route = useRoute()
const layoutStore = useLayoutStore()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

const visibleMenus = computed(() =>
  SIDEBAR_MENUS.filter((menu) => {
    if (!menu.roles || menu.roles.length === 0) return true
    return authStore.hasRole(menu.roles as UserRole[])
  })
)
</script>

<style scoped lang="scss">
.app-sidebar {
  width: 220px;
  min-height: 100vh;
  background: var(--tj-bg-sidebar);
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  flex-shrink: 0;

  &.collapsed {
    width: 64px;
  }
}

.sidebar-logo {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  overflow: hidden;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: var(--tj-primary);
  border-radius: var(--tj-radius-md);
  color: #fff;
  font-size: 12px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  color: #E8F0FE;
  font-size: 15px;
  font-weight: bold;
  margin-left: 10px;
  white-space: nowrap;
  letter-spacing: 0.5px;
}

.sidebar-scroll {
  flex: 1;
}

.sidebar-menu {
  border: none;
}

// 侧边栏菜单激活项指示条（左侧 3px 亮线）
:deep(.el-menu-item.is-active) {
  background: rgba(21, 87, 176, 0.35) !important;
  border-left: 3px solid var(--tj-primary-light) !important;
  color: #fff !important;
}

.collapse-btn {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.6);
  cursor: pointer;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  transition: color 0.2s;

  &:hover {
    color: #fff;
  }
}
</style>
