<template>
  <div class="page-header">
    <div class="header-left">
      <h2 class="page-title">{{ title }}</h2>
      <p v-if="description" class="page-description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="header-actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: var(--tj-space-md);
  margin-bottom: var(--tj-space-md);
  border-bottom: 2px solid var(--tj-border-light);
}

.page-title {
  font-size: var(--tj-font-lg);
  font-weight: 700;
  color: var(--tj-text-primary);
  margin-bottom: 4px;
  letter-spacing: -0.3px;
}

.page-description {
  font-size: var(--tj-font-sm);
  color: var(--tj-text-secondary);
  margin-top: 4px;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
