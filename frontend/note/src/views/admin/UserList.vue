<template>
  <div class="user-list">
    <h3>用户列表</h3>
    <table>
      <thead>
        <tr>
          <th>ID</th>
          <th>用户名</th>
          <th>角色</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="user in users" :key="user.id">
          <td>{{ user.id }}</td>
          <td>{{ user.username }}</td>
          <td>{{ user.roles.join(', ') }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!users.length" class="empty">暂无数据（占位页面，待接入接口）</p>
  </div>
</template>

<script setup>
import { ref } from 'vue'

// TODO: 接入真实接口后替换这里的 mock 数据
const users = ref([
  { id: 1, username: 'admin', roles: ['admin'] },
  { id: 2, username: 'user', roles: ['user'] }
])
</script>

<style scoped>
.user-list {
  padding: 16px;
}

table {
  border-collapse: collapse;
}

th,
td {
  padding: 6px 16px;
  border: 1px solid #ddd;
}

.empty {
  color: #999;
}
</style>
