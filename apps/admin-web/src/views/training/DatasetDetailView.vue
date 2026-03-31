<template>
  <div class="dataset-detail" v-loading="loading">
    <PageHeader
      :title="dataset?.name || '数据集详情'"
      :description="`数据集编号：${datasetCode}`"
    >
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button type="primary" @click="$router.push('/training/jobs')">提交训练作业</el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" v-if="dataset">
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">数据集信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="编号">{{ dataset.datasetCode }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ dataset.name }}</el-descriptions-item>
            <el-descriptions-item label="厂部">{{ FACTORY_CONFIG[dataset.factory]?.label }}</el-descriptions-item>
            <el-descriptions-item label="图像总数">{{ dataset.imageCount.toLocaleString() }}</el-descriptions-item>
            <el-descriptions-item label="标注总数">{{ dataset.annotationCount.toLocaleString() }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDateTime(dataset.createdAt) }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :md="12">
        <el-card shadow="never">
          <template #header><span class="card-title">版本历史</span></template>
          <el-timeline>
            <el-timeline-item v-for="v in dataset.versions" :key="v" :timestamp="v" placement="top">
              版本 {{ v }}
            </el-timeline-item>
          </el-timeline>
          <EmptyState v-if="!dataset.versions?.length" description="暂无版本记录" :image-size="60" />
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
import { trainingApi } from '@/api/training'
import { FACTORY_CONFIG } from '@/constants'
import { formatDateTime } from '@/utils/format'
import type { TrainingDataset } from '@/types'

const route = useRoute()
const datasetCode = route.params.datasetCode as string
const dataset = ref<TrainingDataset | null>(null)
const loading = ref(false)

async function loadDataset() {
  loading.value = true
  try {
    const res = await trainingApi.getDataset(datasetCode)
    dataset.value = res.data.data
  } finally {
    loading.value = false
  }
}

onMounted(loadDataset)
</script>
<style scoped lang="scss">.card-title { font-weight: 600; }</style>
