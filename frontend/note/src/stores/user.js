import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, userApi } from '@/api/index.js'
import { getToken, TOKEN_KEY } from '@/api/request.js'

/**
 * 用户状态。
 *
 * 上一版这里是写死的 mock（mock-token-xxx + 固定 roles），现在接到 api/index.js，
 * 真实后端就绪后**本文件一行都不用改** —— 只要后端按 api/index.js 里的契约实现。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const userInfo = ref(null)

  const isLogin = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value?.role === 'admin')
  const aiQuota = computed(() => userInfo.value?.aiQuota ?? 0)
  const displayName = computed(
    () => userInfo.value?.nickname || userInfo.value?.username || '未登录'
  )

  function setToken(value) {
    token.value = value || ''
    try {
      if (value) localStorage.setItem(TOKEN_KEY, value)
      else localStorage.removeItem(TOKEN_KEY)
    } catch {
      /* 隐私模式忽略 */
    }
  }

  function setUserInfo(info) {
    userInfo.value = info || null
  }

  /** 登录：邮箱 + 密码（2026-10-20 起账号标识是邮箱，不再是用户名） */
  async function login({ email, password }) {
    const { data } = await authApi.login({ email, password })
    setToken(data.token)
    setUserInfo(data.user)
    return data.user
  }

  /** 注册：邮箱 + 验证码 + 密码（+ 可选昵称），账号名由后端生成 */
  async function register({ email, code, password, nickname }) {
    const { data } = await authApi.register({ email, code, password, nickname })
    return data
  }

  /** 路由守卫里调用：刷新页面后凭 token 恢复登录态 */
  async function fetchUserInfo() {
    const { data } = await userApi.profile()
    setUserInfo(data)
    return data
  }

  /** 局部更新（改资料、AI 额度扣减后同步） */
  function patchUserInfo(patch) {
    if (!userInfo.value) return
    userInfo.value = { ...userInfo.value, ...patch }
  }

  async function logout() {
    try {
      if (token.value) await authApi.logout()
    } catch {
      /* 退出失败也要清本地状态 */
    }
    setToken('')
    setUserInfo(null)
  }

  return {
    token,
    userInfo,
    isLogin,
    isAdmin,
    aiQuota,
    displayName,
    setToken,
    setUserInfo,
    patchUserInfo,
    login,
    register,
    fetchUserInfo,
    logout
  }
})
