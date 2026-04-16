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
              <el-input v-model="form.algorithmConfig.plugin_id" placeholder="如: ATOM-DETECT-YOLO-V1" />
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
import { FACTORY_CONFIG, SCENE_CATEGORY_CONFIG } from '@/constants'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)

const sceneId = computed(() => route.params.sceneId as string)
const isEdit = computed(() => !!sceneId.value)

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
      await sceneApi.update(sceneId.value, form)
      ElMessage.success('更新成功')
    } else {
      await sceneApi.create(form)
      ElMessage.success('创建成功')
    }
    router.push('/scenes')
  } finally {
    saving.value = false
  }
}

onMounted(loadScene)
</script>
