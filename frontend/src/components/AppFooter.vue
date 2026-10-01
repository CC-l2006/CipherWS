<template>
  <footer class="app-footer">
    <span>© {{ year }} CipherWS · 池禾</span>
  </footer>
</template>

<script setup>
const year = new Date().getFullYear()
</script>

<style scoped>
  .app-footer {
    display: flex;
    justify-content: center;
    padding: 16px 0 24px;
    font-size: 13px;
    color: #b4b4b4;
    user-select: none;
  }
</style>
