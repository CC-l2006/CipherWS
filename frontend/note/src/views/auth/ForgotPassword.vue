<template>
  <AuthLayout title="找回密码" subtitle="输入注册邮箱获取验证码，验证后可设置新密码。">
    <form class="form" @submit.prevent="onSubmit">
      <AppField
        v-model="form.email"
        label="注册邮箱"
        type="email"
        placeholder="请输入注册时填写的邮箱"
        :error="errors.email"
        required
        @submit="onSubmit"
      />

      <div class="code-row">
        <AppField
          v-model="form.code"
          label="验证码"
          placeholder="6 位数字"
          :error="errors.code"
          required
          @submit="onSubmit"
        />
        <AppButton
          variant="ghost"
          size="md"
          class="code-btn"
          :loading="sending"
          :disabled="countdown > 0"
          @click="onSendCode"
        >
          {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
        </AppButton>
      </div>

      <AppField
        v-model="form.password"
        label="新密码"
        type="password"
        placeholder="至少 6 位"
        :error="errors.password"
        required
        @submit="onSubmit"
      />

      <p v-if="hint" class="hint">{{ hint }}</p>
      <p v-if="formError" class="form__error" role="alert">{{ formError }}</p>

      <AppButton type="submit" variant="primary" size="lg" block :loading="loading">
        重置密码
      </AppButton>
    </form>

    <template #footer>
      想起来了？<RouterLink to="/login">返回登录</RouterLink>
    </template>
  </AuthLayout>
</template>

<script setup>
import { reactive, ref, onBeforeUnmount } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AuthLayout from '@/layouts/AuthLayout.vue'
import AppField from '@/components/AppField.vue'
import AppButton from '@/components/AppButton.vue'
import { authApi } from '@/api/index.js'
import { useToastStore } from '@/stores/toast.js'

const router = useRouter()
const toast = useToastStore()

const form = reactive({ email: '', code: '', password: '' })
const errors = reactive({ email: '', code: '', password: '' })
const formError = ref('')
const hint = ref('')
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer = null

function validate() {
  errors.email = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()) ? '' : '邮箱格式不正确'
  errors.code = /^\d{6}$/.test(form.code.trim()) ? '' : '请输入 6 位数字验证码'
  errors.password = form.password.length >= 6 ? '' : '新密码至少 6 位'
  return Object.values(errors).every((v) => !v)
}

async function onSendCode() {
  if (countdown.value > 0 || sending.value) return
  formError.value = ''
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    errors.email = '请先填写正确的邮箱'
    return
  }
  sending.value = true
  try {
    const { data } = await authApi.sendCode(form.email.trim())
    hint.value = data?.hint || '验证码已发送，请查收邮件'
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (err) {
    formError.value = err.message
  } finally {
    sending.value = false
  }
}

async function onSubmit() {
  if (loading.value) return
  formError.value = ''
  if (!validate()) return

  loading.value = true
  try {
    await authApi.resetPassword({
      email: form.email.trim(),
      code: form.code.trim(),
      password: form.password
    })
    toast.success('密码已重置，请用新密码登录')
    router.replace('/login')
  } catch (err) {
    formError.value = err.message
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.code-row {
  display: flex;
  align-items: flex-end;
  gap: var(--sp-2);
}

.code-row :deep(.field) {
  flex: 1;
}

.code-btn {
  flex: none;
  white-space: nowrap;
}

.hint {
  font-size: var(--fs-xs);
  color: var(--ok);
}

.form__error {
  font-size: var(--fs-sm);
  color: var(--danger);
  padding: 8px 10px;
  border-radius: var(--r-sm);
  background: var(--danger-soft);
}
</style>
