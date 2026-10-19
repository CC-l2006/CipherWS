<template>
  <Teleport to="body">
    <div class="toasts" aria-live="polite" aria-atomic="false">
      <TransitionGroup name="toast">
        <div
          v-for="t in toast.items"
          :key="t.id"
          class="toast"
          :class="`toast--${t.type}`"
          role="status"
        >
          <span class="toast__mark" aria-hidden="true" />
          <span class="toast__msg">{{ t.message }}</span>
          <button class="toast__close" type="button" aria-label="关闭提示" @click="toast.dismiss(t.id)">
            ×
          </button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<script setup>
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()
</script>

<style scoped>
.toasts {
  position: fixed;
  /* 放底部居中：顶部会压住编辑区工具栏和页标题（实测会挡掉「AI 总结」「删除」按钮），
     底部一次性提示更不容易遮住正在操作的东西。 */
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  display: flex;
  flex-direction: column-reverse;
  gap: var(--sp-2);
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: flex-start;
  gap: var(--sp-2);
  min-width: 220px;
  max-width: 420px;
  padding: 10px 12px;
  background: var(--sheet);
  border: 1px solid var(--rule-strong);
  border-left-width: 3px;
  border-radius: var(--r-sm);
  box-shadow: var(--shadow-pop);
  font-size: var(--fs-sm);
  color: var(--ink);
  pointer-events: auto;
}

.toast--success { border-left-color: var(--ok); }
.toast--error   { border-left-color: var(--danger); }
.toast--warn    { border-left-color: var(--warn); }
.toast--info    { border-left-color: var(--accent); }

.toast__mark {
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 7px;
  border-radius: 50%;
  background: var(--ink-3);
}

.toast--success .toast__mark { background: var(--ok); }
.toast--error .toast__mark   { background: var(--danger); }
.toast--warn .toast__mark    { background: var(--warn); }
.toast--info .toast__mark    { background: var(--accent); }

.toast__msg {
  flex: 1;
  line-height: 1.55;
  word-break: break-word;
}

.toast__close {
  flex: none;
  border: 0;
  background: none;
  padding: 0 2px;
  font-size: 16px;
  line-height: 1;
  color: var(--ink-3);
  cursor: pointer;
}

.toast__close:hover { color: var(--ink); }

.toast-enter-active,
.toast-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}
</style>
