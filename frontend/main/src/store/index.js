import { reactive } from 'vue'

/**
 * 全局状态（轻量实现，未引入 Pinia）
 * user: 登录用户信息（登录接口就绪后由登录页写入）
 * 页面切换已交给 vue-router（见 src/router/index.js），此处不再保存导航状态
 */
export const state = reactive({
  user: null
})

export function setUser (user) {
  state.user = user
}

export function clearUser () {
  state.user = null
}
