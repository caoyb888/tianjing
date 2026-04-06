<template>
  <div class="simulation-list">
    <PageHeader title="仿真任务" description="使用录制视频文件仿真推理管道，验证算法效果">
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建仿真任务</el-button>
      </template>
    </PageHeader>

    <DataTable :data="tasks" :total="total" :loading="loading" v-model:page="page" v-model:size="size" @change="loadTasks">
      <el-table-column label="任务ID" prop="taskId" width="200" />
      <el-table-column label="场景" prop="sceneId" width="180" />
      <el-table-column label="视频数" width="90">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.videos?.length ?? 1 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><StatusBadge :status="row.status" /></template>
      </el-table-column>
      <el-table-column label="进度" min-width="140">
        <template #default="{ row }">
          <el-progress v-if="row.status === 'RUNNING'" :percentage="row.progress ?? 0" :stroke-width="8" :striped="true" striped-flow />
          <span v-else-if="row.status === 'COMPLETED'" style="color: #67c23a">100%</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link size="small" @click="$router.push(`/simulations/${row.taskId}`)">详情</el-button>
          <el-button v-if="row.status === 'RUNNING' || row.status === 'PENDING'" link size="small" type="warning" @click="cancelTask(row.taskId)">取消</el-button>
        </template>
      </el-table-column>
    </DataTable>

    <!-- 新建任务对话框 -->
    <el-dialog v-model="showCreate" title="新建仿真任务" width="620px" :close-on-click-modal="false">
      <el-form :model="createForm" label-width="120px">
        <el-form-item label="目标场景" required>
          <el-select
            v-model="createForm.sceneId"
            placeholder="请选择场景"
            filterable
            :loading="scenesLoading"
            style="width: 100%"
          >
            <el-option
              v-for="s in sceneOptions"
              :key="s.sceneId"
              :value="s.sceneId"
              :label="`${s.sceneId}${s.sceneName && s.sceneName !== s.sceneId ? '  ' + s.sceneName : ''}`"
            >
              <div style="display:flex; justify-content:space-between; align-items:center">
                <span>{{ s.sceneId }}</span>
                <span style="font-size:12px; color:#909399; margin-left:12px">
                  {{ [s.factory, s.category].filter(Boolean).join(' · ') }}
                </span>
              </div>
            </el-option>
            <!-- 场景列表为空时的兜底：允许直接输入 -->
            <template v-if="sceneOptions.length === 0 && !scenesLoading" #empty>
              <div style="padding:8px 12px; color:#909399; font-size:13px">
                暂无场景数据，请先在场景管理中创建场景
              </div>
            </template>
          </el-select>
        </el-form-item>
        <el-form-item label="推理插件">
          <el-select v-model="createForm.pluginId" style="width: 100%">
            <el-option v-for="p in pluginOptions" :key="p.pluginId" :label="p.name" :value="p.pluginId" />
          </el-select>
        </el-form-item>
        <el-form-item label="抽帧频率">
          <el-radio-group v-model="createForm.frameFps">
            <el-radio :value="1">1 fps（默认）</el-radio>
            <el-radio :value="2">2 fps</el-radio>
            <el-radio :value="5">5 fps</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 视频列表 -->
        <el-form-item label="视频文件" required>
          <div style="width: 100%">
            <div
              v-for="(video, idx) in createForm.videos"
              :key="idx"
              class="video-item"
            >
              <div class="video-item-header">
                <span class="video-item-index">视频 {{ idx + 1 }}</span>
                <el-select v-model="video.label" size="small" style="width: 130px">
                  <el-option label="混合（默认）" value="MIXED" />
                  <el-option label="正常录像" value="NORMAL" />
                  <el-option label="异常/缺陷" value="ABNORMAL" />
                </el-select>
                <el-button
                  v-if="createForm.videos.length > 1"
                  link
                  size="small"
                  type="danger"
                  @click="removeVideo(idx)"
                >删除</el-button>
              </div>

              <el-upload
                :before-upload="(file: File) => handleVideoUpload(file, idx)"
                :show-file-list="false"
                accept="video/*"
                drag
                style="margin-top: 6px"
              >
                <div v-if="!video.url" class="upload-placeholder">
                  <el-icon class="upload-icon"><Upload /></el-icon>
                  <div>点击或拖拽上传视频</div>
                  <div class="upload-hint">支持 MP4、AVI、MKV，文件大小 ≤ 2GB</div>
                </div>
                <div v-else class="upload-done">
                  <el-icon><VideoPlay /></el-icon>
                  <span>{{ video.name }} 上传成功</span>
                </div>
              </el-upload>
              <el-progress v-if="video.progress > 0 && video.progress < 100" :percentage="video.progress" style="margin-top: 6px" />
            </div>

            <el-button
              text
              :icon="Plus"
              style="margin-top: 8px"
              @click="addVideoSlot"
            >添加更多视频</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button
          type="primary"
          :loading="creating"
          :disabled="!createForm.videos[0]?.url"
          @click="createTask"
        >创建任务</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { Plus, Upload, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import DataTable from '@/components/common/DataTable.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { simulationApi } from '@/api/simulation'
import { algorithmApi } from '@/api/algorithm'
import { sceneApi } from '@/api/scene'
import { formatDateTime } from '@/utils/format'
import type { SimulationTask } from '@/types'

interface VideoSlot {
  url: string
  name: string
  label: string
  progress: number
}

const tasks = ref<SimulationTask[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const showCreate = ref(false)
const creating = ref(false)

const createForm = reactive({
  sceneId: '',
  pluginId: 'CLOUD-PROXY-V1',
  frameFps: 1,
  videos: [{ url: '', name: '', label: 'MIXED', progress: 0 }] as VideoSlot[],
})

const pluginOptions = ref<{ pluginId: string; name: string }[]>([
  { pluginId: 'CLOUD-PROXY-V1', name: 'CLOUD-PROXY-V1（云端推理代理）' }
])

interface SceneOption {
  sceneId: string
  sceneName: string
  factory: string
  category: string
  status: string
}
const sceneOptions = ref<SceneOption[]>([])
const scenesLoading = ref(false)

async function loadScenes() {
  scenesLoading.value = true
  try {
    const res = await sceneApi.list({ page: 1, size: 200, status: 'active' })
    const items = res.data?.data?.items ?? []
    sceneOptions.value = items.map((s: any) => ({
      sceneId:   s.sceneId   ?? s.scene_id,
      sceneName: s.sceneName ?? s.scene_name ?? s.sceneId ?? s.scene_id,
      factory:   s.factory   ?? '',
      category:  s.category  ?? '',
      status:    s.status    ?? '',
    }))
  } catch {
    // 加载失败不阻断，选项为空时用户可手动输入降级处理
  } finally {
    scenesLoading.value = false
  }
}

function openCreate() {
  createForm.sceneId = ''
  createForm.pluginId = 'CLOUD-PROXY-V1'
  createForm.frameFps = 1
  createForm.videos = [{ url: '', name: '', label: 'MIXED', progress: 0 }]
  showCreate.value = true
}

function addVideoSlot() {
  createForm.videos.push({ url: '', name: '', label: 'MIXED', progress: 0 })
}

function removeVideo(idx: number) {
  createForm.videos.splice(idx, 1)
}

async function loadPlugins() {
  try {
    const res = await algorithmApi.list({ page: 1, size: 50 })
    const items = res.data?.data?.items ?? []
    if (items.length > 0) {
      pluginOptions.value = items.map((p: any) => ({
        pluginId: p.pluginId ?? p.plugin_id,
        name: `${p.pluginId ?? p.plugin_id}（${p.name ?? ''}）`
      }))
    }
  } catch {
    // 插件列表加载失败不阻断流程，保留默认选项
  }
}

async function loadTasks() {
  loading.value = true
  try {
    const res = await simulationApi.list({ page: page.value, size: size.value })
    tasks.value = res.data.data.items
    total.value = res.data.data.total
  } finally {
    loading.value = false
  }
}

async function handleVideoUpload(file: File, idx: number) {
  if (!createForm.sceneId) {
    ElMessage.warning('请先填写目标场景 ID')
    return false
  }
  const slot = createForm.videos[idx]
  slot.progress = 0
  try {
    const res = await simulationApi.uploadVideo(file, createForm.sceneId, (p) => { slot.progress = p })
    slot.url = res.data.data.url
    slot.name = file.name
    ElMessage.success(`视频 ${idx + 1} 上传成功`)
  } catch {
    ElMessage.error(`视频 ${idx + 1} 上传失败，请重试`)
  }
  return false // 阻止自动上传
}

async function createTask() {
  if (!createForm.videos[0]?.url) {
    ElMessage.warning('请至少上传一个视频')
    return
  }
  creating.value = true
  try {
    const [first, ...extras] = createForm.videos
    await simulationApi.create({
      sceneId:    createForm.sceneId,
      videoUrl:   first.url,
      videoLabel: first.label,
      pluginId:   createForm.pluginId,
      frameFps:   createForm.frameFps,
      extraVideos: extras.filter(v => v.url).map(v => ({ videoUrl: v.url, label: v.label })),
    })
    ElMessage.success('仿真任务已创建，正在后台执行推理…')
    showCreate.value = false
    loadTasks()
  } finally {
    creating.value = false
  }
}

async function cancelTask(taskId: string) {
  await simulationApi.cancel(taskId)
  ElMessage.success('任务已取消')
  loadTasks()
}

onMounted(() => {
  loadTasks()
  loadPlugins()
  loadScenes()
})
</script>

<style scoped lang="scss">
.video-item {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 10px;
  background: #fafafa;
}
.video-item-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.video-item-index {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}
.upload-placeholder, .upload-done {
  padding: 16px;
  text-align: center;
  color: #909399;
}
.upload-icon {
  font-size: 30px;
  margin-bottom: 6px;
}
.upload-hint {
  font-size: 12px;
  margin-top: 4px;
}
</style>
