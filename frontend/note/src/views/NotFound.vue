<template>
  <div class="notfound">
    <p class="notfound__code" aria-hidden="true">404</p>
    <h1 class="notfound__title">这一页是空白的</h1>
    <p class="notfound__desc">
      地址可能敲错了，或者这篇笔记已经被删除。回到笔记列表重新找找。
    </p>
    <div class="notfound__actions">
      <RouterLink to="/notes">
        <AppButton variant="primary">回到我的笔记</AppButton>
      </RouterLink>
    </div>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router'
import AppButton from '@/components/AppButton.vue'
</script>

<style scoped>
.notfound {
  min-height: 70vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--sp-3);
  padding: var(--sp-8) var(--sp-4);
  text-align: center;
}

.notfound__code {
  font-family: var(--font-serif);
  font-size: 72px;
  line-height: 1;
  color: var(--rule-strong);
  letter-spacing: 0.04em;
}

.notfound__title {
  font-size: var(--fs-xl);
}

.notfound__desc {
  max-width: 36ch;
  font-size: var(--fs-sm);
  line-height: 1.8;
  color: var(--ink-3);
}

.notfound__actions {
  margin-top: var(--sp-2);
}
</style>
