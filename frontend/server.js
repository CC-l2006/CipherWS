/**
 * CipherWS 前端静态服务（零依赖，只用 Node 内置模块）
 *
 * 为什么需要它，而不是随便找个静态服务器：
 *   前端用的是 vue-router 的 createWebHistory（HTML5 history 模式），
 *   /link 这类路径在磁盘上并不存在对应文件。如果服务器找不到文件就返回 404，
 *   用户在 /link 页面按 F5 刷新、或直接粘贴网址访问，就会看到 404。
 *   所以必须做 SPA 回退：找不到的路径统一回落给 index.html，交给前端路由处理。
 *
 * 用法：
 *   node server.js                 # 默认 0.0.0.0:8000，服务同目录下的 dist/
 *   PORT=80 node server.js         # 换端口（80 需要 root 或 setcap）
 *   ROOT=/opt/cipherws/frontend/dist PORT=8000 node server.js
 */

const http = require('node:http')
const fs = require('node:fs')
const path = require('node:path')
const { createGzip } = require('node:zlib')

const PORT = Number(process.env.PORT || 8000)
const HOST = process.env.HOST || '0.0.0.0'
const ROOT = path.resolve(process.env.ROOT || path.join(__dirname, 'dist'))

// 带内容 hash 的构建产物可以永久缓存；index.html 绝不能缓存，
// 否则用户会一直拿到旧版本、指向已被删除的资源文件。
const IMMUTABLE = /\.(?:js|css|woff2?|ttf|eot|png|jpe?g|gif|svg|webp|ico|mp3|mp4|json)$/i

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.mp3': 'audio/mpeg',
  '.mp4': 'video/mp4',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8'
}

const TEXTY = /^(?:text\/|application\/(?:json|javascript)|image\/svg)/

function send(res, status, headers, body) {
  res.writeHead(status, headers)
  if (body === undefined) res.end()
  else res.end(body)
}

function serveFile(req, res, filePath, statusCode) {
  const ext = path.extname(filePath).toLowerCase()
  const type = MIME[ext] || 'application/octet-stream'
  const stat = fs.statSync(filePath)

  const headers = {
    'Content-Type': type,
    'Last-Modified': stat.mtime.toUTCString()
  }

  // 缓存策略：只有 dist/assets/ 下带内容 hash 的产物才敢长期 immutable；
  // public/ 里的原始文件（如 /tab-icon/icon.jpg）文件名不含 hash，
  // 一旦设成长期 immutable，以后替换图片用户一年都看不到更新。
  const inHashedAssets = path.relative(ROOT, filePath).split(path.sep)[0] === 'assets'
  if (ext === '.html') {
    headers['Cache-Control'] = 'no-cache'
  } else if (inHashedAssets && IMMUTABLE.test(filePath)) {
    headers['Cache-Control'] = 'public, max-age=31536000, immutable'
  } else {
    headers['Cache-Control'] = 'public, max-age=3600'
  }

  // 文本类资源做 gzip（体积通常能压到 1/3 左右）
  const acceptEncoding = String(req.headers['accept-encoding'] || '')
  const canGzip = TEXTY.test(type) && /\bgzip\b/.test(acceptEncoding)

  if (canGzip) {
    headers['Content-Encoding'] = 'gzip'
    headers['Vary'] = 'Accept-Encoding'
    res.writeHead(statusCode, headers)
    if (req.method === 'HEAD') return res.end()
    fs.createReadStream(filePath).pipe(createGzip()).pipe(res)
    return
  }

  headers['Content-Length'] = stat.size
  res.writeHead(statusCode, headers)
  if (req.method === 'HEAD') return res.end()
  fs.createReadStream(filePath).pipe(res)
}

const server = http.createServer((req, res) => {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    return send(res, 405, { 'Content-Type': 'text/plain; charset=utf-8', Allow: 'GET, HEAD' }, 'Method Not Allowed')
  }

  // 去掉查询串，解码 URL（处理中文文件名等情况）
  let pathname
  try {
    pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname)
  } catch {
    return send(res, 400, { 'Content-Type': 'text/plain; charset=utf-8' }, 'Bad Request')
  }

  // 防目录穿越：解析后必须仍在 ROOT 之内
  const resolved = path.resolve(ROOT, '.' + pathname)
  if (resolved !== ROOT && !resolved.startsWith(ROOT + path.sep)) {
    return send(res, 403, { 'Content-Type': 'text/plain; charset=utf-8' }, 'Forbidden')
  }

  // 1) 命中真实文件 → 直接返回
  try {
    if (fs.statSync(resolved).isFile()) {
      return serveFile(req, res, resolved, 200)
    }
  } catch {
    /* 文件不存在，继续往下走 */
  }

  // 2) 目录请求 → 尝试目录下的 index.html
  try {
    const indexInDir = path.join(resolved, 'index.html')
    if (fs.statSync(indexInDir).isFile()) {
      return serveFile(req, res, indexInDir, 200)
    }
  } catch {
    /* 忽略 */
  }

  // 3) SPA 回退：其余路径（/link、/foo/bar…）统一交给 index.html
  const spaIndex = path.join(ROOT, 'index.html')
  if (fs.existsSync(spaIndex)) {
    return serveFile(req, res, spaIndex, 200)
  }

  return send(
    res,
    404,
    { 'Content-Type': 'text/plain; charset=utf-8' },
    `404 Not Found\n\n未找到 ${ROOT}\\index.html\n请先执行 npm run build 生成 dist/`
  )
})

server.listen(PORT, HOST, () => {
  console.log(`CipherWS 前端已启动: http://${HOST}:${PORT}`)
  console.log(`静态根目录: ${ROOT}`)
  if (!fs.existsSync(ROOT)) {
    console.warn(`[警告] 静态根目录不存在，请先在 frontend/ 下执行 npm run build`)
  }
})
