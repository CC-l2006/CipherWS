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
    /* 底部再叠加安全区高度：iPhone 的横条会压住页脚文字 */
    padding: 16px 16px calc(24px + env(safe-area-inset-bottom, 0px));
    font-size: 13px;
    color: #b4b4b4;
    user-select: none;
  }

  /* 手机竖屏：页脚字号略收，避免换行 */
  @media (max-width: 600px) {
    .app-footer {
      padding: 12px 16px calc(20px + env(safe-area-inset-bottom, 0px));
      font-size: 12px;
    }
  }
</style>
