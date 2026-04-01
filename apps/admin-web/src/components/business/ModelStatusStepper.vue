<template>
  <div class="model-status-stepper">
    <el-steps :active="activeStep" :process-status="processStatus" finish-status="success" align-center>
      <el-step
        v-for="step in steps"
        :key="step.status"
        :title="step.title"
        :description="step.description"
      />
    </el-steps>
    <div v-if="isDeprecated" class="deprecated-tip">
      <el-tag type="info" size="small">已废弃</el-tag>
      <span>该版本已被新版本替代，自动降级为 DEPRECATED</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 模型版本状态步骤条
 * 状态机（与后端 ModelVersion.status 严格对齐）：
 *   STAGING → SANDBOX_VALIDATING → REVIEWING → PRODUCTION
 *   审核拒绝：REVIEWING → STAGING（回退）
 *   蓝绿切换：PRODUCTION → DEPRECATED（旧版本降级）
 */
const props = defineProps<{
  status: string
}>()

const steps = [
  { status: 'STAGING',            title: '已注册',      description: '模型已从 MLflow 注册，等待验证' },
  { status: 'SANDBOX_VALIDATING', title: 'Sandbox 验证', description: '自动精度验证 48h 门禁' },
  { status: 'REVIEWING',          title: '待审核',      description: '等待 MODEL_REVIEWER 四眼审批' },
  { status: 'PRODUCTION',         title: '已上线',      description: '已发布至生产推理服务' },
]

// DEPRECATED 不进入步骤条，以独立提示展示
const isDeprecated = computed(() => props.status === 'DEPRECATED')

/**
 * statusOrder：各状态对应的步骤索引（0-based）
 * 审核拒绝时 status 回退至 STAGING（后端行为），步骤条展示在第 0 步并标 error
 */
const statusOrder: Record<string, number> = {
  STAGING:            0,
  SANDBOX_VALIDATING: 1,
  REVIEWING:          2,
  PRODUCTION:         3,
  DEPRECATED:         3, // 废弃版本曾到达 PRODUCTION，步骤显示完成
}

const activeStep = computed(() => {
  if (isDeprecated.value) return steps.length // 所有步骤显示 finish
  const idx = statusOrder[props.status] ?? 0
  return props.status === 'PRODUCTION' ? steps.length : idx + 1
})

const processStatus = computed(() => {
  if (props.status === 'PRODUCTION' || isDeprecated.value) return 'success'
  return 'process'
})
</script>

<style scoped lang="scss">
.model-status-stepper {
  padding: 16px 0;
}
.deprecated-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
