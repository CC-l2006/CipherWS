<template>
  <div class="control-wrapper" :class="{ collapsed: isCollapsed }">
    <button class="toggle-btn" @click="isCollapsed = !isCollapsed" :title="isCollapsed ? '展开面板' : '收起面板'">
      <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <line v-if="isCollapsed" x1="12" y1="5" x2="12" y2="19" />
        <line v-if="isCollapsed" x1="5" y1="12" x2="19" y2="12" />
        <line v-if="!isCollapsed" x1="18" y1="6" x2="6" y2="18" />
        <line v-if="!isCollapsed" x1="6" y1="6" x2="18" y2="18" />
      </svg>
    </button>

    <div v-show="!isCollapsed" class="panel">
      <h3 class="panel-title">参数控制</h3>

      <div class="slider-group" v-for="s in sliders" :key="s.key">
        <label class="slider-label">
          <span class="slider-name">{{ s.label }}</span>
          <span class="slider-val">{{ formatVal(s.key) }}</span>
        </label>
        <input
          type="range"
          :min="s.min"
          :max="s.max"
          :step="s.step"
          :value="params[s.key]"
          @input="onChange(s.key, $event.target.value)"
        />
      </div>

      <div class="actions">
        <button class="action-btn primary" @click="resetAll">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 4v6h6M23 20v-6h-6"/><path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"/></svg>
          恢复默认
        </button>
        <button class="action-btn" @click="$emit('replay')">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>
          重播开场
        </button>
      </div>

      <div class="footer-note">
        云间列车 · Up in the CloudSea<br />
        Vibe Coding 大赏 · 程序化渲染
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { createDefaultParams } from '../composables/useShaderRenderer.js'

const props = defineProps({
  params: { type: Object, required: true },
})

defineEmits(['replay'])

const isCollapsed = ref(false)

const sliders = [
  { key: 'openingDur', label: '开场时长', min: 0.5,  max: 10,   step: 0.1,  unit: 's' },
  { key: 'speed',       label: '行进速度', min: 0,     max: 3,    step: 0.01, unit: '×' },
  { key: 'undulation',  label: '云层起伏', min: 0,     max: 2.5,  step: 0.01, unit: '×' },
  { key: 'noiseDetail', label: '噪声细节', min: 1,     max: 8,    step: 1,    unit: '层' },
  { key: 'exposure',    label: '曝光亮度', min: 0.2,   max: 2.5,  step: 0.01, unit: '×' },
  { key: 'zoom',        label: '视角缩放', min: 0.5,   max: 2.0,  step: 0.01, unit: '×' },
]

const fmtMap = {
  openingDur: (v) => Number(v).toFixed(1) + 's',
  noiseDetail: (v) => String(Math.round(v)) + '层',
  default: (v) => Number(v).toFixed(2) + '×',
}
function formatVal(key) {
  const v = props.params[key]
  const fn = fmtMap[key] || fmtMap.default
  return fn(v)
}

function onChange(key, val) {
  props.params[key] = parseFloat(val)
}

const defaults = createDefaultParams()
function resetAll() {
  Object.assign(props.params, defaults)
}
</script>

<style scoped>
.control-wrapper {
  position: fixed;
  right: 12px;
  top: 12px;
  z-index: 100;
  font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.toggle-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(255,255,255,0.15);
  border-radius: 8px;
  background: rgba(20,20,30,0.75);
  backdrop-filter: blur(12px);
  color: rgba(255,255,255,0.8);
  cursor: pointer;
  margin-left: auto;
  transition: background 0.15s;
}
.toggle-btn:hover {
  background: rgba(40,40,60,0.8);
}

.panel {
  margin-top: 8px;
  width: 260px;
  padding: 16px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 12px;
  background: rgba(18,18,28,0.82);
  backdrop-filter: blur(16px);
  color: rgba(255,255,255,0.85);
  box-shadow: 0 8px 32px rgba(0,0,0,0.4);
  transition: opacity 0.2s;
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin: 0 0 14px 0;
  color: rgba(255,255,255,0.4);
}

.slider-group {
  margin-bottom: 12px;
}
.slider-group:last-of-type {
  margin-bottom: 0;
}

.slider-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 4px;
}
.slider-name {
  color: rgba(255,255,255,0.6);
}
.slider-val {
  color: rgba(255,255,255,0.35);
  font-variant-numeric: tabular-nums;
}

input[type="range"] {
  width: 100%;
  height: 4px;
  -webkit-appearance: none;
  appearance: none;
  background: rgba(255,255,255,0.12);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
}
input[type="range"]::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: rgba(255,255,255,0.7);
  border: 2px solid rgba(0,0,0,0.3);
  cursor: pointer;
}
input[type="range"]::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: rgba(255,255,255,0.7);
  border: 2px solid rgba(0,0,0,0.3);
  cursor: pointer;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 4px;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: 8px;
  background: rgba(255,255,255,0.06);
  color: rgba(255,255,255,0.7);
  font-size: 11px;
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}
.action-btn:hover {
  background: rgba(255,255,255,0.12);
}
.action-btn.primary {
  border-color: rgba(255,180,100,0.3);
  color: rgba(255,180,100,0.85);
}
.action-btn.primary:hover {
  background: rgba(255,180,100,0.12);
}

.footer-note {
  margin-top: 14px;
  font-size: 10px;
  color: rgba(255,255,255,0.2);
  text-align: center;
  line-height: 1.5;
}

/* 移动端适配 */
@media (max-width: 480px) {
  .control-wrapper {
    right: 6px;
    top: auto;
    bottom: 6px;
  }
  .panel {
    width: calc(100vw - 24px);
    max-width: 320px;
    padding: 12px;
  }
  .panel-title {
    font-size: 12px;
  }
  .slider-label {
    font-size: 11px;
  }
}
</style>