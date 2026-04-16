<template>
  <div class="user-management">
    <PageHeader title="用户管理" description="管理系统用户及角色分配">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon> 新建用户
        </el-button>
      </template>
    </PageHeader>

    <SearchBar @search="handleSearch" @reset="resetSearch">
      <el-form-item label="角色">
        <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 160px">
          <el-option v-for="r in roleOptions" :key="r.role_code" :label="r.role_name" :value="r.role_code" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="用户名 / 姓名" style="width: 180px" clearable />
      </el-form-item>
    </SearchBar>

    <DataTable
      :data="users"
      :total="total"
      :loading="loading"
      v-model:page="query.page"
      v-model:size="query.size"
      @change="loadUsers"
    >
      <el-table-column label="用户名" prop="username" width="150" />
      <el-table-column label="显示名称" prop="displayName" width="140" />
      <el-table-column label="部门" prop="deptCode" width="100" />
      <el-table-column label="邮箱" prop="email" min-width="160" show-overflow-tooltip />
      <el-table-column label="角色" min-width="240">
        <template #default="{ row }">
          <el-tag
            v-for="role in (row.roles || [])"
            :key="role"
            size="small"
            type="primary"
            style="margin: 2px"
          >
            {{ ROLE_CONFIG[role as UserRole]?.label ?? role }}
          </el-tag>
          <span v-if="!row.roles || row.roles.length === 0" style="color:#aaa;font-size:13px">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'" size="small">
            {{ row.status === 'ACTIVE' ? '正常' : row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后登录" prop="lastLoginAt" width="170" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.lastLoginAt ? row.lastLoginAt.replace('T', ' ').substring(0, 19) : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditRolesDialog(row)">编辑角色</el-button>
          <el-popconfirm
            title="确认删除该用户？此操作不可恢复。"
            confirm-button-text="删除"
            confirm-button-type="danger"
            cancel-button-text="取消"
            @confirm="deleteUser(row)"
          >
            <template #reference>
              <el-button size="small" type="danger" plain>删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </DataTable>

    <!-- 新建用户对话框 -->
    <el-dialog v-model="createDialogVisible" title="新建用户" width="480px" :close-on-click-modal="false">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="字母、数字、下划线" />
        </el-form-item>
        <el-form-item label="显示名称" prop="display_name">
          <el-input v-model="createForm.display_name" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="createForm.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="角色" prop="roles">
          <el-select v-model="createForm.roles" multiple placeholder="选择角色" style="width:100%">
            <el-option v-for="r in roleOptions" :key="r.role_code" :label="r.role_name" :value="r.role_code" />
          </el-select>
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="createForm.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="createForm.dept_code" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑角色对话框 -->
    <el-dialog v-model="editRolesDialogVisible" title="编辑用户角色" width="400px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span>{{ editTarget?.displayName }} ({{ editTarget?.username }})</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editRoles" multiple placeholder="选择角色" style="width:100%">
            <el-option v-for="r in roleOptions" :key="r.role_code" :label="r.role_name" :value="r.role_code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editRolesDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEditRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import SearchBar from '@/components/common/SearchBar.vue'
import DataTable from '@/components/common/DataTable.vue'
import { systemApi } from '@/api/system'
import { ROLE_CONFIG } from '@/constants'
import { UserRole } from '@/types'

// ── 列表 ──────────────────────────────────────────
const users = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 20, role: '', keyword: '' })

const roleOptions = ref<{ role_code: string; role_name: string; description: string }[]>([])

async function loadRoles() {
  try {
    const res = await systemApi.listRoles()
    roleOptions.value = res.data.data
  } catch {}
}

async function loadUsers() {
  loading.value = true
  try {
    const res = await systemApi.listUsers({
      page: query.page,
      size: query.size,
      role: query.role || undefined,
      keyword: query.keyword || undefined,
    })
    users.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() { query.page = 1; loadUsers() }
function resetSearch() { query.role = ''; query.keyword = ''; query.page = 1; loadUsers() }

// ── 新建用户 ─────────────────────────────────────
const createDialogVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref()
const createForm = reactive({
  username: '', display_name: '', password: '',
  roles: [] as string[], email: '', dept_code: '',
})
const createRules = {
  username:     [{ required: true, message: '请输入用户名' }],
  display_name: [{ required: true, message: '请输入显示名称' }],
  password:     [{ required: true, min: 6, message: '密码至少 6 位' }],
}

function openCreateDialog() {
  Object.assign(createForm, { username: '', display_name: '', password: '', roles: [], email: '', dept_code: '' })
  createDialogVisible.value = true
}

async function submitCreate() {
  await createFormRef.value?.validate()
  submitting.value = true
  try {
    await systemApi.createUser(createForm)
    ElMessage.success('用户创建成功')
    createDialogVisible.value = false
    loadUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? '创建失败')
  } finally {
    submitting.value = false
  }
}

// ── 编辑角色 ─────────────────────────────────────
const editRolesDialogVisible = ref(false)
const editTarget = ref<any>(null)
const editRoles = ref<string[]>([])

function openEditRolesDialog(row: any) {
  editTarget.value = row
  editRoles.value = [...(row.roles ?? [])]
  editRolesDialogVisible.value = true
}

async function submitEditRoles() {
  submitting.value = true
  try {
    await systemApi.updateUserRoles(editTarget.value.userId, { roles: editRoles.value })
    ElMessage.success('角色更新成功')
    editRolesDialogVisible.value = false
    loadUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? '更新失败')
  } finally {
    submitting.value = false
  }
}

// ── 删除用户 ─────────────────────────────────────
async function deleteUser(row: any) {
  try {
    await systemApi.deleteUser(row.userId)
    ElMessage.success('用户已删除')
    loadUsers()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message ?? '删除失败')
  }
}

onMounted(() => { loadRoles(); loadUsers() })
</script>
