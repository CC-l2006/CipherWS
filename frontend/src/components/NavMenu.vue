<template>
  <nav class="nav-menu">
    <RouterLink
      v-for="item in items"
      :key="item.name"
      class="nav-item"
      :class="{ active: item.name === activeName }"
      :to="{ name: item.name }"
    >
      {{ item.label }}
    </RouterLink>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

// 二级页面可通过 meta.menu 指回它归属的一级菜单
const activeName = computed(() => route.meta.menu || route.name)

// 新增页面时在这里加一项即可（name 要和 router 里的 name 一致）
const items = [
  { name: 'home', label: '首页' },
  { name: 'link', label: '链接' }
]
</script>

<style scoped>
  .nav-menu {
    --nav-active-bg: #f2ebc7;   /* 选中态底色：奶米黄（云朵色） */
    --nav-active-text: #145c70; /* 选中态文字：深蓝，保证可读性 */
    display: flex;
    justify-content: center;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 24px;
  }

  .nav-item {
    padding: 6px 16px;
    font-size: 14px;
    color: #d9d9d9;
    text-decoration: none;
    background: rgba(255, 255, 255, 0.08);
    border: 1px solid rgba(255, 255, 255, 0.16);
    border-radius: 999px;
    cursor: pointer;
    transition: all 0.25s ease;
  }

  .nav-item:hover {
    color: #ffffff;
    background: rgba(255, 255, 255, 0.16);
  }

  /* 选中态：奶米黄底 + 深蓝文字。
     两者对比度约 6:1，高于 WCAG AA 对正文要求的 4.5:1。
     边框用同色系半透明深蓝，避免深色背景下奶米黄块边缘发虚。 */
  .nav-item.active {
    color: var(--nav-active-text);
    background: var(--nav-active-bg);
    border-color: rgba(20, 92, 112, 0.45);
  }

  /* 选中态悬停：保持同一配色，只轻微提亮，避免变回白字导致看不清 */
  .nav-item.active:hover {
    color: var(--nav-active-text);
    background: #f7f2d9;
    border-color: rgba(20, 92, 112, 0.65);
  }

  /* 键盘可达性：Tab 聚焦时给出清晰提示 */
  .nav-item:focus-visible {
    outline: 2px solid var(--nav-active-bg);
    outline-offset: 2px;
  }
</style>
