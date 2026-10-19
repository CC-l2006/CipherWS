<template>
  <a class="skip-link" href="#main-content">跳到正文</a>

  <RouterView />

  <AppToast />

  <!-- 演示数据标记：明确告诉使用者当前数据来自浏览器本地 mock，不是真实后端 -->
  <p v-if="isMock" class="mock-badge" title="当前使用内置演示数据，数据存放在浏览器 localStorage">
    演示数据
  </p>
</template>

<script setup>
import { RouterView } from 'vue-router'
import AppToast from '@/components/AppToast.vue'
import { isMock } from '@/api/request.js'
</script>

<style scoped>
.skip-link {
  position: absolute;
  left: -9999px;
  top: 0;
  z-index: 1001;
  padding: 8px 14px;
  background: var(--ink);
  color: var(--ink-inverse);
  border-radius: 0 0 var(--r-sm) 0;
}

.skip-link:focus {
  left: 0;
}

.mock-badge {
  position: fixed;
  right: 10px;
  bottom: 8px;
  z-index: 800;
  padding: 2px 8px;
  border: 1px dashed var(--rule-strong);
  border-radius: var(--r-pill);
  background: var(--paper);
  color: var(--ink-3);
  font-size: var(--fs-xs);
  letter-spacing: 0.04em;
  pointer-events: none;
}
</style>
