<template>
  <div class="algorithm-detail" v-loading="loading">
    <PageHeader
      :title="plugin?.name || plugin?.pluginId || '插件详情'"
      :description="`${plugin?.pluginId} · 版本 v${plugin?.version}`"
    >
      <template #actions>
        <el-button
          type="primary"
          :loading="checking"
          :icon="checking ? undefined : Monitor"
          @click="runHealthCheck"
        >{{ checking ? '检测中…' : '检测可用性' }}</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

    <!-- 检测结果横幅 -->
    <el-alert
      v-if="healthResult"
      :type="healthResult.available ? 'success' : 'error'"
      :closable="true"
      show-icon
      style="margin-bottom: 16px"
      @close="healthResult = null"
    >
      <template #title>
        <span v-if="healthResult.available">
          ✅ 算法可用 — 推理代理响应 {{ healthResult.responseMs }}ms
          <el-tag v-if="healthResult.backend" size="small" type="info" style="margin-left:8px">{{ healthResult.backend }}</el-tag>
        </span>
        <span v-else>❌ 算法不可用 — {{ healthResult.message }}</span>
      </template>
      <div v-if="healthResult.available" style="font-size:13px; color:#606266; margin-top:4px">
        {{ healthResult.message }}
      </div>
      <div style="font-size:11px; color:#909399; margin-top:4px">
        检测时间：{{ formatDateTime(healthResult.checkedAt) }}
      </div>
    </el-alert>

    <el-row :gutter="16" v-if="plugin">
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">基本信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="插件ID">{{ plugin.pluginId }}</el-descriptions-item>
            <el-descriptions-item label="版本号">v{{ plugin.version }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ plugin.type }}</el-descriptions-item>
            <el-descriptions-item label="业务维度">
              <el-tag v-if="plugin.businessDimension" :type="dimensionTagType(plugin.businessDimension)" size="small">
                {{ plugin.businessDimension }}
              </el-tag>
              <span v-else style="color:#c0c4cc">—</span>
            </el-descriptions-item>
            <el-descriptions-item label="算法描述">
              <span style="white-space: pre-wrap; line-height: 1.6">{{ plugin.description || '—' }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">精度指标</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="mAP@50">
              {{ plugin.accuracyMetrics.map50 ? (plugin.accuracyMetrics.map50 * 100).toFixed(1) + '%' : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="mAP@50:95">
              {{ plugin.accuracyMetrics.map50_95 ? (plugin.accuracyMetrics.map50_95 * 100).toFixed(1) + '%' : '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="GPU 推理耗时">
              {{ plugin.accuracyMetrics.inferenceMs }}ms
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="24" style="margin-top: 16px">
        <el-card shadow="never">
          <template #header><span class="card-title">支持场景</span></template>
          <div style="padding: 8px 0">
            <el-tag
              v-for="scene in plugin.supportedScenes"
              :key="scene"
              style="margin: 4px"
              effect="plain"
            >
              {{ scene }}
            </el-tag>
            <EmptyState v-if="!plugin.supportedScenes?.length" description="暂无支持场景信息" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Monitor } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { algorithmApi } from '@/api/algorithm'
import { formatDateTime } from '@/utils/format'
import type { AlgorithmPlugin } from '@/types'

const route = useRoute()
const pluginId = route.params.pluginId as string
const plugin  = ref<AlgorithmPlugin | null>(null)
const loading = ref(false)
const checking = ref(false)
const healthResult = ref<{
  available: boolean
  responseMs: number
  message: string
  backend: string | null
  checkedAt: string
} | null>(null)

function dimensionTagType(dim: string | undefined): 'success' | 'warning' | 'danger' | '' {
  if (!dim) return ''
  if (dim.includes('设备')) return 'warning'
  if (dim.includes('质量')) return 'danger'
  if (dim.includes('工艺')) return 'success'
  return ''
}

async function runHealthCheck() {
  checking.value = true
  healthResult.value = null
  try {
    const res = await algorithmApi.healthCheck(pluginId)
    healthResult.value = res.data.data
    if (res.data.data.available) {
      ElMessage.success('算法可用，推理代理响应正常')
    } else {
      ElMessage.warning('算法不可用，请查看检测结果')
    }
  } catch {
    ElMessage.error('检测请求失败，请稍后重试')
  } finally {
    checking.value = false
  }
}

async function loadPlugin() {
  loading.value = true
  try {
    const res = await algorithmApi.get(pluginId)
    plugin.value = res.data.data
  } finally {
    loading.value = false
  }
}

onMounted(loadPlugin)
</script>
<style scoped lang="scss">.card-title { font-weight: 600; }</style>
