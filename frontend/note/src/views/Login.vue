<template>
  <div class="login">
    <h2>登录</h2>
    <form @submit.prevent="handleLogin">
      <p>
        <input v-model="form.username" type="text" placeholder="用户名" />
      </p>
      <p>
        <input v-model="form.password" type="password" placeholder="密码" />
      </p>
      <p>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </p>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  loading.value = true
  error.value = ''
  try {
    await userStore.login(form)
    // 登录成功后回到被拦截的页面，默认去首页
    const redirect = route.query.redirect || '/'
    await router.replace(redirect)
  } catch (err) {
    error.value = err.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  max-width: 320px;
  margin: 80px auto;
  text-align: center;
}

.error {
  color: #d33;
}
</style>
