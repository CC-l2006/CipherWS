<template>
  <div class="empty">
    <!-- 用一枚「空白页」的线稿代替插画：与纸的主题一致，也不引入图片资源 -->
    <svg class="empty__art" viewBox="0 0 64 64" aria-hidden="true">
      <rect x="10" y="6" width="44" height="52" rx="3" fill="none" stroke="currentColor" stroke-width="1.5" />
      <path d="M18 18h28M18 26h28M18 34h20" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" opacity="0.55" />
      <path d="M10 6 L22 6 L10 18 Z" fill="currentColor" opacity="0.12" />
    </svg>
    <p class="empty__title">{{ title }}</p>
    <p v-if="description" class="empty__desc">{{ description }}</p>
    <div v-if="$slots.action" class="empty__action">
      <slot name="action" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '这里还是空的' },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  padding: var(--sp-10) var(--sp-6);
  text-align: center;
  color: var(--ink-3);
}

.empty__art {
  width: 56px;
  height: 56px;
  color: var(--rule-strong);
}

.empty__title {
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  color: var(--ink-2);
}

.empty__desc {
  font-size: var(--fs-sm);
  max-width: 34ch;
  line-height: 1.7;
}

.empty__action {
  margin-top: var(--sp-2);
}
</style>
