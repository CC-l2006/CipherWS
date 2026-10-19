import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router/index.js'
import { useToastStore } from '@/stores/toast.js'
import './styles/tokens.css'

// guards 必须在 router 之后引入：它内部会 import router 并注册 beforeEach
import './router/guards.js'

const app = createApp(App)

// pinia 必须在 router 之前注册：
// guards.js 里的路由守卫会调用 useUserStore()
app.use(createPinia())
app.use(router)

// 统一兜底：任何未捕获的异常都给用户一个明确提示，而不是白屏
app.config.errorHandler = (err) => {
  console.error('[app]', err)
  try {
    useToastStore().error(err?.message || '页面出错了，请刷新重试')
  } catch {
    /* pinia 还没就绪时忽略 */
  }
}

app.mount('#app')
