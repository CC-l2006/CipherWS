// 统一请求封装：基础地址来自环境变量（.env.development / .env.production 的 VITE_API_BASE_URL）
const BASE_URL = import.meta.env.VITE_API_BASE_URL

/**
 * 发起请求，并统一处理网络层错误
 * @param {{ url: string, method?: string, params?: Record<string, any> }} options
 * @returns {Promise<{ success: boolean, errMessage?: string, data?: any, total?: number }>}
 */
async function request({ url, method = 'GET', params } = {}) {
  let queryString = ''
  if (params) {
    queryString = '?' + new URLSearchParams(params).toString()
  }

  try {
    const res = await fetch(`${BASE_URL}${url}${queryString}`, { method })

    if (!res.ok) {
      return {
        success: false,
        errMessage: `请求失败: ${res.status} ${res.statusText}`,
        data: null
      }
    }

    return res.json()
  } catch (error) {
    return {
      success: false,
      errMessage: error.message || '网络请求失败',
      data: null
    }
  }
}

export default request
