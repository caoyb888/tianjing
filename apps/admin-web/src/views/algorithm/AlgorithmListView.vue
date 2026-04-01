<template>
  <div class="algorithm-list">
    <PageHeader title="算法插件库" description="查看和管理所有注册的算法插件">
      <template #actions>
        <PermissionButton type="primary" :icon="Plus" :roles="[UserRole.ADMIN]" hide-if-no-permission @click="showRegister = true">
          注册插件
        </PermissionButton>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <el-col
        v-for="plugin in plugins"
        :key="plugin.pluginId"
        :lg="8" :md="12" :sm="24"
      >
        <el-card shadow="hover" class="plugin-card" @click="$router.push(`/algorithms/${plugin.pluginId}`)">
          <div class="plugin-header">
            <el-tag size="small" type="info">{{ plugin.type }}</el-tag>
            <span class="plugin-version">v{{ plugin.version }}</span>
          </div>
          <h3 class="plugin-name">{{ plugin.pluginId }}</h3>
          <div class="plugin-metrics">
            <div class="metric">
              <span class="label">mAP@50</span>
              <span class="value">{{ plugin.accuracyMetrics.map50 ? (plugin.accuracyMetrics.map50 * 100).toFixed(1) + '%' : '-' }}</span>
            </div>
            <div class="metric">
              <span class="label">推理耗时(GPU)</span>
              <span class="value">{{ plugin.accuracyMetrics.inferenceMs }}ms</span>
            </div>
          </div>
          <div class="plugin-scenes">
            <el-tag v-for="scene in plugin.supportedScenes.slice(0, 3)" :key="scene" size="small" effect="plain" style="margin: 2px">
              {{ scene }}
            </el-tag>
            <span v-if="plugin.supportedScenes.length > 3" style="font-size: 12px; color: #909399">+{{ plugin.supportedScenes.length - 3 }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <EmptyState v-if="!loading && plugins.length === 0" description="暂无注册的算法插件" />

    <!-- 注册插件对话框 -->
    <el-dialog v-model="showRegister" title="注册算法插件" width="560px" @close="resetForm">
      <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-width="110px">
        <el-form-item label="插件 ID" prop="pluginId">
          <el-input v-model="registerForm.pluginId" placeholder="例：ATOM-DETECT-YOLO-V1" />
        </el-form-item>
        <el-form-item label="插件名称" prop="name">
          <el-input v-model="registerForm.name" placeholder="例：通用目标检测引擎" />
        </el-form-item>
        <el-form-item label="版本号" prop="version">
          <el-input v-model="registerForm.version" placeholder="例：1.0.0" />
        </el-form-item>
        <el-form-item label="插件类型" prop="type">
          <el-select v-model="registerForm.type" placeholder="请选择插件类型" style="width: 100%">
            <el-option label="目标检测 (detection)" value="detection" />
            <el-option label="语义分割 (segmentation)" value="segmentation" />
            <el-option label="图像分类 (classification)" value="classification" />
            <el-option label="尺寸测量 (measurement)" value="measurement" />
            <el-option label="图像增强 (enhancement)" value="enhancement" />
          </el-select>
        </el-form-item>
        <el-form-item label="支持场景">
          <el-select
            v-model="registerForm.supportedScenes"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入场景名称后回车"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="mAP@50">
          <el-input-number v-model="registerForm.map50" :min="0" :max="1" :step="0.01" :precision="3" placeholder="例：0.910" style="width: 100%" />
        </el-form-item>
        <el-form-item label="GPU推理耗时(ms)">
          <el-input-number v-model="registerForm.inferenceMs" :min="0" :precision="1" placeholder="例：18" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRegister = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="submitRegister">确认注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import PermissionButton from '@/components/common/PermissionButton.vue'
import { algorithmApi } from '@/api/algorithm'
import { UserRole, type AlgorithmPlugin } from '@/types'

const plugins = ref<AlgorithmPlugin[]>([])
const loading = ref(false)
const showRegister = ref(false)
const registering = ref(false)
const registerFormRef = ref<FormInstance>()

const registerForm = reactive({
  pluginId: '',
  name: '',
  version: '',
  type: '' as AlgorithmPlugin['type'] | '',
  supportedScenes: [] as string[],
  map50: undefined as number | undefined,
  inferenceMs: undefined as number | undefined,
})

const registerRules: FormRules = {
  pluginId: [{ required: true, message: '请输入插件 ID', trigger: 'blur' }],
  name:     [{ required: true, message: '请输入插件名称', trigger: 'blur' }],
  version:  [{ required: true, message: '请输入版本号', trigger: 'blur' }],
  type:     [{ required: true, message: '请选择插件类型', trigger: 'change' }],
}

function resetForm() {
  registerFormRef.value?.resetFields()
  registerForm.supportedScenes = []
  registerForm.map50 = undefined
  registerForm.inferenceMs = undefined
}

async function submitRegister() {
  await registerFormRef.value?.validate()
  registering.value = true
  try {
    await algorithmApi.register({
      pluginId: registerForm.pluginId,
      name: registerForm.name,
      version: registerForm.version,
      type: registerForm.type,
      supportedScenes: registerForm.supportedScenes,
      accuracyMetrics: {
        map50: registerForm.map50,
        inferenceMs: registerForm.inferenceMs ?? 0,
      },
    })
    ElMessage.success('插件注册成功')
    showRegister.value = false
    loadPlugins()
  } catch {
    ElMessage.error('注册失败，请稍后重试')
  } finally {
    registering.value = false
  }
}

async function loadPlugins() {
  loading.value = true
  try {
    const res = await algorithmApi.list({ size: 100 })
    plugins.value = res.data.data.items
  } finally {
    loading.value = false
  }
}

onMounted(loadPlugins)
</script>

<style scoped lang="scss">
.plugin-card {
  cursor: pointer;
  margin-bottom: 16px;
  transition: box-shadow 0.2s;
  &:hover { box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12); }
}
.plugin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.plugin-version { font-size: 12px; color: #909399; }
.plugin-name { font-size: 14px; font-weight: 600; margin-bottom: 12px; color: #303133; }
.plugin-metrics {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  .metric { display: flex; flex-direction: column; gap: 2px; }
  .label { font-size: 11px; color: #909399; }
  .value { font-size: 13px; font-weight: 600; color: #1890ff; }
}
</style>
