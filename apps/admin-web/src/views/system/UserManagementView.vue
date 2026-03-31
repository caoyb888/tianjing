<template>
  <div class="user-management">
    <PageHeader title="用户管理" description="管理系统用户及角色分配">
    </PageHeader>

    <SearchBar @search="loadUsers" @reset="resetSearch">
      <el-form-item label="角色">
        <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 160px">
          <el-option v-for="(cfg, key) in ROLE_CONFIG" :key="key" :label="cfg.label" :value="key" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="用户名 / 姓名" style="width: 180px" clearable />
      </el-form-item>
    </SearchBar>

    <DataTable :data="users" :total="total" :loading="loading" v-model:page="query.page" v-model:size="query.size" @change="loadUsers">
      <el-table-column label="用户名" prop="username" width="140" />
      <el-table-column label="显示名称" prop="displayName" width="140" />
      <el-table-column label="邮箱" prop="email" min-width="180" />
      <el-table-column label="角色" min-width="250">
        <template #default="{ row }">
          <el-tag
            v-for="role in row.roles"
            :key="role"
            size="small"
            style="margin: 2px"
            :color="ROLE_CONFIG[role as UserRole]?.color"
            effect="dark"
          >
            {{ ROLE_CONFIG[role as UserRole]?.label }}
          </el-tag>
        </template>
      </el-table-column>
    </DataTable>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SearchBar from '@/components/common/SearchBar.vue'
import DataTable from '@/components/common/DataTable.vue'
import { systemApi } from '@/api/system'
import { ROLE_CONFIG } from '@/constants'
import { UserRole } from '@/types'

const users = ref<unknown[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 20, role: '', keyword: '' })

async function loadUsers() {
  loading.value = true
  try {
    const res = await systemApi.listUsers({ page: query.page, size: query.size, role: query.role || undefined, keyword: query.keyword || undefined })
    users.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

function resetSearch() { query.role = ''; query.keyword = ''; query.page = 1; loadUsers() }
onMounted(loadUsers)
</script>
