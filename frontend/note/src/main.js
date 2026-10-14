import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './router/guards.js'

const app = createApp(App)

// pinia 必须在 router 之前注册：
// guards.js 里的路由守卫会调用 useUserStore()
app.use(createPinia())
app.use(router)
app.mount('#app')
