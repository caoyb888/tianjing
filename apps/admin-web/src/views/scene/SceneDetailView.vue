<template>
  <div class="scene-detail" v-loading="loading">
    <PageHeader :title="scene?.name || '场景详情'" :description="`场景 ID：${sceneId}`">
      <template #actions>
        <el-button :icon="Edit" @click="$router.push(`/scenes/${sceneId}/edit`)">编辑</el-button>
        <el-button :icon="Share" @click="$router.push(`/scenes/${sceneId}/workflow`)">查看编排</el-button>
        <el-button :icon="Clock" @click="$router.push(`/scenes/${sceneId}/history`)">配置历史</el-button>
        <el-button
          v-if="scene"
          :type="scene.status === 'active' ? 'warning' : 'success'"
          @click="toggleStatus"
        >
          {{ scene?.status === 'active' ? '停用场景' : '启用场景' }}
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" v-if="scene">
      <!-- 基本信息 -->
      <el-col :lg="12" :md="24">
        <el-card shadow="never" class="info-card">
          <template #header><span class="card-title">基本信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="场景ID">{{ scene.sceneId }}</el-descriptions-item>
            <el-descriptions-item label="场景名称">{{ scene.name }}</el-descriptions-item>
            <el-descriptions-item label="所属厂部">
              {{ FACTORY_CONFIG[scene.factory as Factory]?.label }}
            </el-descriptions-item>
            <el-descriptions-item label="场景类别">
              {{ SCENE_CATEGORY_CONFIG[scene.category as SceneCategory]?.label }}
            </el-descriptions-item>
            <el-descriptions-item label="当前状态">
              <StatusBadge :status="scene.status" />
            </el-descriptions-item>
            <el-descriptions-item label="配置版本">v{{ scene.version }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ scene.createdBy }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(scene.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="最后更新">{{ formatDateTime(scene.updatedAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>

      <!-- 算法配置 -->
      <el-col :lg="12" :md="24">
        <el-card shadow="never" class="info-card">
          <template #header><span class="card-title">算法配置</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="插件ID">
              {{ (scene.algorithmConfig as any)?.plugin_id || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="置信度阈值">
              {{ (scene.algorithmConfig as any)?.conf_threshold ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="IOU 阈值">
              {{ (scene.algorithmConfig as any)?.iou_threshold ?? '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" class="info-card" style="margin-top: 16px">
          <template #header><span class="card-title">告警配置</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="告警级别">
              <StatusBadge
                :status="(scene.alarmConfig as any)?.level || 'WARNING'"
                :map="alarmLevelMap"
              />
            </el-descriptions-item>
            <el-descriptions-item label="确认帧数">
              {{ (scene.alarmConfig as any)?.confirm_frames ?? 3 }} 帧
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Edit, Share, Clock } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { sceneApi } from '@/api/scene'
import { FACTORY_CONFIG, SCENE_CATEGORY_CONFIG } from '@/constants'
import { AlarmLevel, Factory, SceneCategory, type SceneConfig } from '@/types'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const sceneId = route.params.sceneId as string
const scene = ref<SceneConfig | null>(null)
const loading = ref(false)

const alarmLevelMap = {
  [AlarmLevel.CRITICAL]: { label: '严重', type: 'danger' as const },
  [AlarmLevel.WARNING]: { label: '警告', type: 'warning' as const },
  [AlarmLevel.INFO]: { label: '信息', type: 'info' as const },
}

async function loadScene() {
  loading.value = true
  try {
    const res = await sceneApi.get(sceneId)
    scene.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function toggleStatus() {
  if (!scene.value) return
  const action = scene.value.status === 'active' ? '停用' : '启用'
  await ElMessageBox.confirm(`确定要${action}此场景吗？`, '确认', { type: 'warning' })
  if (scene.value.status === 'active') {
    await sceneApi.disable(sceneId)
  } else {
    await sceneApi.enable(sceneId)
  }
  ElMessage.success(`${action}成功`)
  loadScene()
}

onMounted(loadScene)
</script>

<style scoped lang="scss">
.info-card {
  margin-bottom: 0;
}
.card-title {
  font-weight: 600;
}
</style>
