import router from './index'
import { useUserStore } from '@/stores/user' // Pinia store

const WHITE_LIST = ['/login', '/404']

// 全局前置守卫
router.beforeEach(async (to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - My App` : 'My App'

  const userStore = useUserStore()

  // 白名单直接放行
  if (WHITE_LIST.includes(to.path)) {
    return next()
  }

  // 未登录
  if (!userStore.token) {
    return next({
      path: '/login',
      query: { redirect: to.fullPath }
    })
  }

  // 已登录但未获取用户信息
  if (!userStore.userInfo) {
    try {
      await userStore.getUserInfo()
      return next({ ...to, replace: true })
    } catch (err) {
      await userStore.logout()
      return next('/login')
    }
  }

  next()
})

// 全局后置钩子
router.afterEach((to) => {
  // 关闭 loading、埋点等
  console.log('路由跳转完成:', to.path)
})

// 路由错误处理
router.onError((error) => {
  console.error('路由错误:', error)
})