<template>
  <div class="workflow-node" :style="{ borderColor: color }">
    <Handle type="target" :position="Position.Left" />
    <div class="node-content">
      <el-icon class="node-icon" :style="{ color }">
        <component :is="icon" />
      </el-icon>
      <div class="node-info">
        <div class="node-type">{{ label }}</div>
        <div class="node-name">{{ data.label }}</div>
      </div>
    </div>
    <Handle type="source" :position="Position.Right" />
  </div>
</template>

<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

defineProps<{
  data: Record<string, unknown>
  color: string
  label: string
  icon: string
}>()
</script>

<style scoped lang="scss">
.workflow-node {
  position: relative;
  background: #fff;
  border: 2px solid;
  border-radius: 8px;
  padding: 10px 14px;
  min-width: 130px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  cursor: pointer;

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
  }
}

.node-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.node-type {
  font-size: 10px;
  color: #909399;
  line-height: 1;
  margin-bottom: 3px;
}

.node-name {
  font-size: 12px;
  font-weight: 600;
  color: #303133;
}
</style>
