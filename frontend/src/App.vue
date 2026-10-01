<template>
  <router-view />
</template>

<script setup>
</script>

<style>
  #app {
    min-height: 100vh;
    background-image: url('@/assets/bac/bing.jpg');
    background-size: cover;
    background-position: center;
    background-repeat: no-repeat;
    background-attachment: fixed;
  }
</style>

<style>
  /* ===== 音乐组件固定在左下角（原 App.vue 样式，随组件一起下移） ===== */
  #music-wrapper {
    position: fixed;
    bottom: 30px;
    left: 30px;
    z-index: 999;

    /* 默认只显示一个小圆点/缩略状态 */
    width: 56px;
    height: 56px;
    border-radius: 50%;
    overflow: hidden;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
    cursor: pointer;

    /* 让内部音乐组件填满 */
    :deep(#music-box) {
      width: 100%;
      height: 100%;
      border-radius: 50%;
      overflow: hidden;
    }
  }

  /* 悬停时展开显示完整卡片 */
  #music-wrapper:hover {
    width: 380px;
    height: 160px;
    border-radius: 24px;
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.08);

    :deep(#music-box) {
      border-radius: 24px;
      width: 380px;
      height: 160px;
      flex-direction: column;
      align-items: stretch;
      gap: 0;
      padding: 12px 0;
    }
  }

  /* 响应式调整 - 小屏幕下缩小间距 */
  @media (max-width: 600px) {
    #music-wrapper {
      bottom: 16px;
      left: 16px;
      width: 48px;
      height: 48px;
    }

    #music-wrapper:hover {
      width: 300px;
      height: 140px;

      :deep(#music-box) {
        width: 300px;
        height: 140px;
      }
    }
  }
</style>
