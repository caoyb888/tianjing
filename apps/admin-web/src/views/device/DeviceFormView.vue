<template>
  <div class="device-form">
    <PageHeader
      :title="isEdit ? '编辑设备' : '注册设备'"
      :description="isEdit ? `设备编码：${deviceCode}` : '注册工业摄像头或边缘推理设备'"
    >
      <template #actions>
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSubmit">保存</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" v-loading="loading">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="24">
          <el-col :md="12">
            <el-form-item label="设备名称" prop="deviceName">
              <el-input v-model="form.deviceName" placeholder="请输入设备名称" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="关联场景" prop="sceneId">
              <el-input v-model="form.sceneId" placeholder="场景 ID，如 SCENE-SINTER-001" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="IP 地址" prop="ipAddress">
              <el-input v-model="form.ipAddress" placeholder="如：192.168.1.100" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="MAC 地址">
              <el-input v-model="form.macAddress" placeholder="如：AA:BB:CC:DD:EE:FF" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="厂商">
              <el-input v-model="form.vendor" placeholder="如：HIKVISION / DAHUA" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="固件版本">
              <el-input v-model="form.firmwareVersion" placeholder="如：V5.7.2" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="分辨率宽">
              <el-input-number v-model="form.resolutionWidth" :min="320" :max="7680" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="分辨率高">
              <el-input-number v-model="form.resolutionHeight" :min="240" :max="4320" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="帧率 (fps)">
              <el-input-number v-model="form.fps" :min="1" :max="120" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :md="12">
            <el-form-item label="视频流地址">
              <el-input v-model="form.streamUrl" placeholder="rtsp://... (Sprint 5 后填写)" />
            </el-form-item>
          </el-col>
          <el-col :md="24">
            <el-form-item label="安装位置">
              <el-input v-model="form.locationDesc" placeholder="如：烧结机1#机头左侧4m处" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { deviceApi } from '@/api/device'

const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const saving = ref(false)
const loading = ref(false)

const deviceCode = route.params.deviceCode as string | undefined
const isEdit = computed(() => !!deviceCode)

const form = reactive({
  deviceName: '',
  sceneId: '',
  ipAddress: '',
  macAddress: '',
  vendor: '',
  firmwareVersion: '',
  resolutionWidth: 1920,
  resolutionHeight: 1080,
  fps: 25,
  streamUrl: '',
  locationDesc: '',
})

const rules: FormRules = {
  deviceName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  ipAddress:  [{ required: true, message: '请输入 IP 地址', trigger: 'blur' }],
}

onMounted(async () => {
  if (!isEdit.value) return
  loading.value = true
  try {
    const res = await deviceApi.get(deviceCode!)
    const d = res.data.data
    form.deviceName      = d.deviceName      ?? ''
    form.sceneId         = d.sceneId         ?? ''
    form.ipAddress       = d.ipAddress       ?? ''
    form.macAddress      = d.macAddress      ?? ''
    form.vendor          = d.vendor          ?? ''
    form.firmwareVersion = d.firmwareVersion ?? ''
    form.resolutionWidth  = d.resolutionWidth  ?? 1920
    form.resolutionHeight = d.resolutionHeight ?? 1080
    form.fps             = d.fps             ?? 25
    form.locationDesc    = d.locationDesc    ?? ''
  } finally {
    loading.value = false
  }
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      device_name:       form.deviceName,
      scene_id:          form.sceneId,
      ip_address:        form.ipAddress,
      mac_address:       form.macAddress,
      vendor:            form.vendor,
      firmware_version:  form.firmwareVersion,
      resolution_width:  form.resolutionWidth,
      resolution_height: form.resolutionHeight,
      fps:               form.fps,
      location_desc:     form.locationDesc,
      rtsp_url:          form.streamUrl || undefined,
    }
    if (isEdit.value) {
      await deviceApi.update(deviceCode!, payload)
      ElMessage.success('设备信息已更新')
      router.push(`/devices/${deviceCode}`)
    } else {
      await deviceApi.register({ ...payload, device_code: '' })
      ElMessage.success('设备注册成功')
      router.push('/devices')
    }
  } finally {
    saving.value = false
  }
}
</script>
