/**
 * CipherWS 前端静态服务（零依赖，只用 Node 内置模块）
 *
 * 一份脚本服务两个站点，各自指向自己的构建产物：
 *   frontend/main/dist     主站 → 默认 8000
 *   frontend/train/dist    云间列车 → 默认 8001
 * 两个站点用「不同端口」区分，直接对应 nginx 双 server 块的两个 root，
 * 备案通过后把 nginx 的 root 指到同样的目录即可，产物不用重排。
 *
 * 端口分配约定（见下方 SITES）：frontend/ 下的子项目按 8000 + 序号顺延，
 * main = 8000、train = 8001，以后新增的依次 8002 / 8003 …。
 * 注意这些端口只用于本机预览与局域网访问，**对外始终是 80/443 上的域名**，
 * 不靠端口号区分站点。
 *
 * 为什么需要它，而不是随便找个静态服务器：
 *   main 用的是 vue-router 的 createWebHistory（HTML5 history 模式），
 *   /link 这类路径在磁盘上并不存在对应文件。如果服务器找不到文件就返回 404，
 *   用户在 /link 页面按 F5 刷新、或直接粘贴网址访问，就会看到 404。
 *   所以必须做 SPA 回退：找不到的路径统一回落给 index.html，交给前端路由处理。
 *
 * 用法：
 *   node server.js --app main              # 主站，0.0.0.0:8000，服务 main/dist
 *   node server.js --app train             # 云间列车，0.0.0.0:8001，服务 train/dist
 *   node server.js --app main --port 80    # 换端口（80 需要 root 或 setcap）
 *   APP=train PORT=8001 node server.js     # 也支持环境变量写法
 *   ROOT=/opt/cipherws/frontend/main/dist node server.js
 *
 * nginx 双 server 块配置见 deploy/nginx/cipherws.conf。
 */

const http = require('node:http')
const fs = require('node:fs')
const path = require('node:path')
const { createGzip } = require('node:zlib')

// 站点注册表：新增子站点只在这里加一行，并在 frontend/<名字>/ 下建工程。
// 端口按 8000 + 序号顺延（main=8000 / train=8001 / 下一个 8002 …），
// 避开 8080（网关）、8081/8082（后端服务）、8848/8849（Nacos）。
const SITES = {
  main: { port: 8000, label: '主站' },
  train: { port: 8001, label: '云间列车' }
}

function readArg(name) {
  const i = process.argv.indexOf(`--${name}`)
  return i > -1 ? process.argv[i + 1] : undefined
}

// 命令行 --app 优先于环境变量 APP，缺省 main
const APP = String(readArg('app') || process.env.APP || 'main').toLowerCase()
if (!SITES[APP]) {
  console.error(`未知站点 "${APP}"，可选：${Object.keys(SITES).join(' / ')}`)
  process.exit(1)
}

const PORT = Number(readArg('port') || process.env.PORT || SITES[APP].port)
const HOST = readArg('host') || process.env.HOST || '0.0.0.0'
const ROOT = path.resolve(process.env.ROOT || path.join(__dirname, APP, 'dist'))

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
    `404 Not Found\n\n未找到 ${path.join(ROOT, 'index.html')}\n请先执行 npm run build:${APP} 生成 frontend/${APP}/dist/`
  )
})

server.listen(PORT, HOST, () => {
  console.log(`[${APP}] ${SITES[APP].label} 已启动: http://${HOST}:${PORT}`)
  console.log(`静态根目录: ${ROOT}`)
  if (!fs.existsSync(ROOT)) {
    console.warn(`[警告] 静态根目录不存在，请先在 frontend/ 下执行 npm run build:${APP}`)
  }
})
