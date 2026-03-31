<template>
  <header class="app-header">
    <!-- 面包屑 -->
    <AppBreadcrumb />

    <!-- 右侧操作区 -->
    <div class="header-right">
      <!-- 告警铃铛 -->
      <el-badge :value="alarmStore.unreadCount" :max="99" :hidden="!alarmStore.hasUnread">
        <el-button
          text
          :icon="Bell"
          class="header-btn"
          @click="showAlarmDrawer = true"
        />
      </el-badge>

      <!-- 全屏 -->
      <el-tooltip content="全屏">
        <el-button text :icon="FullScreen" class="header-btn" @click="toggleFullscreen" />
      </el-tooltip>

      <!-- 用户菜单 -->
      <el-dropdown @command="handleCommand">
        <div class="user-info">
          <el-avatar :size="32" class="user-avatar">
            {{ authStore.username.charAt(0).toUpperCase() }}
          </el-avatar>
          <span class="username">{{ authStore.username }}</span>
          <el-icon class="arrow"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon> 个人信息
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon> 退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>

  <!-- 实时告警抽屉 -->
  <el-drawer v-model="showAlarmDrawer" title="实时告警" :size="400" @open="alarmStore.markAllRead">
    <div v-if="alarmStore.realtimeAlarms.length === 0" class="empty-alarms">
      <el-empty description="暂无告警" />
    </div>
    <div v-else class="alarm-list">
      <div
        v-for="alarm in alarmStore.realtimeAlarms"
        :key="alarm.alarmId"
        class="alarm-item"
        @click="goToAlarm(alarm.alarmId)"
      >
        <el-tag :type="alarmLevelType(alarm.alarmLevel)" size="small">
          {{ alarm.alarmLevel }}
        </el-tag>
        <div class="alarm-info">
          <div class="alarm-type">{{ alarm.anomalyType }}</div>
          <div class="alarm-meta">{{ alarm.sceneId }} · {{ formatDateTime(alarm.timestamp) }}</div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, FullScreen, ArrowDown, User, SwitchButton } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import AppBreadcrumb from './AppBreadcrumb.vue'
import { useAuthStore } from '@/stores/auth'
import { useAlarmStore } from '@/stores/alarm'
import { AlarmLevel } from '@/types'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const authStore = useAuthStore()
const alarmStore = useAlarmStore()
const showAlarmDrawer = ref(false)

function alarmLevelType(level: AlarmLevel) {
  const map = { [AlarmLevel.CRITICAL]: 'danger', [AlarmLevel.WARNING]: 'warning', [AlarmLevel.INFO]: 'info' }
  return (map[level] || 'info') as 'danger' | 'warning' | 'info'
}

function goToAlarm(alarmId: string) {
  showAlarmDrawer.value = false
  router.push(`/alarms/${alarmId}`)
}

function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

async function handleCommand(command: string) {
  if (command === 'logout') {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    await authStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped lang="scss">
.app-header {
  height: 64px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-btn {
  font-size: 18px;
  color: #595959;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 0 8px;

  &:hover {
    background: #f5f5f5;
    border-radius: 4px;
  }
}

.username {
  font-size: 14px;
  color: #303133;
}

.alarm-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;

  &:hover {
    background: #fafafa;
  }
}

.alarm-type {
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}

.alarm-meta {
  font-size: 12px;
  color: #909399;
}
</style>
