/**
 * 接口清单：页面对接后端的唯一入口。
 * 每个方法都是薄封装，返回 { data, total? }，失败抛 ApiError。
 * mock / http 两种模式的差异全部收敛在 request.js，这里不需要关心。
 */

import { request } from './request.js'

/* ---------------- 认证 ---------------- */
export const authApi = {
  register: (body) => request('/auth/register', { method: 'POST', body }),
  login: (body) => request('/auth/login', { method: 'POST', body }),
  logout: () => request('/auth/logout', { method: 'POST' }),
  sendCode: (email) => request('/auth/send-code', { method: 'POST', body: { email } }),
  resetPassword: (body) => request('/auth/reset-password', { method: 'POST', body })
}

/* ---------------- 用户中心 ---------------- */
export const userApi = {
  profile: () => request('/user/profile'),
  updateProfile: (body) => request('/user/profile', { method: 'PUT', body }),
  changePassword: (body) => request('/user/password', { method: 'PUT', body }),
  quota: () => request('/user/quota')
}

/* ---------------- 笔记 ---------------- */
export const noteApi = {
  list: (params) => request('/notes', { params }),
  detail: (id) => request(`/notes/${id}`),
  create: (body) => request('/notes', { method: 'POST', body }),
  update: (id, body) => request(`/notes/${id}`, { method: 'PUT', body }),
  remove: (id, hard = false) => request(`/notes/${id}`, { method: 'DELETE', params: { hard } }),
  restore: (id) => request(`/notes/${id}/restore`, { method: 'POST' }),
  trash: (params) => request('/notes/trash', { params }),
  clearTrash: () => request('/notes/trash', { method: 'DELETE' })
}

/* ---------------- 标签 ---------------- */
export const tagApi = {
  list: () => request('/tags'),
  create: (name) => request('/tags', { method: 'POST', body: { name } }),
  update: (id, name) => request(`/tags/${id}`, { method: 'PUT', body: { name } }),
  remove: (id) => request(`/tags/${id}`, { method: 'DELETE' })
}

/* ---------------- AI 总结 ---------------- */
export const aiApi = {
  summarize: (noteId) => request('/ai/summary', { method: 'POST', body: { noteId } }),
  history: (noteId) => request('/ai/summary', { params: { noteId } })
}

/* ---------------- 公告（对所有登录用户可见） ---------------- */
export const announcementApi = {
  list: () => request('/announcements')
}

/* ---------------- 后台管理 ---------------- */
export const adminApi = {
  users: (params) => request('/admin/users', { params }),
  setUserStatus: (id, enabled) => request(`/admin/users/${id}/status`, { method: 'PUT', body: { enabled } }),
  setUserQuota: (id, aiQuota) => request(`/admin/users/${id}/quota`, { method: 'PUT', body: { aiQuota } }),

  notes: (params) => request('/admin/notes', { params }),
  flagNote: (id, flagged) => request(`/admin/notes/${id}/flag`, { method: 'PUT', body: { flagged } }),

  aiConfig: () => request('/admin/ai-config'),
  saveAiConfig: (body) => request('/admin/ai-config', { method: 'PUT', body }),

  announcements: (params) => request('/admin/announcements', { params }),
  createAnnouncement: (body) => request('/admin/announcements', { method: 'POST', body }),
  updateAnnouncement: (id, body) => request(`/admin/announcements/${id}`, { method: 'PUT', body }),
  removeAnnouncement: (id) => request(`/admin/announcements/${id}`, { method: 'DELETE' }),

  logs: (params) => request('/admin/logs', { params }),
  stats: () => request('/admin/stats'),

  /** 演示用：把 mock 数据恢复到初始状态（http 模式下后端没有这个接口） */
  resetMock: () => request('/mock/reset', { method: 'POST' })
}
