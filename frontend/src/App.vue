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

    /* 默认只显示一个小圆点/缩略状态。
       圆角用 999px 而不是 50%：50% 是百分比、999px 是像素，两者之间浏览器
       会按 calc 插值（实测出现 calc(54.37% - 87.31px)），在缩回中途宽度变小
       的瞬间算出比"半圆所需半径"更小的值，四个角就会方掉。
       统一用 999px 后插值恒定，任意宽度都收成完美半圆。 */
    width: 56px;
    height: 56px;
    border-radius: 999px;
    overflow: hidden;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
    transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
    cursor: pointer;

    /* 这里不要用 :deep(#music-box) 去覆盖内部组件：
       本 <style> 没有 scoped，:deep() 不会被 Vue 编译，会被当成普通 CSS 原样输出，
       浏览器识别不了这个选择器 → 规则静默失效（且不报错，很难发现）。
       内部 #music-box 自身是 width/height:100% + border-radius:inherit，
       会跟随这里的外层尺寸和圆角，无需覆盖。 */
  }

  /* 悬停展开：胶囊形，高度保持与默认悬浮球一致。
     没有全屏形态了，所以只有「圆形」和「胶囊」两种状态，圆角恒为 999px。 */
  #music-wrapper:hover {
    width: 348px;
    height: 56px;
    border-radius: 999px;
    overflow: hidden;
    box-shadow: 0 8px 28px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.08);
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
      height: 48px;
    }
  }
</style>
