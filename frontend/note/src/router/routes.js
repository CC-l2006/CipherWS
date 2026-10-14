export const constantRoutes = [
	{
		path: '',
		name: 'home',
		component: () => import('@/views/Home.vue'),
		meta: { title: '首页', requiresAuth: false }
	},
	{
		path: '',
		name: 'about',
		component: () => import('@/views/About.vue'),
		meta: { title: '关于', requiresAuth: false }
	},
	{
		path: '/login',
		name: 'Login',
		component: () => import('@/views/Login.vue'),
		meta: { title: '登录', requiresAuth: false }
	},
	{
		path: '/:pathMatch(.*)*',
		name: 'NotFound',
		component: () => import('@/views/NotFound.vue'),
		meta: { title: '页面不存在' }
	}
]

export const asyncRoutes = [
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/Admin.vue'),
    meta: { title: '管理后台', requiresAuth: true, roles: ['admin'] },
    children: [
      {
        path: 'users',
        name: 'UserList',
        component: () => import('@/views/admin/UserList.vue'),
        meta: { title: '用户列表', requiresAuth: true }
      }
    ]
  }
]