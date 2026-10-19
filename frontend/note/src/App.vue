<template>
  <a class="skip-link" href="#main-content">跳到正文</a>

  <RouterView />

  <AppToast />
</template>

<script setup>
import { RouterView } from 'vue-router'
import AppToast from '@/components/AppToast.vue'
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
</style>
