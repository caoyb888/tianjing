<template>
  <div class="dataset-list">
    <PageHeader title="训练数据集" description="管理各厂部的标注训练数据集" />

    <SearchBar @search="loadDatasets" @reset="resetSearch">
      <el-form-item label="厂部">
        <el-select v-model="query.factory" placeholder="全部厂部" clearable style="width: 140px">
          <el-option v-for="(cfg, key) in FACTORY_CONFIG" :key="key" :label="cfg.label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="数据集名称" style="width: 200px" clearable />
      </el-form-item>
    </SearchBar>

    <DataTable
      :data="datasets"
      :total="total"
      :loading="loading"
      v-model:page="query.page"
      v-model:size="query.size"
      @change="loadDatasets"
    >
      <el-table-column label="数据集编号" prop="datasetCode" width="180" />
      <el-table-column label="名称" prop="name" min-width="160">
        <template #default="{ row }">
          <el-button link @click="$router.push(`/training/datasets/${row.datasetCode}`)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="厂部" width="100">
        <template #default="{ row }">{{ FACTORY_CONFIG[row.factory]?.label }}</template>
      </el-table-column>
      <el-table-column label="图像数" prop="imageCount" width="100" align="right" />
      <el-table-column label="标注数" prop="annotationCount" width="100" align="right" />
      <el-table-column label="版本数" width="90" align="center">
        <template #default="{ row }">{{ row.versions?.length || 0 }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/training/datasets/${row.datasetCode}`)">详情</el-button>
        </template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SearchBar from '@/components/common/SearchBar.vue'
import DataTable from '@/components/common/DataTable.vue'
import { trainingApi } from '@/api/training'
import { FACTORY_CONFIG } from '@/constants'
import { formatDateTime } from '@/utils/format'
import type { TrainingDataset } from '@/types'

const datasets = ref<TrainingDataset[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 20, factory: '', keyword: '' })

async function loadDatasets() {
  loading.value = true
  try {
    const res = await trainingApi.listDatasets({
      page: query.page,
      size: query.size,
      factory: query.factory || undefined,
      keyword: query.keyword || undefined,
    })
    datasets.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  query.factory = ''
  query.keyword = ''
  query.page = 1
  loadDatasets()
}

onMounted(loadDatasets)
</script>
