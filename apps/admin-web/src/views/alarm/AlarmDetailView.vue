<template>
  <div class="alarm-detail" v-loading="loading">
    <PageHeader title="告警详情" :description="`告警 ID：${alarmId}`">
      <template #actions>
        <el-button @click="$router.back()">返回</el-button>
        <el-button v-if="alarm && !alarm.feedbackStatus" type="primary" @click="showFeedback = true">
          提交处置
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" v-if="alarm">
      <el-col :lg="14" :md="24">
        <!-- 告警图像（Canvas 叠加检测框） -->
        <el-card shadow="never" class="image-card">
          <template #header>
            <span class="card-title">告警图像</span>
            <el-tag v-if="alarm.isSandbox" type="warning" size="small" style="margin-left: 8px">
              Sandbox · 虚线框
            </el-tag>
          </template>
          <AlarmImageViewer
            :image-url="alarm.imageUrl"
            :detections="flatDetections"
            :is-sandbox="alarm.isSandbox"
          />
          <el-alert v-if="alarm.isSandbox" type="warning" :closable="false" show-icon style="margin-top: 8px">
            此为 Sandbox 推理结果，标注框以虚线显示，不触发生产告警
          </el-alert>
        </el-card>
      </el-col>

      <el-col :lg="10" :md="24">
        <el-card shadow="never">
          <template #header><span class="card-title">告警信息</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="告警级别">
              <el-tag :type="levelType(alarm.alarmLevel)">{{ alarm.alarmLevel }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="异常类型">{{ alarm.anomalyType }}</el-descriptions-item>
            <el-descriptions-item label="置信度">{{ formatConfidence(alarm.confidence) }}</el-descriptions-item>
            <el-descriptions-item label="所属场景">{{ alarm.sceneId }}</el-descriptions-item>
            <el-descriptions-item label="厂部">{{ FACTORY_CONFIG[alarm.factory]?.label }}</el-descriptions-item>
            <el-descriptions-item label="来源">
              <el-tag v-if="alarm.isSandbox" type="warning" effect="plain">Sandbox</el-tag>
              <el-tag v-else type="success" effect="plain">生产</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="告警时间">{{ formatDateTime(alarm.timestamp) }}</el-descriptions-item>
            <el-descriptions-item label="处置状态">
              <StatusBadge :status="alarm.feedbackStatus || 'pending'" :map="feedbackStatusMap" />
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- 处置表单 -->
    <el-dialog v-model="showFeedback" title="提交处置结果" width="480px">
      <el-form :model="feedbackForm" label-width="100px">
        <el-form-item label="处置结果" required>
          <el-radio-group v-model="feedbackForm.feedback_type">
            <el-radio value="confirm">确认告警（真阳性）</el-radio>
            <el-radio value="reject">驳回告警（误报）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处置说明">
          <el-input v-model="feedbackForm.comment" type="textarea" :rows="3" placeholder="可选，描述处置原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFeedback = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitFeedback">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import AlarmImageViewer from '@/components/business/AlarmImageViewer.vue'
import { alarmApi } from '@/api/alarm'
import { FACTORY_CONFIG } from '@/constants'
import { AlarmLevel, type AlarmRecord } from '@/types'
import { formatDateTime, formatConfidence } from '@/utils/format'

const route = useRoute()
const alarmId = route.params.alarmId as string
const alarm = ref<AlarmRecord | null>(null)
const loading = ref(false)
const showFeedback = ref(false)
const submitting = ref(false)

// 将 AlarmDetection.bbox 展开为 AlarmImageViewer 需要的扁平格式
const flatDetections = computed(() =>
  (alarm.value?.detections || []).map((d) => ({
    x1: d.bbox.x1,
    y1: d.bbox.y1,
    x2: d.bbox.x2,
    y2: d.bbox.y2,
    class_name: d.class_name,
    confidence: d.confidence,
  }))
)

const feedbackForm = reactive({
  feedback_type: 'confirm' as 'confirm' | 'reject',
  comment: '',
})

const feedbackStatusMap = {
  pending: { label: '待处置', type: 'info' as const },
  confirmed: { label: '已确认', type: 'success' as const },
  rejected: { label: '已驳回', type: 'warning' as const },
}

function levelType(level: AlarmLevel) {
  const map = { [AlarmLevel.CRITICAL]: 'danger', [AlarmLevel.WARNING]: 'warning', [AlarmLevel.INFO]: 'info' }
  return (map[level] || 'info') as 'danger' | 'warning' | 'info'
}

async function loadAlarm() {
  loading.value = true
  try {
    const res = await alarmApi.get(alarmId)
    alarm.value = res.data.data
  } finally {
    loading.value = false
  }
}

async function submitFeedback() {
  submitting.value = true
  try {
    await alarmApi.submitFeedback(alarmId, feedbackForm)
    ElMessage.success('处置结果已提交')
    showFeedback.value = false
    loadAlarm()
  } finally {
    submitting.value = false
  }
}

onMounted(loadAlarm)
</script>

<style scoped lang="scss">
.alarm-image {
  width: 100%;
  max-height: 420px;
}
.image-error {
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}
.card-title { font-weight: 600; }
</style>
