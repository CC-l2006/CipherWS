<template>
  <span class="tag" :class="[`tag--${tone}`, { 'is-static': !closable }]">
    <slot />
    <button v-if="closable" class="tag__close" type="button" :aria-label="`移除 ${label}`" @click.stop="$emit('close')">
      ×
    </button>
  </span>
</template>

<script setup>
defineProps({
  /** neutral / accent / ok / warn / danger */
  tone: { type: String, default: 'neutral' },
  closable: { type: Boolean, default: false },
  label: { type: String, default: '' }
})

defineEmits(['close'])
</script>

<style scoped>
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 1px 8px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  font-size: var(--fs-xs);
  line-height: 1.6;
  color: var(--ink-2);
  background: transparent;
  white-space: nowrap;
}

.tag--accent {
  border-color: transparent;
  background: var(--accent-soft);
  color: var(--accent-ink);
}

.tag--ok {
  border-color: transparent;
  background: var(--ok-soft);
  color: var(--ok);
}

.tag--warn {
  border-color: transparent;
  background: var(--warn-soft);
  color: var(--warn);
}

.tag--danger {
  border-color: transparent;
  background: var(--danger-soft);
  color: var(--danger);
}

.tag__close {
  border: 0;
  background: none;
  padding: 0 0 0 2px;
  margin: 0;
  font-size: 14px;
  line-height: 1;
  color: currentColor;
  opacity: 0.5;
  cursor: pointer;
}

.tag__close:hover {
  opacity: 1;
}
</style>
