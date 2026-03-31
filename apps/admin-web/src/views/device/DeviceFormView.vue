<template>
  <div class="device-form">
    <PageHeader title="注册设备" description="注册工业摄像头或边缘推理设备">
      <template #actions>
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="24">
          <el-col :md="12">
            <el-form-item label="设备名称" prop="deviceName">
              <el-input v-model="form.deviceName" placeholder="请输入设备名称" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="关联场景" prop="sceneId">
              <el-input v-model="form.sceneId" placeholder="场景 ID" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="所属厂部" prop="factory">
              <el-select v-model="form.factory" class="full-width">
                <el-option v-for="(cfg, key) in FACTORY_CONFIG" :key="key" :label="cfg.label" :value="key" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="IP 地址" prop="ipAddress">
              <el-input v-model="form.ipAddress" placeholder="如：192.168.1.100" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="视频流地址">
              <el-input v-model="form.streamUrl" placeholder="rtsp://... (Sprint 5 后填写)" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { deviceApi } from '@/api/device'
import { FACTORY_CONFIG } from '@/constants'

const router = useRouter()
const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive({ deviceName: '', sceneId: '', factory: '', ipAddress: '', streamUrl: '' })
const rules: FormRules = {
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  factory: [{ required: true, message: '请选择厂部', trigger: 'change' }],
  ipAddress: [{ required: true, message: '请输入 IP 地址', trigger: 'blur' }],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await deviceApi.register(form)
    ElMessage.success('设备注册成功')
    router.push('/devices')
  } finally {
    saving.value = false
  }
}
</script>
