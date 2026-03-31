<template>
  <div class="model-status-stepper">
    <el-steps :active="activeStep" :process-status="processStatus" finish-status="success" align-center>
      <el-step
        v-for="step in steps"
        :key="step.status"
        :title="step.title"
        :description="step.description"
        :icon="step.icon"
      />
    </el-steps>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

// 模型状态流转：training → pending_review → approved / rejected → production
const props = defineProps<{
  status: string
}>()

const steps = [
  { status: 'training', title: '训练中', description: '模型正在训练', icon: undefined },
  { status: 'pending_review', title: '待审核', description: '等待 MODEL_REVIEWER 审批', icon: undefined },
  { status: 'approved', title: '审批通过', description: 'Sandbox 验证完成', icon: undefined },
  { status: 'production', title: '生产部署', description: '已发布至生产推理服务', icon: undefined },
]

const statusOrder: Record<string, number> = {
  training: 0,
  pending_review: 1,
  approved: 2,
  production: 3,
  rejected: 1, // 拒绝停在审核步骤
}

const activeStep = computed(() => {
  const idx = statusOrder[props.status] ?? 0
  return props.status === 'production' ? steps.length : idx + 1
})

const processStatus = computed(() => {
  if (props.status === 'rejected') return 'error'
  if (props.status === 'production') return 'success'
  return 'process'
})
</script>

<style scoped lang="scss">
.model-status-stepper {
  padding: 16px 0;
}
</style>
