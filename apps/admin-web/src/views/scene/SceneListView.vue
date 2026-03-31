<template>
  <div class="scene-list">
    <PageHeader title="场景列表" description="管理所有工业视觉检测场景">
      <template #actions>
        <PermissionButton
          type="primary"
          :icon="Plus"
          :roles="[UserRole.ADMIN, UserRole.SCENE_EDITOR]"
          hide-if-no-permission
          @click="$router.push('/scenes/new')"
        >
          新建场景
        </PermissionButton>
      </template>
    </PageHeader>

    <!-- 搜索栏 -->
    <SearchBar @search="loadScenes" @reset="resetSearch">
      <el-form-item label="厂部">
        <el-select v-model="query.factory" placeholder="全部厂部" clearable style="width: 140px">
          <el-option v-for="(cfg, key) in FACTORY_CONFIG" :key="key" :label="cfg.label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="类别">
        <el-select v-model="query.category" placeholder="全部类别" clearable style="width: 140px">
          <el-option v-for="(cfg, key) in SCENE_CATEGORY_CONFIG" :key="key" :label="cfg.label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px">
          <el-option label="运行中" value="active" />
          <el-option label="已停用" value="inactive" />
          <el-option label="草稿" value="draft" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="场景名称 / ID" style="width: 200px" clearable />
      </el-form-item>
    </SearchBar>

    <!-- 数据表格 -->
    <DataTable
      :data="scenes"
      :total="total"
      :loading="loading"
      v-model:page="query.page"
      v-model:size="query.size"
      @change="loadScenes"
    >
      <el-table-column label="场景ID" prop="sceneId" width="180" />
      <el-table-column label="场景名称" prop="name" min-width="160">
        <template #default="{ row }">
          <el-button link @click="$router.push(`/scenes/${row.sceneId}`)">{{ row.name }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="厂部" width="100">
        <template #default="{ row }">{{ FACTORY_CONFIG[row.factory as Factory]?.label }}</template>
      </el-table-column>
      <el-table-column label="类别" width="120">
        <template #default="{ row }">{{ SCENE_CATEGORY_CONFIG[row.category as SceneCategory]?.label }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusBadge :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="版本" prop="version" width="70" align="center" />
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/scenes/${row.sceneId}`)">详情</el-button>
          <el-button link size="small" @click="$router.push(`/scenes/${row.sceneId}/edit`)">编辑</el-button>
          <el-button
            link
            size="small"
            :type="row.status === 'active' ? 'warning' : 'success'"
            @click="toggleScene(row)"
          >
            {{ row.status === 'active' ? '停用' : '启用' }}
          </el-button>
          <el-button link size="small" @click="$router.push(`/scenes/${row.sceneId}/workflow`)">编排</el-button>
        </template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import SearchBar from '@/components/common/SearchBar.vue'
import DataTable from '@/components/common/DataTable.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import PermissionButton from '@/components/common/PermissionButton.vue'
import { sceneApi } from '@/api/scene'
import { FACTORY_CONFIG, SCENE_CATEGORY_CONFIG } from '@/constants'
import { UserRole, Factory, SceneCategory, type SceneConfig } from '@/types'
import { formatDateTime } from '@/utils/format'

const scenes = ref<SceneConfig[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  page: 1,
  size: 20,
  factory: '',
  category: '',
  status: '',
  keyword: '',
})

async function loadScenes() {
  loading.value = true
  try {
    const res = await sceneApi.list({
      page: query.page,
      size: query.size,
      factory: query.factory || undefined,
      category: query.category || undefined,
      status: query.status || undefined,
      keyword: query.keyword || undefined,
    })
    scenes.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  query.factory = ''
  query.category = ''
  query.status = ''
  query.keyword = ''
  query.page = 1
  loadScenes()
}

async function toggleScene(scene: SceneConfig) {
  const action = scene.status === 'active' ? '停用' : '启用'
  await ElMessageBox.confirm(`确定要${action}场景「${scene.name}」吗？`, '确认', { type: 'warning' })
  if (scene.status === 'active') {
    await sceneApi.disable(scene.sceneId)
  } else {
    await sceneApi.enable(scene.sceneId)
  }
  ElMessage.success(`${action}成功`)
  loadScenes()
}

onMounted(loadScenes)
</script>
