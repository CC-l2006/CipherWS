<template>
  <canvas ref="canvasRef" class="shader-canvas" />
</template>

<script setup>
import { ref } from 'vue'
import { useShaderRenderer } from '../composables/useShaderRenderer.js'

const props = defineProps({
  params: { type: Object, required: true },
})

const canvasRef = ref(null)

const { replay } = useShaderRenderer(canvasRef, () => props.params)

defineExpose({ replay })
</script>

<style scoped>
.shader-canvas {
  display: block;
  width: 100%;
  height: 100%;
  position: fixed;
  top: 0;
  left: 0;
  z-index: 0;
  /* 小屏触摸：防止 canvas 截获滑动操作（面板可滚动） */
  touch-action: none;
}
</style>