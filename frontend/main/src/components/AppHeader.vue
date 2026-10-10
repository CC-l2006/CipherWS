<template>
  <header class="app-header">
    <top-card />
  </header>
</template>

<script setup>
import topCard from '@/components/top-card/top-card.vue'
</script>

<style scoped>
  .app-header {
    display: flex;
    justify-content: center;
    padding-top: 24px;
  }
</style>
