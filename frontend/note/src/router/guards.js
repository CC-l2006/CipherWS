import router from './index.js'
import { useUserStore } from '@/stores/user.js'
import { useToastStore } from '@/stores/toast.js'

const APP_NAME = '池禾笔记'

// 未登录也能访问的页面
const WHITE_LIST = ['/login', '/register', '/forgot-password']

router.beforeEach(async (to) => {
  // 浏览器标签页只显示站点名，不再拼页面标题前缀
  document.title = APP_NAME

  const userStore = useUserStore()
  const toast = useToastStore()

  // 已登录还去登录页 → 直接回首页，避免重复登录
  if (to.meta.guestOnly && userStore.isLogin) {
    return { path: '/' }
  }

  if (WHITE_LIST.includes(to.path) || to.meta.guestOnly) {
    return true
  }

  if (!userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 已登录但刷新页面后还没有用户信息 → 补拉一次
  if (!userStore.userInfo) {
    try {
      await userStore.fetchUserInfo()
    } catch {
      await userStore.logout()
      toast.warn('登录已失效，请重新登录')
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  // 角色校验（需求 4.1.4：普通用户无后台入口）
  const roles = to.meta.roles
  if (Array.isArray(roles) && roles.length) {
    const mine = userStore.userInfo?.role
    if (!roles.includes(mine)) {
      toast.error('该页面仅管理员可访问')
      return { path: '/' }
    }
  }

  return true
})

router.afterEach((to) => {
  // 页面切换后把焦点交给主区域，键盘与读屏用户不用重新 Tab 一遍导航
  requestAnimationFrame(() => {
    const main = document.getElementById('main-content')
    if (main) {
      main.setAttribute('tabindex', '-1')
      main.focus({ preventScroll: true })
    }
  })
})

router.onError((error) => {
  console.error('[router]', error)
})
