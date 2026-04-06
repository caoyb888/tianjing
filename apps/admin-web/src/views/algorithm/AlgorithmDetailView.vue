<template>
  <div class="algorithm-detail" v-loading="loading">
    <PageHeader
      :title="plugin?.pluginId || '插件详情'"
      :description="`算法插件 · 版本 v${plugin?.version}`"
    >
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
      </template>
    </PageHeader>

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
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { algorithmApi } from '@/api/algorithm'
import type { AlgorithmPlugin } from '@/types'

const route = useRoute()
const pluginId = route.params.pluginId as string
const plugin = ref<AlgorithmPlugin | null>(null)
const loading = ref(false)

function dimensionTagType(dim: string | undefined): 'success' | 'warning' | 'danger' | '' {
  if (!dim) return ''
  if (dim.includes('设备')) return 'warning'
  if (dim.includes('质量')) return 'danger'
  if (dim.includes('工艺')) return 'success'
  return ''
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
