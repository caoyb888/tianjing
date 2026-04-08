<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Close, ArrowRight, ArrowLeft as ArrowLeftIcon } from '@element-plus/icons-vue'
import { simulationApi } from '@/api/simulation'
import AnnotationCanvas from '@/components/annotation/AnnotationCanvas.vue'
import { useAnnotationHotkeys } from '@/composables/useAnnotationHotkeys'
import type { Detection } from '@/components/annotation/AnnotationCanvas.vue'

// ============================================================================
// 路由和参数
// ============================================================================

const route = useRoute()
const router = useRouter()
const taskId = computed(() => route.params.taskId as string)

// ============================================================================
// 状态
// ============================================================================

/** 任务信息 */
const taskStats = ref({
  totalFrames: 0,
  reviewedFrames: 0,
  pendingFrames: 0,
  approvedFrames: 0,
  rejectedFrames: 0,
  progressPercent: 0
})

/** 帧列表 */
const frames = ref<Array<{
  frameId: string
  frameUrl: string
  frameIndex: number
  reviewStatus: string
  isModified: boolean
  detectionCount: number
  correctedCount: number | null
}>>([])

/** 当前选中帧索引 */
const currentFrameIndex = ref(0)

/** 当前帧详情 */
const currentFrame = ref<{
  frameId: string
  frameUrl: string
  frameIndex: number
  reviewStatus: string
  isModified: boolean
  reviewedBy: string | null
  reviewedAt: string | null
  originalDetections: Detection[]
  correctedDetections: Detection[] | null
} | null>(null)

/** 当前帧的检测框（用于画布） */
const currentDetections = ref<Detection[]>([])

/** 加载状态 */
const loading = ref(false)
const saving = ref(false)

/** 选中框的ID */
const selectedBoxId = ref<string | null>(null)

/** 画布组件引用 */
const canvasRef = ref<InstanceType<typeof AnnotationCanvas> | null>(null)

// ============================================================================
// 计算属性
// ============================================================================

const progressText = computed(() => {
  return `${taskStats.value.reviewedFrames}/${taskStats.value.totalFrames}`
})

const currentFrameStatus = computed(() => {
  return currentFrame.value?.reviewStatus || 'PENDING'
})

// ============================================================================
// 方法
// ============================================================================

/** 初始化审核 */
async function initReview() {
  try {
    loading.value = true
    await simulationApi.initReview(taskId.value)
    ElMessage.success('审核初始化成功')
    await loadStats()
    await loadFrames()
  } catch (error: any) {
    // 已存在记录不算错误
    if (error.response?.data?.code !== 0) {
      ElMessage.warning(error.response?.data?.message || '初始化失败')
    }
  } finally {
    loading.value = false
  }
}

/** 加载统计 */
async function loadStats() {
  try {
    const res = await simulationApi.getReviewStats(taskId.value)
    taskStats.value = res.data.data
  } catch (error) {
    console.error('加载统计失败', error)
  }
}

/** 加载帧列表 */
async function loadFrames() {
  try {
    const res = await simulationApi.listReviewFrames(taskId.value, {
      page: 1,
      size: 1000 // 加载全部，前端做虚拟滚动
    })
    frames.value = res.data.data.items
    if (frames.value.length > 0 && !currentFrame.value) {
      await loadFrameDetail(frames.value[0].frameId)
    }
  } catch (error) {
    console.error('加载帧列表失败', error)
  }
}

/** 加载单帧详情 */
async function loadFrameDetail(frameId: string) {
  try {
    loading.value = true
    const res = await simulationApi.getReviewFrame(taskId.value, frameId)
    currentFrame.value = res.data.data
    currentFrameIndex.value = frames.value.findIndex(f => f.frameId === frameId)

    // 准备检测框数据
    const original = res.data.data.originalDetections || []
    const corrected = res.data.data.correctedDetections

    if (corrected) {
      // 使用校正后的框
      currentDetections.value = corrected.map((d: any, idx: number) => ({
        id: `det_${idx}`,
        classId: d.classId,
        className: d.className,
        confidence: d.confidence,
        bbox: d.bbox,
        isNew: false
      }))
    } else {
      // 使用原始框
      currentDetections.value = original.map((d: any, idx: number) => ({
        id: `det_${idx}`,
        classId: d.classId,
        className: d.className,
        confidence: d.confidence,
        bbox: d.bbox,
        isNew: false
      }))
    }
  } catch (error) {
    console.error('加载帧详情失败', error)
    ElMessage.error('加载帧详情失败')
  } finally {
    loading.value = false
  }
}

/** 保存当前帧审核结果 */
async function saveFrame(status: 'APPROVED' | 'REJECTED' | 'SKIPPED') {
  if (!currentFrame.value) return

  try {
    saving.value = true
    const correctedDetections = currentDetections.value.map(d => ({
      classId: d.classId,
      className: d.className,
      confidence: d.confidence,
      bbox: d.bbox
    }))

    await simulationApi.saveReviewFrame(taskId.value, currentFrame.value.frameId, {
      reviewStatus: status,
      correctedDetections: status === 'REJECTED' ? null : correctedDetections
    })

    // 更新本地状态
    const frame = frames.value.find(f => f.frameId === currentFrame.value!.frameId)
    if (frame) {
      frame.reviewStatus = status
      frame.isModified = true
      frame.correctedCount = correctedDetections.length
    }

    await loadStats()

    // 自动下一帧
    if (currentFrameIndex.value < frames.value.length - 1) {
      await nextFrame()
    } else {
      ElMessage.success('审核完成')
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

/** 批量通过 */
async function bulkApprove() {
  try {
    await ElMessageBox.confirm('确定要批量通过所有未修改的帧吗？', '批量通过', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    loading.value = true
    const res = await simulationApi.bulkApprove(taskId.value, {
      mode: 'all_unmodified'
    })

    ElMessage.success(`批量通过 ${res.data.data.processed_count} 帧`)
    await loadStats()
    await loadFrames()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error(error.response?.data?.message || '批量通过失败')
    }
  } finally {
    loading.value = false
  }
}

/** 上一帧 */
async function prevFrame() {
  if (currentFrameIndex.value > 0) {
    const prevIndex = currentFrameIndex.value - 1
    await loadFrameDetail(frames.value[prevIndex].frameId)
  }
}

/** 下一帧 */
async function nextFrame() {
  if (currentFrameIndex.value < frames.value.length - 1) {
    const nextIndex = currentFrameIndex.value + 1
    await loadFrameDetail(frames.value[nextIndex].frameId)
  }
}

/** 删除选中框 */
function deleteSelectedBox() {
  canvasRef.value?.deleteSelectedBox()
}

/** 返回任务详情 */
function goBack() {
  router.push(`/simulations/${taskId.value}`)
}

/** 导出数据集 */
function exportDataset() {
  router.push(`/simulations/${taskId.value}?export=true`)
}

// ============================================================================
// 键盘快捷键
// ============================================================================

useAnnotationHotkeys({
  onPrevFrame: prevFrame,
  onNextFrame: nextFrame,
  onApprove: () => saveFrame('APPROVED'),
  onReject: () => saveFrame('REJECTED'),
  onDeleteBox: deleteSelectedBox,
  onCancel: () => canvasRef.value?.cancel()
})

// ============================================================================
// 生命周期
// ============================================================================

onMounted(async () => {
  await initReview()
})
</script>

<template>
  <div class="annotation-review-view">
    <!-- 顶部栏 -->
    <div class="header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" @click="goBack">
          返回详情
        </el-button>
        <h2 class="title">
          标注审核 — {{ taskId }}
          <el-tag v-if="taskStats.progressPercent === 100" type="success" size="small">
            已完成
          </el-tag>
        </h2>
      </div>

      <div class="header-center">
        <el-progress
          :percentage="taskStats.progressPercent"
          :format="() => progressText"
          style="width: 200px"
        />
        <span class="stats-text">
          通过 {{ taskStats.approvedFrames }} / 拒绝 {{ taskStats.rejectedFrames }} / 待审 {{ taskStats.pendingFrames }}
        </span>
      </div>

      <div class="header-right">
        <el-button type="success" @click="bulkApprove">
          批量通过未修改帧
        </el-button>
        <el-button type="primary" @click="exportDataset">
          导出数据集
        </el-button>
      </div>
    </div>

    <!-- 主体区域 -->
    <div class="main-content">
      <!-- 左侧：帧缩略图列表 -->
      <div class="left-panel">
        <div class="panel-title">帧列表</div>
        <el-scrollbar class="frame-list">
          <div
            v-for="(frame, idx) in frames"
            :key="frame.frameId"
            :class="['frame-thumb', {
              active: currentFrame?.frameId === frame.frameId,
              approved: frame.reviewStatus === 'APPROVED',
              rejected: frame.reviewStatus === 'REJECTED'
            }]"
            @click="loadFrameDetail(frame.frameId)"
          >
            <div class="thumb-index">{{ idx + 1 }}</div>
            <img :src="frame.frameUrl" loading="lazy" />
            <div class="thumb-status">
              <el-icon v-if="frame.reviewStatus === 'APPROVED'"><Check /></el-icon>
              <el-icon v-else-if="frame.reviewStatus === 'REJECTED'"><Close /></el-icon>
              <span v-else-if="frame.reviewStatus === 'SKIPPED'">跳过</span>
            </div>
          </div>
        </el-scrollbar>
      </div>

      <!-- 中央：标注画布 -->
      <div class="center-panel">
        <div class="canvas-container">
          <AnnotationCanvas
            v-if="currentFrame"
            ref="canvasRef"
            :frame-url="currentFrame.frameUrl"
            v-model:detections="currentDetections"
            @box-selected="selectedBoxId = $event"
          />
          <div v-else class="empty-state">
            <el-empty description="加载中..." />
          </div>
        </div>

        <!-- 底部帧导航 -->
        <div class="frame-nav">
          <el-button :icon="ArrowLeftIcon" :disabled="currentFrameIndex === 0" @click="prevFrame">
            上一帧
          </el-button>
          <span class="frame-info">
            {{ currentFrameIndex + 1 }} / {{ frames.length }} 帧
            <template v-if="currentFrame">
              | {{ currentFrame.frameId }}
              <el-tag v-if="currentFrameStatus === 'APPROVED'" type="success" size="small">已通过</el-tag>
              <el-tag v-else-if="currentFrameStatus === 'REJECTED'" type="danger" size="small">已拒绝</el-tag>
              <el-tag v-else-if="currentFrameStatus === 'SKIPPED'" type="info" size="small">已跳过</el-tag>
              <el-tag v-else type="warning" size="small">待审核</el-tag>
            </template>
          </span>
          <el-button :disabled="currentFrameIndex >= frames.length - 1" @click="nextFrame">
            下一帧<el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 右侧：操作面板 -->
      <div class="right-panel">
        <div class="panel-title">操作面板</div>

        <!-- 类别信息 -->
        <div class="section">
          <h4>当前框信息</h4>
          <div v-if="selectedBoxId" class="box-info">
            <p>框ID: {{ selectedBoxId }}</p>
            <el-button type="danger" size="small" @click="deleteSelectedBox">
              删除选中框 (Delete)
            </el-button>
          </div>
          <div v-else class="box-info empty">
            <p>未选中框</p>
            <p class="hint">点击框可选中，按 Delete 删除</p>
          </div>
        </div>

        <!-- 操作说明 -->
        <div class="section">
          <h4>操作说明</h4>
          <ul class="shortcuts">
            <li><kbd>A</kbd> / <kbd>←</kbd> 上一帧</li>
            <li><kbd>D</kbd> / <kbd>→</kbd> 下一帧</li>
            <li><kbd>Enter</kbd> 通过当前帧</li>
            <li><kbd>R</kbd> 拒绝当前帧</li>
            <li><kbd>Delete</kbd> 删除选中框</li>
            <li><kbd>Esc</kbd> 取消选中</li>
            <li>拖拽空白处画新框</li>
          </ul>
        </div>

        <!-- 审核操作 -->
        <div class="section actions">
          <h4>审核操作</h4>
          <el-button
            type="success"
            size="large"
            :loading="saving"
            :disabled="!currentFrame"
            @click="saveFrame('APPROVED')"
          >
            <el-icon><Check /></el-icon>
            通过 (Enter)
          </el-button>
          <el-button
            type="danger"
            size="large"
            :loading="saving"
            :disabled="!currentFrame"
            @click="saveFrame('REJECTED')"
          >
            <el-icon><Close /></el-icon>
            拒绝 (R)
          </el-button>
          <el-button
            size="large"
            :loading="saving"
            :disabled="!currentFrame"
            @click="saveFrame('SKIPPED')"
          >
            跳过
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.annotation-review-view {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #f5f7fa;
}

// 顶部栏
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background-color: white;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);

  .header-left {
    display: flex;
    align-items: center;
    gap: 16px;

    .title {
      margin: 0;
      font-size: 18px;
      font-weight: 600;
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }

  .header-center {
    display: flex;
    align-items: center;
    gap: 16px;

    .stats-text {
      font-size: 14px;
      color: #606266;
    }
  }

  .header-right {
    display: flex;
    gap: 8px;
  }
}

// 主体区域
.main-content {
  display: flex;
  flex: 1;
  overflow: hidden;
}

// 左侧面板
.left-panel {
  width: 200px;
  background-color: white;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;

  .panel-title {
    padding: 12px 16px;
    font-weight: 600;
    border-bottom: 1px solid #e4e7ed;
  }

  .frame-list {
    flex: 1;
    padding: 8px;

    .frame-thumb {
      position: relative;
      margin-bottom: 8px;
      border-radius: 4px;
      overflow: hidden;
      cursor: pointer;
      border: 2px solid transparent;
      transition: all 0.2s;

      &:hover {
        border-color: #409eff;
      }

      &.active {
        border-color: #409eff;
        box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.2);
      }

      &.approved {
        border-color: #67c23a;
      }

      &.rejected {
        border-color: #f56c6c;
      }

      img {
        width: 100%;
        height: auto;
        display: block;
      }

      .thumb-index {
        position: absolute;
        top: 4px;
        left: 4px;
        background: rgba(0, 0, 0, 0.6);
        color: white;
        padding: 2px 6px;
        border-radius: 4px;
        font-size: 12px;
      }

      .thumb-status {
        position: absolute;
        top: 4px;
        right: 4px;
        background: rgba(255, 255, 255, 0.9);
        padding: 2px 6px;
        border-radius: 4px;
        font-size: 12px;

        .el-icon {
          font-size: 14px;
        }
      }
    }
  }
}

// 中央面板
.center-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .canvas-container {
    flex: 1;
    padding: 16px;
    overflow: hidden;

    .empty-state {
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }

  .frame-nav {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
    padding: 12px;
    background-color: white;
    border-top: 1px solid #e4e7ed;

    .frame-info {
      font-size: 14px;
      color: #606266;
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }
}

// 右侧面板
.right-panel {
  width: 280px;
  background-color: white;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow-y: auto;

  .panel-title {
    padding: 12px 16px;
    font-weight: 600;
    border-bottom: 1px solid #e4e7ed;
  }

  .section {
    padding: 16px;
    border-bottom: 1px solid #e4e7ed;

    h4 {
      margin: 0 0 12px 0;
      font-size: 14px;
      color: #303133;
    }

    .box-info {
      &.empty {
        color: #909399;

        .hint {
          font-size: 12px;
          margin-top: 8px;
        }
      }
    }

    .shortcuts {
      margin: 0;
      padding-left: 20px;
      font-size: 13px;
      color: #606266;

      li {
        margin-bottom: 8px;
      }

      kbd {
        display: inline-block;
        padding: 2px 6px;
        background: #f4f4f5;
        border: 1px solid #dcdfe6;
        border-radius: 4px;
        font-family: monospace;
        font-size: 12px;
      }
    }

    &.actions {
      display: flex;
      flex-direction: column;
      gap: 8px;

      :deep(.el-button) {
        width: 100%;
        margin-left: 0;
      }
    }
  }
}
</style>
