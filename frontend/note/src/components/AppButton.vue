<template>
  <button
    class="btn"
    :class="[`btn--${variant}`, `btn--${size}`, { 'is-loading': loading, 'is-block': block }]"
    :type="type"
    :disabled="disabled || loading"
  >
    <span v-if="loading" class="spinner" aria-hidden="true" />
    <slot />
  </button>
</template>

<script setup>
defineProps({
  /** primary=主行动 / ghost=次要 / quiet=纯文字 / danger=危险 */
  variant: { type: String, default: 'ghost' },
  size: { type: String, default: 'md' },
  type: { type: String, default: 'button' },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  block: { type: Boolean, default: false }
})
</script>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--sp-2);
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  font-family: var(--font-sans);
  font-size: var(--fs-sm);
  line-height: 1;
  cursor: pointer;
  white-space: nowrap;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.btn--md {
  padding: 9px 16px;
}

.btn--sm {
  padding: 6px 11px;
  font-size: var(--fs-xs);
}

.btn--lg {
  padding: 12px 22px;
  font-size: var(--fs-base);
}

.btn--block {
  width: 100%;
}

/* 主行动：墨蓝实心 */
.btn--primary {
  background: var(--accent);
  border-color: var(--accent);
  color: var(--ink-inverse);
}

.btn--primary:hover:not(:disabled) {
  background: var(--accent-hover);
  border-color: var(--accent-hover);
}

.btn--primary:active:not(:disabled) {
  background: var(--accent-press);
}

/* 次要：纸上的描边，没有填充 */
.btn--ghost {
  background: transparent;
  border-color: var(--rule-strong);
  color: var(--ink-2);
}

.btn--ghost:hover:not(:disabled) {
  border-color: var(--ink-3);
  color: var(--ink);
  background: var(--paper-deep);
}

/* 纯文字：用于列表行内操作 */
.btn--quiet {
  background: transparent;
  color: var(--accent);
  padding-left: 4px;
  padding-right: 4px;
}

.btn--quiet:hover:not(:disabled) {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.btn--danger {
  background: transparent;
  border-color: var(--rule-strong);
  color: var(--danger);
}

.btn--danger:hover:not(:disabled) {
  border-color: var(--danger);
  background: var(--danger-soft);
}

/* 加载态用一个小墨点呼吸，不用旋转圈 */
@keyframes pulse-dot {
  0%, 100% { opacity: 0.25; }
  50% { opacity: 1; }
}

.spinner {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  animation: pulse-dot 1s ease-in-out infinite;
}
</style>
