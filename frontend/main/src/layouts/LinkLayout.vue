<template>
  <div class="layout-blank">
    <router-view />
  </div>
</template>

<script setup>
</script>

<style scoped>
  .layout-blank {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
  }
</style>
