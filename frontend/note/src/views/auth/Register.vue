<template>
  <AuthLayout title="注册" subtitle="注册后即获得一个私有的笔记本，以及 5 次 AI 总结额度。">
    <form class="form" @submit.prevent="onSubmit">
      <AppField
        v-model="form.username"
        label="账号"
        placeholder="2-20 个字符，可用中文、字母、数字、下划线"
        :error="errors.username"
        required
        @submit="onSubmit"
      />
      <AppField
        v-model="form.email"
        label="邮箱"
        type="email"
        placeholder="用于找回密码"
        :error="errors.email"
        required
        @submit="onSubmit"
      />
      <AppField
        v-model="form.password"
        label="密码"
        type="password"
        placeholder="至少 6 位"
        :error="errors.password"
        required
        @submit="onSubmit"
      />
      <AppField
        v-model="form.confirm"
        label="确认密码"
        type="password"
        placeholder="再输入一次"
        :error="errors.confirm"
        required
        @submit="onSubmit"
      />

      <p v-if="formError" class="form__error" role="alert">{{ formError }}</p>

      <AppButton type="submit" variant="primary" size="lg" block :loading="loading">
        注册
      </AppButton>
    </form>

    <template #footer>
      已有账号？<RouterLink to="/login">直接登录</RouterLink>
    </template>
  </AuthLayout>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AuthLayout from '@/layouts/AuthLayout.vue'
import AppField from '@/components/AppField.vue'
import AppButton from '@/components/AppButton.vue'
import { useUserStore } from '@/stores/user.js'
import { useToastStore } from '@/stores/toast.js'

const router = useRouter()
const userStore = useUserStore()
const toast = useToastStore()

const form = reactive({ username: '', email: '', password: '', confirm: '' })
const errors = reactive({ username: '', email: '', password: '', confirm: '' })
const formError = ref('')
const loading = ref(false)

function validate() {
  // 与后端保持一致：2-20 个字符，允许中文、字母、数字、下划线
  errors.username = /^[\u4e00-\u9fa5A-Za-z0-9_]{2,20}$/.test(form.username.trim())
    ? ''
    : '账号需为 2-20 个字符，可用中文、字母、数字或下划线'
  errors.email = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) ? '' : '邮箱格式不正确'
  errors.password = form.password.length >= 6 ? '' : '密码至少 6 位'
  errors.confirm = form.confirm === form.password ? '' : '两次输入的密码不一致'
  return Object.values(errors).every((v) => !v)
}

async function onSubmit() {
  if (loading.value) return
  formError.value = ''
  if (!validate()) return

  loading.value = true
  try {
    await userStore.register({
      username: form.username.trim(),
      email: form.email.trim(),
      password: form.password
    })
    toast.success('注册成功，请登录')
    router.replace('/login')
  } catch (err) {
    formError.value = err.message
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.form__error {
  font-size: var(--fs-sm);
  color: var(--danger);
  padding: 8px 10px;
  border-radius: var(--r-sm);
  background: var(--danger-soft);
}
</style>
