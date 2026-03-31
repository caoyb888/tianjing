<template>
  <div class="sandbox-report" v-loading="loading">
    <PageHeader title="Sandbox 对比报告" :description="`会话 ${sessionId} vs 生产基线`">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button
          v-if="report && canPromote"
          type="success"
          :icon="Upload"
          @click="showPromote = true"
        >
          申请转正
        </el-button>
      </template>
    </PageHeader>

    <div v-if="report">
      <!-- 门禁状态 -->
      <el-card shadow="never" class="gate-card">
        <template #header><span class="card-title">Sandbox 转正门禁检查</span></template>
        <el-row :gutter="16">
          <el-col :md="8" v-for="gate in gateChecks" :key="gate.name">
            <div class="gate-item" :class="gate.passed ? 'passed' : 'failed'">
              <el-icon class="gate-icon">
                <component :is="gate.passed ? 'CircleCheck' : 'CircleClose'" />
              </el-icon>
              <div class="gate-info">
                <div class="gate-name">{{ gate.name }}</div>
                <div class="gate-desc">{{ gate.description }}</div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 精度对比 -->
      <el-row :gutter="16" style="margin-top: 16px">
        <el-col :md="12">
          <el-card shadow="never">
            <template #header><span class="card-title">精度指标对比</span></template>
            <el-table :data="report.metrics_comparison || []" size="small">
              <el-table-column label="指标" prop="metric" />
              <el-table-column label="生产基线" prop="production" />
              <el-table-column label="Sandbox" prop="sandbox" />
              <el-table-column label="差异" prop="diff">
                <template #default="{ row }">
                  <span :class="row.diff > 0 ? 'text-success' : 'text-danger'">
                    {{ row.diff > 0 ? '+' : '' }}{{ (row.diff * 100).toFixed(2) }}%
                  </span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
        <el-col :md="12">
          <el-card shadow="never" style="height: 100%">
            <template #header><span class="card-title">资源消耗对比</span></template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="GPU 显存(生产)">
                {{ report.resource_comparison?.production_gpu_memory_gb }} GB
              </el-descriptions-item>
              <el-descriptions-item label="GPU 显存(Sandbox)">
                {{ report.resource_comparison?.sandbox_gpu_memory_gb }} GB
              </el-descriptions-item>
              <el-descriptions-item label="推理 P99(生产)">
                {{ report.resource_comparison?.production_p99_ms }} ms
              </el-descriptions-item>
              <el-descriptions-item label="推理 P99(Sandbox)">
                {{ report.resource_comparison?.sandbox_p99_ms }} ms
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
    </div>

      <!-- 样本帧对比 -->
      <el-card shadow="never" style="margin-top: 16px" v-if="report.sample_frame">
        <template #header><span class="card-title">样本帧检测对比（生产 vs Sandbox）</span></template>
        <SandboxCompareViewer
          :image-url="report.sample_frame.image_url"
          :production-detections="report.sample_frame.production_detections"
          :sandbox-detections="report.sample_frame.sandbox_detections"
        />
      </el-card>
    </div>

    <EmptyState v-else-if="!loading" description="报告生成中，请稍后刷新" />

    <!-- 申请转正对话框 -->
    <el-dialog v-model="showPromote" title="申请模型转正" width="480px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px">
        转正需要 2 位 MODEL_REVIEWER 角色审核，且审核人不能与提交人相同（四眼原则）。
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="备注说明">
          <el-input v-model="promoteComment" type="textarea" :rows="3" placeholder="可选，描述转正理由" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPromote = false">取消</el-button>
        <el-button type="primary" :loading="promoting" @click="submitPromote">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SandboxCompareViewer from '@/components/business/SandboxCompareViewer.vue'
import { sandboxApi } from '@/api/sandbox'

const route = useRoute()
const sessionId = route.params.sessionId as string
const report = ref<Record<string, any> | null>(null)
const loading = ref(false)
const showPromote = ref(false)
const promoting = ref(false)
const promoteComment = ref('')

const gateChecks = computed(() => {
  if (!report.value) return []
  return [
    {
      name: '精度提升检查',
      description: 'Sandbox 精确率 ≥ 生产基线 × 102%',
      passed: report.value.gates?.accuracy_passed,
    },
    {
      name: '资源消耗检查',
      description: 'GPU 显存 ≤ 生产基线 × 110%',
      passed: report.value.gates?.resource_passed,
    },
    {
      name: '延迟检查',
      description: 'P99 延迟 ≤ 生产基线 × 120%',
      passed: report.value.gates?.latency_passed,
    },
  ]
})

const canPromote = computed(() =>
  report.value?.gates?.accuracy_passed &&
  report.value?.gates?.resource_passed &&
  report.value?.gates?.latency_passed
)

async function loadReport() {
  loading.value = true
  try {
    const res = await sandboxApi.getReport(sessionId)
    report.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function submitPromote() {
  promoting.value = true
  try {
    await sandboxApi.promote(sessionId, { comment: promoteComment.value })
    ElMessage.success('转正申请已提交，等待审核')
    showPromote.value = false
  } finally {
    promoting.value = false
  }
}

onMounted(loadReport)
</script>

<style scoped lang="scss">
.gate-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
  border: 1px solid;

  &.passed {
    border-color: #b7eb8f;
    background: #f6ffed;
    .gate-icon { color: #52c41a; }
  }

  &.failed {
    border-color: #ffa39e;
    background: #fff2f0;
    .gate-icon { color: #ff4d4f; }
  }
}

.gate-icon { font-size: 24px; flex-shrink: 0; }
.gate-name { font-weight: 600; margin-bottom: 4px; }
.gate-desc { font-size: 12px; color: #606266; }
.card-title { font-weight: 600; }
.text-success { color: #52c41a; }
.text-danger { color: #ff4d4f; }
</style>
