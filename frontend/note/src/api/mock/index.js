/**
 * mock 请求分发器：把 api/request.js 发来的调用路由到内存数据上。
 *
 * 返回信封与真实后端保持一致：
 *   { success: true, data, total }
 *   { success: false, errMessage }
 * 这样页面和 api 层在 mock / http 两种模式下行为完全一致。
 */

import { loadDb, saveDb, nextId, nowIso, resetDb } from './db.js'

const ok = (data, total) => ({ success: true, data, total })
const fail = (errMessage) => ({ success: false, errMessage })

/* ------------------------------- 通用工具 ------------------------------- */

function currentUser(db, token) {
  const m = /^mock-token-(\d+)-/.exec(String(token || ''))
  if (!m) return null
  const user = db.users.find((u) => u.id === Number(m[1])) || null
  if (!user || user.enabled === false) return null
  return user
}

/** 对外绝不返回密码 */
function publicUser(u) {
  if (!u) return null
  const { password, ...rest } = u
  return rest
}

function paginate(list, params = {}) {
  const page = Math.max(1, Number(params.page) || 1)
  const size = Math.max(1, Number(params.size) || 10)
  const start = (page - 1) * size
  return { data: list.slice(start, start + size), total: list.length }
}

function noteTags(db, noteId) {
  const ids = db.noteTagRel.filter((r) => r.noteId === noteId).map((r) => r.tagId)
  return db.tags.filter((t) => ids.includes(t.id))
}

/** 把 note 组装成页面需要的形状（带标签、作者名） */
function decorateNote(db, note) {
  const author = db.users.find((u) => u.id === note.userId)
  return {
    ...note,
    tags: noteTags(db, note.id),
    authorName: author?.nickname || author?.username || '未知用户'
  }
}

function logUser(db, userId, type, detail) {
  db.userOperationLogs.unshift({
    id: nextId('userOperationLog'),
    userId,
    type,
    detail,
    createdAt: nowIso(),
    ip: '127.0.0.1'
  })
}

function logAdmin(db, adminId, type, detail) {
  db.adminOperationLogs.unshift({
    id: nextId('adminOperationLog'),
    adminId,
    type,
    detail,
    createdAt: nowIso(),
    ip: '127.0.0.1'
  })
}

/**
 * 演示用的「AI 摘要」。
 * 真实实现由 note-ai-service 调用第三方模型，这里用可解释的启发式，
 * 保证结果看得出和原文有关、又不会假装自己是真的模型输出。
 */
function fakeSummary(content, maxChars) {
  const text = String(content || '').trim()
  if (!text) return '（正文为空，无法总结）'

  const lines = text.split(/\n+/).map((s) => s.trim()).filter(Boolean)
  const first = lines[0] || text
  const head = first.length > maxChars ? `${first.slice(0, maxChars)}…` : first

  const bullets = lines
    .slice(1)
    .filter((l) => /^(\d+[.、)]|[-*•])/.test(l))
    .map((l) => l.replace(/^(\d+[.、)]|[-*•])\s*/, ''))
    .slice(0, 4)

  const tail = bullets.length
    ? `\n\n要点：\n${bullets.map((b) => `· ${b.length > 60 ? `${b.slice(0, 60)}…` : b}`).join('\n')}`
    : ''

  return `这篇笔记主要在讲：${head}${tail}`
}

/* ------------------------------- 路由实现 ------------------------------- */

const routes = []
function on(method, pattern, handler) {
  routes.push({ method, pattern, handler })
}

/* ---------------- 认证 ---------------- */

on('POST', /^\/auth\/register$/, ({ body }) => {
  const db = loadDb()
  const username = String(body?.username || '').trim()
  const password = String(body?.password || '')
  const email = String(body?.email || '').trim()

  // 与后端一致：2-20 个字符，允许中文
  if (!/^[\u4e00-\u9fa5A-Za-z0-9_]{2,20}$/.test(username)) {
    return fail('账号需为 2-20 个字符，可用中文、字母、数字或下划线')
  }
  if (password.length < 6) return fail('密码至少 6 位')
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail('邮箱格式不正确')
  if (db.users.some((u) => u.username.toLowerCase() === username.toLowerCase())) return fail('该账号已被注册')

  const user = {
    id: nextId('user'),
    username,
    password,
    role: 'user',
    enabled: true,
    email,
    nickname: username,
    avatar: '',
    bio: '',
    aiQuota: 5,
    createdAt: nowIso()
  }
  db.users.push(user)
  saveDb()
  return ok(publicUser(user))
})

on('POST', /^\/auth\/login$/, ({ body }) => {
  const db = loadDb()
  const username = String(body?.username || '').trim()
  const password = String(body?.password || '')
  const user = db.users.find((u) => u.username.toLowerCase() === username.toLowerCase())
  if (!user || user.password !== password) return fail('账号或密码不正确')
  if (user.enabled === false) return fail('该账号已被禁用，请联系管理员')

  logUser(db, user.id, '登录', '账号登录成功')
  saveDb()
  return ok({
    token: `mock-token-${user.id}-${Date.now()}`,
    user: publicUser(user)
  })
})

on('POST', /^\/auth\/logout$/, () => ok(true))

on('POST', /^\/auth\/send-code$/, ({ body }) => {
  const db = loadDb()
  const email = String(body?.email || '').trim()
  if (!db.users.some((u) => u.email.toLowerCase() === email.toLowerCase())) {
    return fail('该邮箱未注册')
  }
  // 演示环境直接把验证码返回给前端，真实后端必须走邮件
  return ok({ code: '123456', hint: '演示环境固定验证码 123456' })
})

on('POST', /^\/auth\/reset-password$/, ({ body }) => {
  const db = loadDb()
  const email = String(body?.email || '').trim()
  const code = String(body?.code || '').trim()
  const password = String(body?.password || '')
  const user = db.users.find((u) => u.email.toLowerCase() === email.toLowerCase())
  if (!user) return fail('该邮箱未注册')
  if (code !== '123456') return fail('验证码不正确')
  if (password.length < 6) return fail('新密码至少 6 位')
  user.password = password
  saveDb()
  return ok(true)
})

/* ---------------- 用户中心 ---------------- */

on('GET', /^\/user\/profile$/, ({ db, user }) => ok(publicUser(user)))

on('PUT', /^\/user\/profile$/, ({ db, user, body }) => {
  const nickname = String(body?.nickname || '').trim()
  if (!nickname) return fail('昵称不能为空')
  if ([...nickname].length > 20) return fail('昵称最多 20 个字符')
  if ([...(body?.bio || '')].length > 120) return fail('个人简介最多 120 个字符')

  // 邮箱**不在这里改**：必须走 sendBindCode + bindEmail 并验码，
  // 否则一个人可以随便填别人的邮箱，再拿它去重置别人的密码。
  // 头像允许两种值：mock 里存的是 data URL，真实后端是 /uploads/**。
  const avatar = String(body?.avatar ?? user.avatar ?? '')
  if (avatar && !avatar.startsWith('data:image/') && !avatar.startsWith('/uploads/')) {
    return fail('头像请通过上传功能设置')
  }

  user.nickname = nickname
  user.bio = String(body?.bio || '').slice(0, 120)
  user.avatar = avatar
  saveDb()
  return ok(publicUser(user))
})

/* ---------------- 邮箱验证码（mock 内存态，刷新即失效） ---------------- */
const mailCodes = new Map()

on('POST', /^\/user\/email\/code$/, ({ db, user, body }) => {
  const email = String(body?.email || '').trim().toLowerCase()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail('邮箱格式不正确')
  if (email === String(user.email || '').toLowerCase()) return fail('这个邮箱已经绑定到当前账号了')
  if (db.users.some((u) => u.id !== user.id && String(u.email || '').toLowerCase() === email)) {
    return fail('该邮箱已被其他账号绑定')
  }

  const now = Date.now()
  const prev = mailCodes.get(email)
  if (prev && now - prev.sentAt < 60000) {
    return fail(`验证码发送过于频繁，请 ${Math.ceil((60000 - (now - prev.sentAt)) / 1000)} 秒后再试`)
  }

  const code = String(Math.floor(100000 + Math.random() * 900000))
  mailCodes.set(email, { code, purpose: 'bind', expireAt: now + 5 * 60_000, attempts: 0, sentAt: now })
  return ok({ hint: '演示环境：验证码已直接返回，无需查收邮件', code, cooldown: 60 })
})

on('PUT', /^\/user\/email$/, ({ db, user, body }) => {
  const email = String(body?.email || '').trim().toLowerCase()
  const code = String(body?.code || '').trim()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail('邮箱格式不正确')
  if (email === String(user.email || '').toLowerCase()) return fail('这个邮箱已经绑定到当前账号了')
  if (db.users.some((u) => u.id !== user.id && String(u.email || '').toLowerCase() === email)) {
    return fail('该邮箱已被其他账号绑定')
  }

  const entry = mailCodes.get(email)
  if (!entry) return fail('请先获取验证码')
  if (entry.expireAt < Date.now()) {
    mailCodes.delete(email)
    return fail('验证码已过期，请重新获取')
  }
  if (entry.purpose !== 'bind') return fail('验证码用途不匹配，请重新获取')
  if (entry.attempts >= 5) {
    mailCodes.delete(email)
    return fail('验证码错误次数过多，请重新获取')
  }
  if (entry.code !== code) {
    entry.attempts += 1
    return fail('验证码不正确')
  }

  mailCodes.delete(email)
  user.email = email
  saveDb()
  return ok(publicUser(user))
})

on('POST', /^\/user\/avatar$/, ({ user, body }) => {
  const dataUrl = String(body?.dataUrl || '')
  if (!dataUrl.startsWith('data:image/')) return fail('只支持 PNG / JPG / WebP 格式的图片')
  // 演示模式存的是 base64，localStorage 只有 ~5MB，所以卡得比后端严
  if (Number(body?.size || 0) > 500 * 1024) {
    return fail('演示模式请上传 500KB 以内的图片（数据存在浏览器本地）')
  }
  user.avatar = dataUrl
  saveDb()
  return ok(publicUser(user))
})

on('PUT', /^\/user\/password$/, ({ db, user, body }) => {
  const oldPassword = String(body?.oldPassword || '')
  const newPassword = String(body?.newPassword || '')
  if (user.password !== oldPassword) return fail('原密码不正确')
  if (newPassword.length < 6) return fail('新密码至少 6 位')
  if (newPassword === oldPassword) return fail('新密码不能与原密码相同')
  user.password = newPassword
  saveDb()
  return ok(true)
})

on('GET', /^\/user\/quota$/, ({ user }) => ok({ aiQuota: user.aiQuota }))

/* ---------------- 笔记 ---------------- */

on('GET', /^\/notes$/, ({ db, user, params }) => {
  const keyword = String(params?.keyword || '').trim().toLowerCase()
  const tagId = params?.tagId ? Number(params.tagId) : null
  const status = params?.status || ''

  let list = db.notes.filter((n) => n.userId === user.id && !n.deleted)
  if (keyword) {
    list = list.filter(
      (n) =>
        n.title.toLowerCase().includes(keyword) || String(n.content).toLowerCase().includes(keyword)
    )
  }
  if (tagId) {
    const noteIds = db.noteTagRel.filter((r) => r.tagId === tagId).map((r) => r.noteId)
    list = list.filter((n) => noteIds.includes(n.id))
  }
  if (status) list = list.filter((n) => n.status === status)

  list = [...list].sort((a, b) => (a.updatedAt < b.updatedAt ? 1 : -1))
  const page = paginate(list, params)
  return ok(page.data.map((n) => decorateNote(db, n)), page.total)
})

on('GET', /^\/notes\/trash$/, ({ db, user, params }) => {
  let list = db.notes.filter((n) => n.userId === user.id && n.deleted)
  list = [...list].sort((a, b) => (a.updatedAt < b.updatedAt ? 1 : -1))
  const page = paginate(list, params)
  return ok(page.data.map((n) => decorateNote(db, n)), page.total)
})

on('DELETE', /^\/notes\/trash$/, ({ db, user }) => {
  db.notes = db.notes.filter((n) => !(n.userId === user.id && n.deleted))
  saveDb()
  return ok(true)
})

on('GET', /^\/notes\/(\d+)$/, ({ db, user, args }) => {
  const note = db.notes.find((n) => n.id === Number(args[0]) && n.userId === user.id)
  if (!note) return fail('笔记不存在')
  const summaries = db.aiSummaryRecords
    .filter((r) => r.noteId === note.id)
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
  return ok({ ...decorateNote(db, note), summaries })
})

on('POST', /^\/notes$/, ({ db, user, body }) => {
  const title = String(body?.title || '').trim() || '无标题笔记'
  const note = {
    id: nextId('note'),
    userId: user.id,
    title,
    content: String(body?.content || ''),
    status: body?.status === 'draft' ? 'draft' : 'private',
    deleted: false,
    flagged: false,
    createdAt: nowIso(),
    updatedAt: nowIso()
  }
  db.notes.unshift(note)
  bindTags(db, note.id, body?.tagIds)
  logUser(db, user.id, '新建笔记', `新建《${title}》`)
  saveDb()
  return ok(decorateNote(db, note))
})

on('PUT', /^\/notes\/(\d+)$/, ({ db, user, args, body }) => {
  const note = db.notes.find((n) => n.id === Number(args[0]) && n.userId === user.id)
  if (!note) return fail('笔记不存在')
  if (body?.title !== undefined) note.title = String(body.title).trim() || '无标题笔记'
  if (body?.content !== undefined) note.content = String(body.content)
  if (body?.status !== undefined) note.status = body.status === 'draft' ? 'draft' : 'private'
  note.updatedAt = nowIso()
  if (body?.tagIds !== undefined) bindTags(db, note.id, body.tagIds)
  saveDb()
  return ok(decorateNote(db, note))
})

on('DELETE', /^\/notes\/(\d+)$/, ({ db, user, args, params }) => {
  const note = db.notes.find((n) => n.id === Number(args[0]) && n.userId === user.id)
  if (!note) return fail('笔记不存在')
  if (String(params?.hard) === 'true') {
    db.notes = db.notes.filter((n) => n.id !== note.id)
    db.noteTagRel = db.noteTagRel.filter((r) => r.noteId !== note.id)
    logUser(db, user.id, '删除笔记', `永久删除《${note.title}》`)
  } else {
    note.deleted = true
    note.updatedAt = nowIso()
    logUser(db, user.id, '删除笔记', `《${note.title}》移入回收站`)
  }
  saveDb()
  return ok(true)
})

on('POST', /^\/notes\/(\d+)\/restore$/, ({ db, user, args }) => {
  const note = db.notes.find((n) => n.id === Number(args[0]) && n.userId === user.id)
  if (!note) return fail('笔记不存在')
  note.deleted = false
  note.updatedAt = nowIso()
  logUser(db, user.id, '恢复笔记', `从回收站恢复《${note.title}》`)
  saveDb()
  return ok(true)
})

function bindTags(db, noteId, tagIds) {
  db.noteTagRel = db.noteTagRel.filter((r) => r.noteId !== noteId)
  if (!Array.isArray(tagIds)) return
  tagIds.forEach((tagId) => {
    if (db.tags.some((t) => t.id === Number(tagId))) {
      db.noteTagRel.push({ noteId, tagId: Number(tagId) })
    }
  })
}

/* ---------------- 标签 ---------------- */

on('GET', /^\/tags$/, ({ db, user }) => {
  const list = db.tags
    .filter((t) => t.userId === user.id)
    .map((t) => ({
      ...t,
      noteCount: db.noteTagRel.filter(
        (r) =>
          r.tagId === t.id &&
          db.notes.some((n) => n.id === r.noteId && !n.deleted && n.userId === user.id)
      ).length
    }))
  return ok(list, list.length)
})

on('POST', /^\/tags$/, ({ db, user, body }) => {
  const name = String(body?.name || '').trim()
  if (!name) return fail('标签名不能为空')
  if (name.length > 12) return fail('标签名最多 12 个字符')
  // 需求 7.4：标签属于用户私有，不同用户同名标签互不干扰；同一用户内不允许重名
  if (db.tags.some((t) => t.userId === user.id && t.name === name)) return fail('同名标签已存在')
  const tag = { id: nextId('tag'), userId: user.id, name, createdAt: nowIso() }
  db.tags.push(tag)
  saveDb()
  return ok(tag)
})

on('PUT', /^\/tags\/(\d+)$/, ({ db, user, args, body }) => {
  const tag = db.tags.find((t) => t.id === Number(args[0]) && t.userId === user.id)
  if (!tag) return fail('标签不存在')
  const name = String(body?.name || '').trim()
  if (!name) return fail('标签名不能为空')
  if (db.tags.some((t) => t.userId === user.id && t.name === name && t.id !== tag.id)) {
    return fail('同名标签已存在')
  }
  tag.name = name
  saveDb()
  return ok(tag)
})

on('DELETE', /^\/tags\/(\d+)$/, ({ db, user, args }) => {
  const tag = db.tags.find((t) => t.id === Number(args[0]) && t.userId === user.id)
  if (!tag) return fail('标签不存在')
  // 需求 4.5.3：删标签不删笔记，只解除关联
  db.tags = db.tags.filter((t) => t.id !== tag.id)
  db.noteTagRel = db.noteTagRel.filter((r) => r.tagId !== tag.id)
  saveDb()
  return ok(true)
})

/* ---------------- AI 总结 ---------------- */

on('POST', /^\/ai\/summary$/, ({ db, user, body }) => {
  const noteId = Number(body?.noteId)
  const note = db.notes.find((n) => n.id === noteId && n.userId === user.id)
  if (!note) return fail('笔记不存在')
  if (!String(note.content || '').trim()) return fail('正文为空，无法生成摘要')

  const config = db.aiConfig[0] || { maxChars: 1200 }
  const quota = Number(config.maxChars) || 1200

  // 需求 4.4.4：额度不足禁止调用
  if (user.aiQuota <= 0) return fail('AI 额度已用完，请联系管理员分配')

  user.aiQuota -= 1
  const record = {
    id: nextId('aiSummaryRecord'),
    noteId,
    userId: user.id,
    sourceSnippet: String(note.content).slice(0, 60),
    summary: fakeSummary(note.content, quota),
    createdAt: nowIso(),
    quotaCost: 1
  }
  db.aiSummaryRecords.unshift(record)
  logUser(db, user.id, 'AI 总结', `对《${note.title}》调用 AI 总结，消耗 1 次额度`)
  saveDb()
  return ok({ ...record, quotaLeft: user.aiQuota })
})

on('GET', /^\/ai\/summary$/, ({ db, user, params }) => {
  const noteId = params?.noteId ? Number(params.noteId) : null
  let list = db.aiSummaryRecords.filter((r) => r.userId === user.id)
  if (noteId) list = list.filter((r) => r.noteId === noteId)
  list = [...list].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
  return ok(list, list.length)
})

/* ---------------- 公告（用户可见） ---------------- */

on('GET', /^\/announcements$/, ({ db }) => {
  const list = db.announcements
    .filter((a) => a.enabled)
    .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
  return ok(list, list.length)
})

/* ---------------- 后台：用户管理 ---------------- */

on('GET', /^\/admin\/users$/, ({ db, user, params }) => {
  requireAdmin(user)
  const keyword = String(params?.keyword || '').trim().toLowerCase()
  let list = [...db.users]
  if (keyword) {
    list = list.filter(
      (u) =>
        u.username.toLowerCase().includes(keyword) ||
        String(u.nickname || '').toLowerCase().includes(keyword) ||
        String(u.email || '').toLowerCase().includes(keyword)
    )
  }
  list.sort((a, b) => a.id - b.id)
  const page = paginate(list, params)
  return ok(
    page.data.map((u) => ({
      ...publicUser(u),
      noteCount: db.notes.filter((n) => n.userId === u.id && !n.deleted).length
    })),
    page.total
  )
})

on('PUT', /^\/admin\/users\/(\d+)\/status$/, ({ db, user, args, body }) => {
  requireAdmin(user)
  const target = db.users.find((u) => u.id === Number(args[0]))
  if (!target) return fail('用户不存在')
  if (target.id === user.id) return fail('不能禁用自己')
  target.enabled = Boolean(body?.enabled)
  logAdmin(db, user.id, target.enabled ? '启用用户' : '禁用用户', `${target.enabled ? '启用' : '禁用'}用户 ${target.username}`)
  saveDb()
  return ok(publicUser(target))
})

on('PUT', /^\/admin\/users\/(\d+)\/quota$/, ({ db, user, args, body }) => {
  requireAdmin(user)
  const target = db.users.find((u) => u.id === Number(args[0]))
  if (!target) return fail('用户不存在')
  const quota = Number(body?.aiQuota)
  if (!Number.isInteger(quota) || quota < 0 || quota > 9999) return fail('额度需为 0-9999 的整数')
  const before = target.aiQuota
  target.aiQuota = quota
  logAdmin(db, user.id, '重置 AI 配额', `${target.username} 额度 ${before} -> ${quota}`)
  saveDb()
  return ok(publicUser(target))
})

/* ---------------- 后台：笔记巡检 ---------------- */

on('GET', /^\/admin\/notes$/, ({ db, user, params }) => {
  requireAdmin(user)
  const keyword = String(params?.keyword || '').trim().toLowerCase()
  let list = [...db.notes]
  if (keyword) {
    list = list.filter(
      (n) =>
        n.title.toLowerCase().includes(keyword) ||
        db.users.find((u) => u.id === n.userId)?.username.toLowerCase().includes(keyword)
    )
  }
  list.sort((a, b) => (a.updatedAt < b.updatedAt ? 1 : -1))
  const page = paginate(list, params)
  return ok(page.data.map((n) => decorateNote(db, n)), page.total)
})

on('PUT', /^\/admin\/notes\/(\d+)\/flag$/, ({ db, user, args, body }) => {
  requireAdmin(user)
  const note = db.notes.find((n) => n.id === Number(args[0]))
  if (!note) return fail('笔记不存在')
  note.flagged = Boolean(body?.flagged)
  logAdmin(db, user.id, '巡检笔记', `${note.flagged ? '标记违规' : '取消标记'}《${note.title}》`)
  saveDb()
  return ok(decorateNote(db, note))
})

/* ---------------- 后台：AI 配置 ---------------- */

on('GET', /^\/admin\/ai-config$/, ({ db, user }) => {
  requireAdmin(user)
  const c = db.aiConfig[0] || {}
  return ok({
    ...c,
    // 需求 6.2.4：密钥不在前端暴露，只回显掩码
    apiKeyMasked: c.apiKey ? `${String(c.apiKey).slice(0, 6)}${'*'.repeat(8)}` : ''
  })
})

on('PUT', /^\/admin\/ai-config$/, ({ db, user, body }) => {
  requireAdmin(user)
  const c = db.aiConfig[0]
  if (!c) return fail('配置不存在')
  const endpoint = String(body?.endpoint || '').trim()
  const model = String(body?.model || '').trim()
  const maxChars = Number(body?.maxChars)

  if (!/^https?:\/\//.test(endpoint)) return fail('接口地址需以 http(s):// 开头')
  if (!model) return fail('模型名称不能为空')
  if (!Number.isInteger(maxChars) || maxChars < 100 || maxChars > 20000) {
    return fail('最大字数需为 100-20000 的整数')
  }

  const beforeMax = c.maxChars
  c.endpoint = endpoint
  c.model = model
  c.maxChars = maxChars
  const apiKey = String(body?.apiKey || '').trim()
  if (apiKey) c.apiKey = apiKey
  c.updatedBy = user.id
  c.updatedAt = nowIso()

  if (beforeMax !== maxChars) {
    logAdmin(db, user.id, '修改 AI 配置', `单次总结最大字数 ${beforeMax} -> ${maxChars}`)
  } else {
    logAdmin(db, user.id, '修改 AI 配置', `更新接口地址/模型为 ${model}`)
  }
  saveDb()
  return ok(true)
})

/* ---------------- 后台：公告 ---------------- */

on('GET', /^\/admin\/announcements$/, ({ db, user, params }) => {
  requireAdmin(user)
  const list = [...db.announcements].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
  const page = paginate(list, params)
  return ok(page.data, page.total)
})

on('POST', /^\/admin\/announcements$/, ({ db, user, body }) => {
  requireAdmin(user)
  const title = String(body?.title || '').trim()
  const content = String(body?.content || '').trim()
  if (!title) return fail('公告标题不能为空')
  if (!content) return fail('公告内容不能为空')
  const item = {
    id: nextId('announcement'),
    title,
    content,
    adminId: user.id,
    createdAt: nowIso(),
    enabled: body?.enabled !== false
  }
  db.announcements.unshift(item)
  logAdmin(db, user.id, '发布公告', `发布《${title}》`)
  saveDb()
  return ok(item)
})

on('PUT', /^\/admin\/announcements\/(\d+)$/, ({ db, user, args, body }) => {
  requireAdmin(user)
  const item = db.announcements.find((a) => a.id === Number(args[0]))
  if (!item) return fail('公告不存在')
  if (body?.title !== undefined) item.title = String(body.title).trim() || item.title
  if (body?.content !== undefined) item.content = String(body.content)
  if (body?.enabled !== undefined) item.enabled = Boolean(body.enabled)
  logAdmin(db, user.id, '编辑公告', `编辑《${item.title}》`)
  saveDb()
  return ok(item)
})

on('DELETE', /^\/admin\/announcements\/(\d+)$/, ({ db, user, args }) => {
  requireAdmin(user)
  const item = db.announcements.find((a) => a.id === Number(args[0]))
  if (!item) return fail('公告不存在')
  db.announcements = db.announcements.filter((a) => a.id !== item.id)
  logAdmin(db, user.id, '删除公告', `删除《${item.title}》`)
  saveDb()
  return ok(true)
})

/* ---------------- 后台：日志与统计 ---------------- */

on('GET', /^\/admin\/logs$/, ({ db, user, params }) => {
  requireAdmin(user)
  const type = params?.type === 'admin' ? 'admin' : 'user'
  const keyword = String(params?.keyword || '').trim().toLowerCase()
  const from = params?.from ? new Date(params.from).getTime() : null
  const to = params?.to ? new Date(`${params.to}T23:59:59`).getTime() : null

  const raw =
    type === 'admin'
      ? db.adminOperationLogs.map((l) => {
          const u = db.users.find((x) => x.id === l.adminId)
          return { ...l, actorName: u?.nickname || u?.username || '管理员' }
        })
      : db.userOperationLogs.map((l) => {
          const u = db.users.find((x) => x.id === l.userId)
          return { ...l, actorName: u?.nickname || u?.username || '用户' }
        })

  let list = raw
  if (keyword) {
    list = list.filter(
      (l) =>
        String(l.type).toLowerCase().includes(keyword) ||
        String(l.detail).toLowerCase().includes(keyword) ||
        String(l.actorName).toLowerCase().includes(keyword)
    )
  }
  if (from) list = list.filter((l) => new Date(l.createdAt).getTime() >= from)
  if (to) list = list.filter((l) => new Date(l.createdAt).getTime() <= to)

  list = [...list].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
  const page = paginate(list, params)
  return ok(page.data, page.total)
})

on('GET', /^\/admin\/stats$/, ({ db, user }) => {
  requireAdmin(user)

  // 近 7 天 AI 调用次数
  const daily = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    const key = d.toISOString().slice(0, 10)
    daily.push({
      date: key,
      count: db.aiSummaryRecords.filter((r) => r.createdAt.slice(0, 10) === key).length
    })
  }

  const byUser = {}
  db.aiSummaryRecords.forEach((r) => {
    byUser[r.userId] = (byUser[r.userId] || 0) + 1
  })
  const topUsers = Object.entries(byUser)
    .map(([id, count]) => {
      const u = db.users.find((x) => x.id === Number(id))
      return { userId: Number(id), name: u?.nickname || u?.username || '未知', count }
    })
    .sort((a, b) => b.count - a.count)
    .slice(0, 5)

  return ok({
    daily,
    topUsers,
    totals: {
      users: db.users.length,
      notes: db.notes.filter((n) => !n.deleted).length,
      aiCalls: db.aiSummaryRecords.length,
      flaggedNotes: db.notes.filter((n) => n.flagged).length
    }
  })
})

/* ---------------- 演示用：重置数据 ---------------- */

on('POST', /^\/mock\/reset$/, () => {
  resetDb()
  return ok(true)
})

function requireAdmin(user) {
  if (!user || user.role !== 'admin') {
    // 用一个明确标记的失败让上层弹出「无权限」，而不是静默 404
    const err = new Error('无管理员权限')
    err.forbidden = true
    throw err
  }
}

/* ------------------------------- 分发入口 ------------------------------- */

export async function mockRequest({ method, path, params, body }) {
  const db = loadDb()
  const token = localStorage.getItem('note_token') || ''
  const user = currentUser(db, token)

  for (const r of routes) {
    if (r.method !== method) continue
    const m = r.pattern.exec(path)
    if (!m) continue

    // 除认证与公告外的接口都要求登录
    const needAuth = !path.startsWith('/auth/') && path !== '/announcements'
    if (needAuth && !user) {
      return { success: false, errMessage: '登录已失效，请重新登录' }
    }

    try {
      return await r.handler({ db, user, params, body, args: m.slice(1) })
    } catch (err) {
      if (err?.forbidden) return { success: false, errMessage: '无管理员权限' }
      return { success: false, errMessage: err?.message || '服务异常' }
    }
  }

  return { success: false, errMessage: `mock 未实现该接口：${method} ${path}` }
}
