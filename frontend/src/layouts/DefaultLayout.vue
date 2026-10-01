<template>
  <div class="layout-default">
    <AppHeader />
    <NavMenu />
    <main class="layout-body">
      <router-view />
    </main>
    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
import AppFooter from '@/components/AppFooter.vue'
import NavMenu from '@/components/NavMenu.vue'
</script>

<style scoped>
  .layout-default {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
  }

  .layout-body {
    flex: 1;
    display: flex;
    flex-direction: column;
  }
</style>
