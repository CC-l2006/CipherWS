import MainLayout from '@/layouts/MainLayout.vue'

/**
 * 路由表按《需求文档》的 7 个功能模块组织：
 *   1 登录与权限  → /login /register /forgot-password
 *   2 用户信息    → /profile
 *   3 笔记核心    → /notes /notes/:id /notes/trash
 *   5 标签分类    → /tags
 *   4 AI 总结     → 不是独立页面，挂在笔记编辑区的右侧留白（页边批注）
 *   6 系统后台    → /admin/*
 *   7 日志与统计  → /admin/logs /admin/stats
 *
 * 分两组导出：
 *   constantRoutes 任何人可访问（含登录页），
 *   asyncRoutes    需要 admin 角色，守卫里按 meta.roles 拦截。
 *
 * 注意：`asyncRoutes` 必须排在 404 兜底之前注册，否则会被 catch-all 抢先匹配。
 */

export const constantRoutes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', guestOnly: true }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/auth/Register.vue'),
    meta: { title: '注册', guestOnly: true }
  },
  {
    path: '/forgot-password',
    name: 'forgot-password',
    component: () => import('@/views/auth/ForgotPassword.vue'),
    meta: { title: '找回密码', guestOnly: true }
  },
  {
    path: '/',
    component: MainLayout,
    redirect: '/notes',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'notes',
        name: 'notes',
        component: () => import('@/views/notes/Notes.vue'),
        meta: { title: '我的笔记', requiresAuth: true }
      },
      {
        // 放在 :id 之前更直观；vue-router 4 本身也会按静态段优先匹配
        path: 'notes/trash',
        name: 'trash',
        component: () => import('@/views/notes/Trash.vue'),
        meta: { title: '回收站', requiresAuth: true }
      },
      {
        path: 'notes/:id',
        name: 'note-detail',
        component: () => import('@/views/notes/Notes.vue'),
        meta: { title: '笔记', requiresAuth: true }
      },
      {
        path: 'tags',
        name: 'tags',
        component: () => import('@/views/Tags.vue'),
        meta: { title: '标签', requiresAuth: true }
      },
      {
        path: 'announcements',
        name: 'announcements',
        component: () => import('@/views/Announcements.vue'),
        meta: { title: '系统公告', requiresAuth: true }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人资料', requiresAuth: true }
      }
    ]
  }
]

export const asyncRoutes = [
  {
    path: '/admin',
    component: () => import('@/views/Admin.vue'),
    redirect: '/admin/users',
    meta: { title: '管理后台', requiresAuth: true, roles: ['admin'] },
    children: [
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/admin/UserList.vue'),
        meta: { title: '用户管理', requiresAuth: true, roles: ['admin'] }
      },
      {
        path: 'notes',
        name: 'admin-notes',
        component: () => import('@/views/admin/NoteInspect.vue'),
        meta: { title: '笔记巡检', requiresAuth: true, roles: ['admin'] }
      },
      {
        path: 'ai-config',
        name: 'admin-ai-config',
        component: () => import('@/views/admin/AiConfig.vue'),
        meta: { title: 'AI 参数配置', requiresAuth: true, roles: ['admin'] }
      },
      {
        path: 'announcements',
        name: 'admin-announcements',
        component: () => import('@/views/admin/AnnouncementAdmin.vue'),
        meta: { title: '公告管理', requiresAuth: true, roles: ['admin'] }
      },
      {
        path: 'logs',
        name: 'admin-logs',
        component: () => import('@/views/admin/Logs.vue'),
        meta: { title: '操作日志', requiresAuth: true, roles: ['admin'] }
      },
      {
        path: 'stats',
        name: 'admin-stats',
        component: () => import('@/views/admin/Stats.vue'),
        meta: { title: 'AI 调用统计', requiresAuth: true, roles: ['admin'] }
      }
    ]
  }
]

/** ⚠️ 必须最后注册 */
export const notFoundRoute = {
  path: '/:pathMatch(.*)*',
  name: 'not-found',
  component: () => import('@/views/NotFound.vue'),
  meta: { title: '页面不存在' }
}
