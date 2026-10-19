import { createRouter, createWebHistory } from 'vue-router'
import { constantRoutes, asyncRoutes, notFoundRoute } from './routes.js'

const router = createRouter({
  // HTML5 历史模式：nginx 那边需要有 SPA 回退（try_files $uri $uri/ /index.html）
  history: createWebHistory(import.meta.env.BASE_URL),

  routes: [
    ...constantRoutes,
    ...asyncRoutes,
    notFoundRoute // 必须最后
  ],

  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    // 笔记列表换页时不希望整页滚回顶部，交给页面自己处理
    if (to.name === from.name && to.name === 'note-detail') return false
    return { top: 0 }
  }
})

export default router
