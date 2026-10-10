import { createRouter, createWebHistory } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'

/**
 * 路由核心
 * 目前只有两个页面：首页 + 一个占位页（导航链接的目标）。
 * 新增页面时在这里加一条即可，布局统一挂在 DefaultLayout 下
 * （全屏无头尾的页面可以另建一个 BlankLayout 分组）。
 */
export const ROUTES = [
  {
    path: '/',
    component: DefaultLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/Home.vue'),
        // depth 越大表示越"深"。DefaultLayout 用它决定切换动画方向：
        // 去更深的页面（首页 -> 链接）向左滑动，返回时向右滑动。
        meta: { title: '首页', depth: 0 }
      },
      {
        path: 'link',
        name: 'link',
        component: () => import('@/views/Link.vue'),
        meta: { title: '链接', depth: 1 }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: ROUTES
})

router.afterEach((to) => {
  if (to.meta && to.meta.title) {
    document.title = `${to.meta.title} | CipherWS`
  }
})

export default router
