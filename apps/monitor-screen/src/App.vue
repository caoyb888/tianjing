<template>
  <!-- 外层容器占满视口，背景色与大屏一致 -->
  <div class="viewport">
    <!-- 1920×1080 固定画布，通过 transform: scale 适配任意分辨率 -->
    <div class="screen-canvas" :style="canvasStyle">
      <MonitorScreen />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import MonitorScreen from './MonitorScreen.vue'

// 大屏设计分辨率
const DESIGN_WIDTH  = 1920
const DESIGN_HEIGHT = 1080

const scale = ref(1)

function calcScale() {
  const scaleX = window.innerWidth  / DESIGN_WIDTH
  const scaleY = window.innerHeight / DESIGN_HEIGHT
  // 等比缩放，取最小值保证内容完全显示
  scale.value = Math.min(scaleX, scaleY)
}

const canvasStyle = computed(() => ({
  width:           `${DESIGN_WIDTH}px`,
  height:          `${DESIGN_HEIGHT}px`,
  transform:       `scale(${scale.value})`,
  transformOrigin: 'top left',
  // 居中显示
  marginLeft: `${(window.innerWidth  - DESIGN_WIDTH  * scale.value) / 2}px`,
  marginTop:  `${(window.innerHeight - DESIGN_HEIGHT * scale.value) / 2}px`,
}))

onMounted(() => {
  calcScale()
  window.addEventListener('resize', calcScale)
})
onUnmounted(() => {
  window.removeEventListener('resize', calcScale)
})
</script>

<style scoped>
.viewport {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: #040b1a;
}
.screen-canvas {
  position: absolute;
  top: 0;
  left: 0;
  overflow: hidden;
}
</style>
