<template>
  <Teleport to="body">
    <div v-if="modelValue" class="modal" role="dialog" aria-modal="true" :aria-label="title">
      <div class="modal__scrim" @click="onScrim" />
      <div ref="panelRef" class="modal__panel" :style="{ width: width }">
        <header class="modal__head">
          <h2 class="modal__title">{{ title }}</h2>
          <button class="modal__close" type="button" aria-label="关闭" @click="close">×</button>
        </header>

        <div class="modal__body">
          <slot />
        </div>

        <footer v-if="$slots.footer" class="modal__foot">
          <slot name="footer" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: String, default: '480px' },
  closeOnScrim: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'close'])
const panelRef = ref(null)
let lastFocused = null

function close() {
  emit('update:modelValue', false)
  emit('close')
}

function onScrim() {
  if (props.closeOnScrim) close()
}

function onKeydown(e) {
  if (e.key === 'Escape') close()
}

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      lastFocused = document.activeElement
      document.addEventListener('keydown', onKeydown)
      // 打开后把焦点送进面板，键盘用户不会被留在弹窗背后
      await nextTick()
      const focusable = panelRef.value?.querySelector(
        'input, textarea, select, button:not(.modal__close)'
      )
      ;(focusable || panelRef.value)?.focus?.()
    } else {
      document.removeEventListener('keydown', onKeydown)
      lastFocused?.focus?.()
    }
  }
)

onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--sp-4);
}

.modal__scrim {
  position: absolute;
  inset: 0;
  background: rgba(34, 31, 27, 0.42);
}

.modal__panel {
  position: relative;
  max-width: 100%;
  max-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  background: var(--sheet);
  border: 1px solid var(--rule);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-pop);
  outline: none;
}

.modal__panel:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-4) var(--sp-5);
  border-bottom: 1px solid var(--rule);
}

.modal__title {
  font-size: var(--fs-lg);
}

.modal__close {
  border: 0;
  background: none;
  font-size: 22px;
  line-height: 1;
  padding: 0 4px;
  color: var(--ink-3);
  cursor: pointer;
}

.modal__close:hover {
  color: var(--ink);
}

.modal__body {
  padding: var(--sp-5);
  overflow-y: auto;
}

.modal__foot {
  display: flex;
  justify-content: flex-end;
  gap: var(--sp-2);
  padding: var(--sp-3) var(--sp-5);
  border-top: 1px solid var(--rule);
  background: var(--paper);
  border-radius: 0 0 var(--r-md) var(--r-md);
}
</style>
