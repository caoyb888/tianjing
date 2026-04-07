<template>
  <div class="data-table">
    <el-table
      v-bind="$attrs"
      :data="data"
      :loading="loading"
      v-loading="loading"
      stripe
      border
      style="width: 100%"
    >
      <slot />
    </el-table>
    <div v-if="total > 0" class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    data: unknown[]
    total?: number
    loading?: boolean
    page?: number
    size?: number
  }>(),
  { total: 0, loading: false, page: 1, size: 20 }
)

const emit = defineEmits<{
  'update:page': [page: number]
  'update:size': [size: number]
  change: []
}>()

const currentPage = computed({
  get: () => props.page,
  set: (val) => emit('update:page', val),
})

const pageSize = computed({
  get: () => props.size,
  set: (val) => emit('update:size', val),
})

function handleSizeChange() {
  currentPage.value = 1
  emit('change')
}

function handleCurrentChange() {
  emit('change')
}
</script>

<style scoped lang="scss">
.data-table {
  background: var(--tj-bg-card);
  border-radius: var(--tj-radius-sm);
  box-shadow: var(--tj-shadow-card);
  overflow: hidden;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: var(--tj-space-md);
  border-top: 1px solid var(--tj-border-light);
}

// 表格行高收紧（工业 UI 信息密度优化）
:deep(.el-table__row td) {
  padding: 10px 0;
}

// 表头加强
:deep(.el-table__header th) {
  background: var(--tj-primary-light) !important;
  color: var(--tj-text-primary);
  font-weight: 600;
  font-size: var(--tj-font-sm);
}

// 斑马纹
:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: #FAFBFF;
}

// 行悬浮
:deep(.el-table__body tr:hover > td) {
  background: var(--tj-primary-light) !important;
}
</style>
