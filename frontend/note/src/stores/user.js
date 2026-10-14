import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const TOKEN_KEY = 'note_token'

/**
 * 用户状态（占位实现）
 * 目前使用本地 mock 数据，接入真实后端时把 requestLogin / requestUserInfo
 * 换成你的 axios 请求即可，其余逻辑不用改。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref(null)

  const isLogin = computed(() => !!token.value)
  const roles = computed(() => userInfo.value?.roles || [])

  function setToken(value) {
    token.value = value || ''
    if (value) {
      localStorage.setItem(TOKEN_KEY, value)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }
  }

  function setUserInfo(info) {
    userInfo.value = info
  }

  // 登录：接入后端后替换为真实接口调用
  async function login({ username, password }) {
    if (!username || !password) {
      throw new Error('请输入用户名和密码')
    }
    // TODO: 替换为真实登录接口
    const mockToken = `mock-token-${username}`
    setToken(mockToken)
    setUserInfo({ username, nickname: username, roles: ['admin'] })
    return userInfo.value
  }

  // 获取用户信息：接入后端后替换为真实接口调用
  async function getUserInfo() {
    if (!token.value) {
      throw new Error('未登录')
    }
    // TODO: 替换为真实用户信息接口
    if (!userInfo.value) {
      userInfo.value = {
        username: 'admin',
        nickname: '管理员',
        roles: ['admin']
      }
    }
    return userInfo.value
  }

  async function logout() {
    // TODO: 接入后端后调用退出登录接口
    setToken('')
    setUserInfo(null)
  }

  return {
    token,
    userInfo,
    isLogin,
    roles,
    setToken,
    setUserInfo,
    login,
    getUserInfo,
    logout
  }
})
