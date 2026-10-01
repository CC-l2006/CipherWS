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

  .nav-item.active {
    color: #ffffff;
    border-color: rgba(255, 255, 255, 0.6);
    background: rgba(129, 110, 216, 0.65);
  }
</style>
