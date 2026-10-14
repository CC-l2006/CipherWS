import { createRouter, createWebHistory, createWebHashHistory } from 'vue-router'
import { constantRoutes } from './routes'

const router = createRouter({
  // 使用 HTML5 历史模式
  history: createWebHistory(import.meta.env.BASE_URL),
  // 如果需要 hash 模式，改用：
  // history: createWebHashHistory(),

  routes: constantRoutes,

  // 滚动行为
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    return { top: 0, behavior: 'smooth' }
  }
})

export default router