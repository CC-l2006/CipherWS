/**
 * 统一请求层
 *
 * 两种模式，由 .env 的 VITE_API_MODE 决定（构建时内联）：
 *   mock —— 走内置 mock（src/api/mock），数据存 localStorage，不需要任何后端。
 *           刻意用**动态 import**：http 模式下这块代码根本不会加载。
 *   http —— 走真实后端，同源 /api/**，由 nginx 反代到 note-gateway。
 *
 * 返回约定沿用 cipherws 后端已有的信封格式：
 *   { success: true, data, total }
 *   { success: false, errMessage }
 * 失败统一抛 ApiError，页面只管 try/catch，不用判断 success。
 */

const MODE = String(import.meta.env.VITE_API_MODE || 'mock').toLowerCase()
const BASE = String(import.meta.env.VITE_API_BASE_URL || '')

/** 当前是否为 mock 模式（页面顶部会据此显示一枚「演示数据」标记） */
export const isMock = MODE !== 'http'

export class ApiError extends Error {
  constructor(message, code) {
    super(message || '请求失败')
    this.name = 'ApiError'
    this.code = code
  }
}

/** 登录态令牌的存取：mock 与 http 共用同一个 key */
export const TOKEN_KEY = 'note_token'

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || ''
  } catch {
    return ''
  }
}

// mock 模块只在真正需要时加载一次
let mockModule = null
async function loadMock() {
  if (!mockModule) {
    mockModule = await import('./mock/index.js')
  }
  return mockModule
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

/** mock 模式下的统一出口：不为 true 一律抛错 */
function unwrapMock(res) {
  if (!res || res.success !== true) {
    throw new ApiError(res?.errMessage, res?.code)
  }
  return { data: res.data, total: res.total }
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

  // 这里直接比较 import.meta.env，而不是复用上面的 isMock 常量：
  // Vite 会把 import.meta.env.VITE_API_MODE 内联成字面量，于是这一行在
  // http 模式下会折叠成 `if (false)`，整个 mock 模块由 rollup 直接摇掉
  // —— 生产产物里连懒加载 chunk 都不会剩下（复用常量则无法折叠）。
  if (import.meta.env.VITE_API_MODE !== 'http') {
    const { mockRequest } = await loadMock()
    return unwrapMock(await mockRequest({
      method: method.toUpperCase(),
      path,
      params: cleanParams,
      body
    }))
  }

  const qs = new URLSearchParams(cleanParams).toString()
  const url = `${BASE}${path}${qs ? `?${qs}` : ''}`

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
 * http 模式下**绝不能手写 Content-Type** —— 必须让浏览器自动带上
 * `multipart/form-data; boundary=...`，自己写会丢掉 boundary，后端直接解析失败。
 *
 * mock 模式没有后端，把文件读成 data URL 交给 mock 存进 localStorage。
 */
export async function upload(path, file) {
  if (import.meta.env.VITE_API_MODE !== 'http') {
    const { mockRequest } = await loadMock()
    const dataUrl = await readAsDataUrl(file)
    return unwrapMock(await mockRequest({
      method: 'POST',
      path,
      params: {},
      body: { dataUrl, name: file.name, type: file.type, size: file.size }
    }))
  }

  const form = new FormData()
  form.append('file', file)

  let res
  try {
    res = await fetch(`${BASE}${path}`, {
      method: 'POST',
      // 注意这里没有 Content-Type，见方法注释
      headers: authHeader(),
      body: form
    })
  } catch {
    throw new ApiError('网络请求失败，请检查连接')
  }
  return parseResponse(res)
}

function readAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new ApiError('读取图片失败，请重试'))
    reader.readAsDataURL(file)
  })
}

export default request
