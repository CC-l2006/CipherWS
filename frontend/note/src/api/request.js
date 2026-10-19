/**
 * 统一请求层
 *
 * 直接打真实后端：同源 /api/**，由 nginx 反代到 note-gateway（8083）。
 *
 * 返回约定沿用 cipherws 后端已有的信封格式：
 *   { success: true, data, total }
 *   { success: false, errMessage }
 * 失败统一抛 ApiError，页面只管 try/catch，不用判断 success。
 *
 * 以前这里还有一套「mock 模式」（数据存 localStorage、不依赖后端），
 * 现在项目要真上线上，已经整体删掉了 —— 演示数据和真实数据混在一个入口里，
 * 很容易出现「本地看着好好的、线上是另一套逻辑」的问题。
 */

const BASE = String(import.meta.env.VITE_API_BASE_URL || '')

/**
 * 所有接口都以 /api 开头（网关就是按这个前缀分流的）。
 *
 * api/index.js 里写的是业务路径（'/auth/login'、'/notes'），
 * 前缀在这里统一拼 —— 之前这是个真 bug：路径没带 /api，
 * mock 模式下看不出来（mock 直接按 path 匹配），
 * 一切到真实后端就打到 /auth/login 上，Vite 返回 404 而不是接口响应。
 */
const API_PREFIX = '/api'

export class ApiError extends Error {
  constructor(message, code) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = code
  }
}

/** 登录态令牌的存取 */
export const TOKEN_KEY = 'note_token'

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || ''
  } catch {
    return ''
  }
}

/** 统一的响应解析：把 HTTP 状态 + 业务信封收敛成 {data,total} 或 ApiError */
async function parseResponse(res) {
  let json
  try {
    json = await res.json()
  } catch {
    throw new ApiError(`响应不是合法 JSON（HTTP ${res.status}）`)
  }
  if (!res.ok || json.success !== true) {
    throw new ApiError(json.errMessage || `请求失败（HTTP ${res.status}）`, res.status)
  }
  return { data: json.data, total: json.total }
}

function authHeader() {
  return getToken() ? { Authorization: `Bearer ${getToken()}` } : {}
}

/**
 * @param {string} path      以 / 开头的接口路径，例如 '/notes'
 * @param {object} [options]
 * @param {string} [options.method]
 * @param {object} [options.params] 查询参数，undefined / null / '' 会被剔除
 * @param {object} [options.body]   请求体
 * @returns {Promise<{data:any,total?:number}>}
 */
export async function request(path, { method = 'GET', params, body } = {}) {
  const cleanParams = {}
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== null && v !== '') cleanParams[k] = v
    }
  }

  const qs = new URLSearchParams(cleanParams).toString()
  const url = `${BASE}${API_PREFIX}${path}${qs ? `?${qs}` : ''}`

  let res
  try {
    res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json', ...authHeader() },
      body: body === undefined ? undefined : JSON.stringify(body)
    })
  } catch {
    throw new ApiError('网络请求失败，请检查连接')
  }
  return parseResponse(res)
}

/**
 * 上传文件（multipart/form-data）。
 *
 * **绝不能手写 Content-Type** —— 必须让浏览器自动带上
 * `multipart/form-data; boundary=...`，自己写会丢掉 boundary，后端直接解析失败。
 */
export async function upload(path, file) {
  const form = new FormData()
  form.append('file', file)

  let res
  try {
    res = await fetch(`${BASE}${API_PREFIX}${path}`, {
      method: 'POST',
      headers: authHeader(),
      body: form
    })
  } catch {
    throw new ApiError('网络请求失败，请检查连接')
  }
  return parseResponse(res)
}

export default request
