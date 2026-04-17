<template>
  <div class="scene-form">
    <PageHeader :title="isEdit ? '编辑场景' : '新建场景'" :description="isEdit ? `场景 ID：${sceneId}` : '创建新的工业视觉检测场景'">
      <template #actions>
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" v-loading="loading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" class="scene-form-inner">
        <el-row :gutter="24">
          <el-col :md="12">
            <el-form-item label="场景名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入场景名称" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="所属厂部" prop="factory">
              <el-select v-model="form.factory" placeholder="请选择厂部" class="full-width">
                <el-option v-for="(cfg, key) in FACTORY_CONFIG" :key="key" :label="cfg.label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="场景类别" prop="category">
              <el-select v-model="form.category" placeholder="请选择类别" class="full-width">
                <el-option v-for="(cfg, key) in SCENE_CATEGORY_CONFIG" :key="key" :label="cfg.label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="算法插件" prop="algorithmConfig.plugin_id">
              <el-select
                v-model="form.algorithmConfig.plugin_id"
                filterable
                placeholder="请选择算法插件"
                class="full-width"
                :loading="pluginsLoading"
              >
                <el-option
                  v-for="plugin in pluginOptions"
                  :key="plugin.pluginId"
                  :label="`${plugin.pluginId}（${plugin.name}）`"
                  :value="plugin.pluginId"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="置信度阈值" prop="algorithmConfig.conf_threshold">
              <el-input-number v-model="form.algorithmConfig.conf_threshold" :min="0" :max="1" :step="0.05" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="IOU 阈值" prop="algorithmConfig.iou_threshold">
              <el-input-number v-model="form.algorithmConfig.iou_threshold" :min="0" :max="1" :step="0.05" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="告警级别" prop="alarmConfig.level">
              <el-select v-model="form.alarmConfig.level" class="full-width">
                <el-option label="严重 (CRITICAL)" value="CRITICAL" />
                <el-option label="警告 (WARNING)" value="WARNING" />
                <el-option label="信息 (INFO)" value="INFO" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="确认帧数" prop="alarmConfig.confirm_frames">
              <el-input-number v-model="form.alarmConfig.confirm_frames" :min="1" :max="10" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :md="24">
            <el-form-item label="场景描述">
              <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选，描述场景用途和检测目标" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { sceneApi } from '@/api/scene'
import { algorithmApi } from '@/api/algorithm'
import { FACTORY_CONFIG, SCENE_CATEGORY_CONFIG } from '@/constants'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)

const sceneId = computed(() => route.params.sceneId as string)
const isEdit = computed(() => !!sceneId.value)

const pluginOptions = ref<Array<{ pluginId: string; name: string }>>([])
const pluginsLoading = ref(false)
const currentVersion = ref<number | null>(null)  // 乐观锁版本号，PUT 时必须携带

async function loadPlugins() {
  pluginsLoading.value = true
  try {
    const res = await algorithmApi.list({ page: 1, size: 200 })
    pluginOptions.value = res.data.data.items ?? []
  } finally {
    pluginsLoading.value = false
  }
}

const form = reactive({
  name: '',
  factory: '',
  category: '',
  description: '',
  algorithmConfig: {
    plugin_id: '',
    conf_threshold: 0.85,
    iou_threshold: 0.45,
  },
  alarmConfig: {
    level: 'WARNING',
    confirm_frames: 3,
  },
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入场景名称', trigger: 'blur' }],
  factory: [{ required: true, message: '请选择厂部', trigger: 'change' }],
  category: [{ required: true, message: '请选择场景类别', trigger: 'change' }],
}

// 前端枚举值 → DB 值（与后端 toDbFactory/toDbCategory 保持一致）
const FACTORY_TO_DB: Record<string, string> = {
  pellet: 'PELLET', sintering: 'SINTER', steel: 'STEEL', section: 'SECTION', strip: 'STRIP',
}
const CATEGORY_TO_DB: Record<string, string> = {
  quality: 'QUALITY_INSPECT', equipment: 'EQUIPMENT_MONITOR', process: 'PROCESS_PARAM',
}

async function loadScene() {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await sceneApi.get(sceneId.value)
    const data = res.data.data
    form.name = data.name
    form.factory = data.factory
    form.category = data.category
    form.description = data.description || ''
    Object.assign(form.algorithmConfig, data.algorithmConfig)
    Object.assign(form.alarmConfig, data.alarmConfig)
    currentVersion.value = data.version  // 保存乐观锁版本号
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value) {
      // 映射为后端 SceneConfigRequest 期望的字段名
      await sceneApi.update(sceneId.value, {
        scene_name: form.name,
        factory_code: FACTORY_TO_DB[form.factory] ?? form.factory.toUpperCase(),
        category: CATEGORY_TO_DB[form.category] ?? form.category.toUpperCase(),
        algo_params_json: form.algorithmConfig,
        alarm_config_json: form.alarmConfig,
        version: currentVersion.value,
      })
      ElMessage.success('更新成功')
    } else {
      await sceneApi.create({
        scene_name: form.name,
        factory_code: FACTORY_TO_DB[form.factory] ?? form.factory.toUpperCase(),
        category: CATEGORY_TO_DB[form.category] ?? form.category.toUpperCase(),
        algo_params_json: form.algorithmConfig,
        alarm_config_json: form.alarmConfig,
      })
      ElMessage.success('创建成功')
    }
    router.push('/scenes')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadScene()
  loadPlugins()
})
</script>
