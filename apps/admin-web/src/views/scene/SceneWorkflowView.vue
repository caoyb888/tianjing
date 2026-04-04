<template>
  <div class="workflow-editor">
    <div class="editor-header">
      <div class="editor-title">
        <el-icon><Share /></el-icon>
        低代码算法编排器 — {{ sceneId }}
      </div>
      <div class="editor-actions">
        <el-button :icon="RefreshLeft" @click="loadWorkflow">重新加载</el-button>
        <el-button type="primary" :icon="DocumentAdd" :loading="saving" @click="saveWorkflow">保存编排</el-button>
        <el-button @click="$router.back()">返回</el-button>
      </div>
    </div>

    <div class="editor-body">
      <!-- 节点面板 -->
      <div class="node-palette">
        <div class="palette-title">节点类型</div>
        <div
          v-for="nodeType in nodeTypes"
          :key="nodeType.type"
          class="palette-node"
          :style="{ borderLeftColor: nodeType.color }"
          draggable="true"
          @dragstart="onDragStart($event, nodeType.type)"
        >
          <el-icon><component :is="nodeType.icon" /></el-icon>
          <span>{{ nodeType.label }}</span>
        </div>
      </div>

      <!-- 画布 -->
      <div class="canvas-area" @drop="onDrop" @dragover.prevent>
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-edge-options="{ animated: true, type: 'smoothstep' }"
          fit-view-on-init
          class="flow-canvas"
          @node-click="onNodeClick"
        >
          <Background pattern-color="#e0e0e0" :gap="20" />
          <Controls />
          <MiniMap />

          <!-- 自定义节点 -->
          <template #node-videoSource="props">
            <WorkflowNode v-bind="props" color="#52c41a" label="视频源" :icon="'VideoCamera'" />
          </template>
          <template #node-preprocess="props">
            <WorkflowNode v-bind="props" color="#1890ff" label="预处理" :icon="'Filter'" />
          </template>
          <template #node-inference="props">
            <WorkflowNode v-bind="props" color="#722ed1" label="推理插件" :icon="'Cpu'" />
          </template>
          <template #node-logicGate="props">
            <WorkflowNode v-bind="props" color="#fa8c16" label="逻辑门" :icon="'Switch'" />
          </template>
          <template #node-alarm="props">
            <WorkflowNode v-bind="props" color="#f5222d" label="告警动作" :icon="'Bell'" />
          </template>
          <template #node-storage="props">
            <WorkflowNode v-bind="props" color="#13c2c2" label="存储动作" :icon="'FolderAdd'" />
          </template>
          <template #node-mqttPush="props">
            <WorkflowNode v-bind="props" color="#eb2f96" label="MQTT 推送" :icon="'Share'" />
          </template>
        </VueFlow>
      </div>

      <!-- 节点属性面板 -->
      <div v-if="selectedNode" class="node-properties">
        <div class="properties-title">节点属性</div>
        <el-form :model="selectedNode.data" label-width="90px" size="small">
          <el-form-item label="节点标签">
            <el-input v-model="selectedNode.data.label" @change="updateNodeData" />
          </el-form-item>

          <!-- 视频源特有属性 -->
          <template v-if="selectedNode.type === 'videoSource'">
            <el-form-item label="摄像头">
              <el-select
                v-model="selectedNode.data.device_code"
                filterable
                allow-create
                placeholder="选择或输入设备编码"
                style="width:100%"
                @change="updateNodeData"
              >
                <el-option
                  v-for="opt in cameraOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="镜像帧率">
              <el-input-number
                v-model="selectedNode.data.mirror_fps"
                :min="1"
                :max="25"
                style="width:100%"
                @change="updateNodeData"
              />
            </el-form-item>
          </template>

          <!-- 预处理特有属性 -->
          <template v-if="selectedNode.type === 'preprocess'">
            <el-form-item label="处理类型">
              <el-select v-model="selectedNode.data.preprocess_type" style="width:100%" @change="updateNodeData">
                <el-option label="去雾增强" value="dehaze" />
                <el-option label="亮度调整" value="brightness" />
                <el-option label="ROI 裁剪" value="crop" />
                <el-option label="对比度增强" value="contrast" />
              </el-select>
            </el-form-item>
          </template>

          <!-- 推理节点特有属性 -->
          <template v-if="selectedNode.type === 'inference'">
            <el-form-item label="插件ID">
              <el-input v-model="selectedNode.data.plugin_id" @change="updateNodeData" />
            </el-form-item>
            <el-form-item label="置信阈值">
              <el-input-number v-model="selectedNode.data.conf_threshold" :min="0" :max="1" :step="0.05" :precision="2" style="width:100%" @change="updateNodeData" />
            </el-form-item>
          </template>

          <!-- 告警节点特有属性 -->
          <template v-if="selectedNode.type === 'alarm'">
            <el-form-item label="告警级别">
              <el-select v-model="selectedNode.data.level" style="width:100%" @change="updateNodeData">
                <el-option label="CRITICAL" value="CRITICAL" />
                <el-option label="WARNING" value="WARNING" />
                <el-option label="INFO" value="INFO" />
              </el-select>
            </el-form-item>
            <el-form-item label="确认帧数">
              <el-input-number v-model="selectedNode.data.confirm_frames" :min="1" :max="10" style="width:100%" @change="updateNodeData" />
            </el-form-item>
          </template>

          <!-- 逻辑门属性 -->
          <template v-if="selectedNode.type === 'logicGate'">
            <el-form-item label="条件类型">
              <el-select v-model="selectedNode.data.condition" style="width:100%" @change="updateNodeData">
                <el-option label="置信度阈值" value="confidence_threshold" />
                <el-option label="连续帧计数" value="frame_count" />
                <el-option label="类别过滤" value="class_filter" />
              </el-select>
            </el-form-item>
          </template>

          <!-- 存储动作特有属性 -->
          <template v-if="selectedNode.type === 'storage'">
            <el-form-item label="保留时长">
              <el-input-number
                v-model="selectedNode.data.retention_hours"
                :min="1"
                :max="720"
                style="width:100%"
                @change="updateNodeData"
              />
            </el-form-item>
            <div style="font-size:11px;color:#909399;margin:-8px 0 8px 90px">单位：小时，最长 720h（30天）</div>
          </template>

          <!-- MQTT 推送属性 -->
          <template v-if="selectedNode.type === 'mqttPush'">
            <el-form-item label="Topic">
              <el-input v-model="selectedNode.data.topic" @change="updateNodeData" />
            </el-form-item>
          </template>

          <el-form-item style="margin-top: 12px">
            <el-button type="danger" size="small" @click="deleteSelectedNode">删除节点</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import type { Node, Edge } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import { Share, RefreshLeft, DocumentAdd } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import WorkflowNode from '@/components/business/WorkflowNode.vue'
import { sceneApi } from '@/api/scene'
import { deviceApi } from '@/api/device'

const route = useRoute()
const sceneId = route.params.sceneId as string
const saving = ref(false)

const nodes = ref<Node[]>([])
const edges = ref<Edge[]>([])
const selectedNode = ref<Node | null>(null)

// 摄像头设备列表（供视频源节点选择）
const cameraOptions = ref<{ label: string; value: string }[]>([])

async function loadCameraOptions() {
  try {
    const res = await deviceApi.list({ size: 200 })
    const items: any[] = res.data?.data?.items ?? res.data?.items ?? []
    cameraOptions.value = items.map((d: any) => ({
      label: `${d.deviceName ?? d.device_name}（${d.deviceCode ?? d.device_code}）`,
      value: d.deviceCode ?? d.device_code,
    }))
  } catch {
    // 加载失败时不阻断编排器使用，手动输入设备编码即可
  }
}

const { addNodes, removeNodes, findNode } = useVueFlow()

// 节点类型定义
const nodeTypes = [
  { type: 'videoSource', label: '视频源', icon: 'VideoCamera', color: '#52c41a' },
  { type: 'preprocess', label: '预处理', icon: 'Filter', color: '#1890ff' },
  { type: 'inference', label: '推理插件', icon: 'Cpu', color: '#722ed1' },
  { type: 'logicGate', label: '逻辑门', icon: 'Switch', color: '#fa8c16' },
  { type: 'alarm', label: '告警动作', icon: 'Bell', color: '#f5222d' },
  { type: 'storage', label: '存储动作', icon: 'FolderAdd', color: '#13c2c2' },
  { type: 'mqttPush', label: 'MQTT 推送', icon: 'Share', color: '#eb2f96' },
]

let dragNodeType = ''

function onDragStart(event: DragEvent, nodeType: string) {
  dragNodeType = nodeType
}

function onDrop(event: DragEvent) {
  if (!dragNodeType) return
  const canvasEl = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const position = { x: event.clientX - canvasEl.left, y: event.clientY - canvasEl.top }

  const newNode: Node = {
    id: `${dragNodeType}-${Date.now()}`,
    type: dragNodeType,
    position,
    data: {
      label: nodeTypes.find((t) => t.type === dragNodeType)?.label || dragNodeType,
      // 视频源
      device_code: '',
      mirror_fps: 5,
      // 预处理
      preprocess_type: 'dehaze',
      // 推理插件
      plugin_id: '',
      conf_threshold: 0.85,
      // 逻辑门
      condition: 'confidence_threshold',
      // 告警动作
      level: 'WARNING',
      confirm_frames: 3,
      // 存储动作
      retention_hours: 72,
      // MQTT 推送
      topic: `iiot/tianjing/alarm/${sceneId}`,
    },
  }
  addNodes([newNode])
  dragNodeType = ''
}

function onNodeClick({ node }: { node: Node }) {
  selectedNode.value = { ...node }
}

function updateNodeData() {
  if (!selectedNode.value) return
  const node = findNode(selectedNode.value.id)
  if (node) {
    node.data = { ...selectedNode.value.data }
  }
}

function deleteSelectedNode() {
  if (!selectedNode.value) return
  removeNodes([selectedNode.value.id])
  selectedNode.value = null
}

async function loadWorkflow() {
  try {
    const res = await sceneApi.get(sceneId)
    const workflowJson = (res.data.data as any).workflowJson
    if (workflowJson) {
      nodes.value = workflowJson.nodes || []
      edges.value = workflowJson.edges || []
    }
  } catch {
    ElMessage.warning('暂无编排配置，请从节点面板拖入节点开始编排')
  }
}

async function saveWorkflow() {
  saving.value = true
  try {
    await sceneApi.update(sceneId, {
      workflowJson: {
        nodes: nodes.value,
        edges: edges.value,
      },
    })
    ElMessage.success('编排已保存')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadWorkflow()
  loadCameraOptions()
})
</script>

<style scoped lang="scss">
.workflow-editor {
  height: calc(100vh - 128px);
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
}

.editor-header {
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid #e8e8e8;
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}

.editor-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
}

.editor-actions {
  display: flex;
  gap: 8px;
}

.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.node-palette {
  width: 140px;
  border-right: 1px solid #e8e8e8;
  padding: 12px 8px;
  overflow-y: auto;
  flex-shrink: 0;
}

.palette-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  padding: 0 4px;
}

.palette-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px;
  border: 1px solid #e8e8e8;
  border-left: 3px solid;
  border-radius: 4px;
  margin-bottom: 6px;
  cursor: grab;
  font-size: 12px;
  background: #fafafa;
  transition: box-shadow 0.2s;

  &:hover {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
    background: #fff;
  }

  &:active {
    cursor: grabbing;
  }
}

.canvas-area {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.flow-canvas {
  width: 100%;
  height: 100%;
}

.node-properties {
  width: 240px;
  border-left: 1px solid #e8e8e8;
  padding: 12px;
  overflow-y: auto;
  flex-shrink: 0;
}

.properties-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}
</style>
