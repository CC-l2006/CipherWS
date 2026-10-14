<template>
  <div class="admin">
    <header class="admin-header">
      <h2>管理后台</h2>
      <nav>
        <router-link to="/admin/users">用户列表</router-link>
        <button type="button" @click="handleLogout">退出登录</button>
      </nav>
    </header>

    <!-- 子路由出口：/admin/users -->
    <router-view />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

async function handleLogout() {
  await userStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #eee;
}

.admin-header nav {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
