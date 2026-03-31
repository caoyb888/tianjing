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
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 16px;
  border-top: 1px solid #f0f0f0;
}
</style>
