/**
 * mock 数据库：与《需求文档》第 5 节的 9 张表一一对应，落在 localStorage。
 *
 * 为什么要有它：前端可以在完全没有后端的情况下跑通全部 7 个模块（便于演示与
 * 独立开发），而且切换真实接口时页面代码一行都不用改 —— 见 api/request.js。
 *
 * ⚠️ 这里存的是**明文密码**，仅用于本地演示。真实后端必须不可逆加密（需求 6.2）。
 */

const STORAGE_KEY = 'cipherws_note_mock_db_v1'

const iso = (d) => new Date(d).toISOString()

/** 相对今天偏移 n 天，用于生成有层次的示例时间 */
function daysAgo(n, hour = 10, minute = 0) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  d.setHours(hour, minute, 0, 0)
  return d.toISOString()
}

function seed() {
  const users = [
    {
      id: 1,
      username: 'admin',
      password: 'admin123',
      role: 'admin',
      enabled: true,
      email: 'admin@cipherws.icu',
      nickname: '站点管理员',
      avatar: '',
      bio: '负责用户审核、笔记巡检与 AI 参数配置。',
      aiQuota: 100,
      createdAt: daysAgo(120, 9)
    },
    {
      id: 2,
      username: 'demo',
      password: 'demo123',
      role: 'user',
      enabled: true,
      email: 'demo@cipherws.icu',
      nickname: '池禾',
      avatar: '',
      bio: '毕业设计中，用这个笔记本记技术笔记和灵感。',
      aiQuota: 8,
      createdAt: daysAgo(60, 14, 20)
    },
    {
      id: 3,
      username: 'lisi',
      password: 'lisi123',
      role: 'user',
      enabled: true,
      email: 'lisi@example.com',
      nickname: '李四',
      avatar: '',
      bio: '',
      aiQuota: 5,
      createdAt: daysAgo(30, 11, 5)
    },
    {
      id: 4,
      username: 'wangwu',
      password: 'wangwu123',
      role: 'user',
      enabled: false,
      email: 'wangwu@example.com',
      nickname: '王五',
      avatar: '',
      bio: '',
      aiQuota: 0,
      createdAt: daysAgo(20, 16, 40)
    }
  ]

  const tags = [
    { id: 1, userId: 2, name: '毕业设计', createdAt: daysAgo(58) },
    { id: 2, userId: 2, name: '前端', createdAt: daysAgo(57) },
    { id: 3, userId: 2, name: '灵感', createdAt: daysAgo(50) },
    { id: 4, userId: 2, name: '读书', createdAt: daysAgo(40) },
    { id: 5, userId: 3, name: '前端', createdAt: daysAgo(29) }
  ]

  const notes = [
    {
      id: 1,
      userId: 2,
      title: 'Nginx 双站点是怎么分流的',
      content:
        '同一个 nginx 用两个 server 块按 server_name 分流，域名不同、root 不同，就能把两个完全独立的前端站点放在一台机器上。\n\n关键点：\n1. 两个 server 块都 listen 80，靠 server_name 区分；\n2. 主站的 location / 反代到本机 node，列车站直接 root 到 dist；\n3. index.html 一定不能长缓存，否则新构建换了 hash 资源名，用户会白屏。\n\n顺手把 /api 也反代到网关，前后端同源之后跨域问题就彻底消失了。',
      status: 'private',
      deleted: false,
      flagged: false,
      createdAt: daysAgo(3, 21, 10),
      updatedAt: daysAgo(1, 9, 30)
    },
    {
      id: 2,
      userId: 2,
      title: '为什么不用 npm workspaces',
      content:
        '主站是 Vite 5，列车站是 Vite 6。如果强行用 workspaces 把依赖提升到根 node_modules，两个版本会互相污染，sass 的编译链也会打架。\n\n结论：两个子项目各自一套 node_modules，根 package.json 只做命令编排，一个依赖都不装。',
      status: 'private',
      deleted: false,
      flagged: false,
      createdAt: daysAgo(6, 15, 45),
      updatedAt: daysAgo(6, 16, 20)
    },
    {
      id: 3,
      userId: 2,
      title: '一个还没想清楚的灵感',
      content:
        '如果把 AI 摘要做成「页边批注」而不是弹窗，读笔记的时候摘要和正文可以并排看 —— 像老师写在稿纸边上的批注。\n\n待验证：窄屏下页边放不下，要退化成折叠面板。',
      status: 'draft',
      deleted: false,
      flagged: false,
      createdAt: daysAgo(2, 23, 5),
      updatedAt: daysAgo(2, 23, 40)
    },
    {
      id: 4,
      userId: 2,
      title: '《人月神话》读书笔记',
      content:
        '「向进度落后的项目增加人手，只会让进度更落后。」\n\n原因是沟通成本随人数平方增长，而新人还需要学习成本。这解释了为什么加人不是万能的。',
      status: 'private',
      deleted: false,
      flagged: false,
      createdAt: daysAgo(12, 20, 0),
      updatedAt: daysAgo(12, 20, 30)
    },
    {
      id: 5,
      userId: 2,
      title: '（已删除）临时记的一串命令',
      content: 'rm -rf node_modules && npm install\n实测对 EBUSY 有效。',
      status: 'private',
      deleted: true,
      flagged: false,
      createdAt: daysAgo(9, 12, 0),
      updatedAt: daysAgo(9, 12, 5)
    },
    {
      id: 6,
      userId: 3,
      title: 'LeetCode 双指针小结',
      content: '左右指针相向移动，适用于有序数组的两数之和、三数之和。',
      status: 'private',
      deleted: false,
      flagged: false,
      createdAt: daysAgo(5, 10, 15),
      updatedAt: daysAgo(5, 10, 45)
    }
  ]

  const noteTagRel = [
    { noteId: 1, tagId: 1 },
    { noteId: 1, tagId: 2 },
    { noteId: 2, tagId: 1 },
    { noteId: 3, tagId: 3 },
    { noteId: 4, tagId: 4 }
  ]

  const aiSummaryRecords = [
    {
      id: 1,
      noteId: 1,
      userId: 2,
      sourceSnippet: '同一个 nginx 用两个 server 块按 server_name 分流……',
      summary:
        '用两个 server 块按域名分流，主站反代 node、列车站直托 dist；同时强调 index.html 不能长缓存，并把 /api 一起反代以消除跨域。',
      createdAt: daysAgo(1, 10, 0),
      quotaCost: 1
    },
    {
      id: 2,
      noteId: 2,
      userId: 2,
      sourceSnippet: '主站是 Vite 5，列车站是 Vite 6……',
      summary: '用版本冲突解释为什么放弃 npm workspaces，改为各子项目独立依赖、根目录只做命令编排。',
      createdAt: daysAgo(6, 17, 0),
      quotaCost: 1
    }
  ]

  const announcements = [
    {
      id: 1,
      title: '笔记系统上线',
      content: '支持笔记编写、标签分类、回收站与 AI 智能总结。AI 总结每次消耗 1 次额度，额度可在个人资料页查看。',
      adminId: 1,
      createdAt: daysAgo(7, 9, 0),
      enabled: true
    },
    {
      id: 2,
      title: '关于 AI 额度调整',
      content: '本周内所有用户 AI 额度统一上调 2 次，如未到账请联系管理员。',
      adminId: 1,
      createdAt: daysAgo(1, 18, 0),
      enabled: false
    }
  ]

  const userOperationLogs = [
    { id: 1, userId: 2, type: '登录', detail: '账号登录成功', createdAt: daysAgo(0, 9, 12), ip: '47.104.253.198' },
    { id: 2, userId: 2, type: '新建笔记', detail: '新建《Nginx 双站点是怎么分流的》', createdAt: daysAgo(3, 21, 12), ip: '47.104.253.198' },
    { id: 3, userId: 2, type: 'AI 总结', detail: '调用 AI 总结，消耗 1 次额度', createdAt: daysAgo(1, 10, 0), ip: '47.104.253.198' },
    { id: 4, userId: 2, type: '删除笔记', detail: '《（已删除）临时记的一串命令》移入回收站', createdAt: daysAgo(9, 12, 6), ip: '47.104.253.198' },
    { id: 5, userId: 3, type: '登录', detail: '账号登录成功', createdAt: daysAgo(1, 8, 40), ip: '112.65.12.9' }
  ]

  const adminOperationLogs = [
    { id: 1, adminId: 1, type: '禁用用户', detail: '禁用用户 wangwu', createdAt: daysAgo(4, 15, 0), ip: '47.104.253.198' },
    { id: 2, adminId: 1, type: '修改 AI 配置', detail: '单次总结最大字数 800 -> 1200', createdAt: daysAgo(2, 11, 30), ip: '47.104.253.198' },
    { id: 3, adminId: 1, type: '发布公告', detail: '发布《笔记系统上线》', createdAt: daysAgo(7, 9, 0), ip: '47.104.253.198' }
  ]

  const aiConfig = [
    {
      id: 1,
      endpoint: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
      apiKey: 'sk-demo-0000000000000000',
      model: 'qwen-plus',
      maxChars: 1200,
      updatedBy: 1,
      updatedAt: daysAgo(2, 11, 30)
    }
  ]

  return {
    users,
    notes,
    tags,
    noteTagRel,
    aiSummaryRecords,
    announcements,
    userOperationLogs,
    adminOperationLogs,
    aiConfig,
    seq: {
      user: 5,
      note: 7,
      tag: 6,
      aiSummaryRecord: 3,
      announcement: 3,
      userOperationLog: 6,
      adminOperationLog: 4,
      aiConfig: 2
    }
  }
}

let cache = null

export function loadDb() {
  if (cache) return cache
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      cache = JSON.parse(raw)
      return cache
    }
  } catch {
    // 数据损坏就重建，不让演示卡在坏状态上
  }
  cache = seed()
  saveDb()
  return cache
}

export function saveDb() {
  if (!cache) return
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(cache))
  } catch {
    // 隐私模式下 localStorage 可能不可写，忽略即可（仅影响刷新后是否保留）
  }
}

/** 演示用：把数据恢复到初始状态 */
export function resetDb() {
  cache = seed()
  saveDb()
  return cache
}

export function nextId(table) {
  const db = loadDb()
  db.seq[table] = (db.seq[table] || 1) + 1
  return db.seq[table] - 1
}

export const nowIso = () => new Date().toISOString()
export { iso, daysAgo }
