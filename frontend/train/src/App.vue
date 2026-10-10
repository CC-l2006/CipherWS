<template>
  <section class="app-root">
    <ShaderCanvas ref="shaderRef" :params="params" />
    <ControlPanel :params="params" @replay="onReplay" />
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import ShaderCanvas from './components/ShaderCanvas.vue'
import ControlPanel from './components/ControlPanel.vue'
import { createDefaultParams } from './composables/useShaderRenderer.js'

const params = reactive(createDefaultParams())
const shaderRef = ref(null)

function onReplay() {
  shaderRef.value?.replay()
}
</script>

<style scoped>
.app-root {
  width: 100%;
  height: 100%;
  position: relative;
}
</style>