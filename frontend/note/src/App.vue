<script setup>
// 根组件只负责渲染当前路由匹配到的页面
// 注意：原来的 HelloWorld 展示已由 /views/Home.vue 承担
</script>

<template>
  <router-view />
</template>

<style>
#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>
