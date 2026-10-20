<template>
  <AuthLayout
    title="注册"
    subtitle="用邮箱注册：先收一个验证码，验证通过才建账号。注册后即获得私有笔记本与 5 次 AI 总结额度。"
  >
    <form class="form" @submit.prevent="onSubmit">
      <!-- 邮箱：既是登录账号，也是验证码收件地址 -->
      <AppField
        v-model="form.email"
        label="邮箱"
        type="email"
        placeholder="用来登录，也会收到验证码"
        :error="errors.email"
        required
        @submit="onSubmit"
      />

      <!-- 验证码 + 发送按钮 -->
      <div class="code-row">
        <AppField
          v-model="form.code"
          label="验证码"
          :maxlength="6"
          placeholder="6 位数字"
          :error="errors.code"
          @submit="onSubmit"
        />
        <AppButton
          variant="ghost"
          :disabled="countdown > 0 || sending"
          :loading="sending"
          @click="sendCode"
        >
          {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
        </AppButton>
      </div>
      <p v-if="codeHint" class="hint">{{ codeHint }}</p>

      <AppField
        v-model="form.nickname"
        label="昵称（可选）"
        :maxlength="20"
        placeholder="不填就用邮箱前缀"
        :hint="`${nickLen}/20`"
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
import { computed, onUnmounted, reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import AuthLayout from '@/layouts/AuthLayout.vue'
import AppField from '@/components/AppField.vue'
import AppButton from '@/components/AppButton.vue'
import { authApi } from '@/api/index.js'
import { useUserStore } from '@/stores/user.js'
import { useToastStore } from '@/stores/toast.js'

const router = useRouter()
const userStore = useUserStore()
const toast = useToastStore()

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

// 没有 username —— 账号名由后端用邮箱前缀生成，用户不用抢
const form = reactive({ email: '', code: '', nickname: '', password: '', confirm: '' })
const errors = reactive({ email: '', code: '', password: '', confirm: '' })
const formError = ref('')
const loading = ref(false)

const nickLen = computed(() => [...String(form.nickname || '')].length)

/* ---------- 发送验证码 ---------- */
const sending = ref(false)
const codeHint = ref('')
const countdown = ref(0)
let timer = null

onUnmounted(() => clearInterval(timer))

function startCountdown(seconds) {
  countdown.value = seconds
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

async function sendCode() {
  const email = form.email.trim()
  errors.email = !email ? '请先填写邮箱' : EMAIL_RE.test(email) ? '' : '邮箱格式不正确'
  if (errors.email) return

  sending.value = true
  try {
    // purpose=register：后端会校验「这个邮箱还没注册过」
    const { data } = await authApi.sendCode(email, 'register')
    codeHint.value = data?.hint || '验证码已发送'
    // 本地没配 SMTP 时后端会把码直接返回，自动填入省一次手输
    if (data?.code) {
      form.code = data.code
      codeHint.value = `${data.hint}（已自动填入）`
    }
    startCountdown(data?.cooldown || 60)
  } catch (err) {
    errors.email = err.message
  } finally {
    sending.value = false
  }
}

/* ---------- 提交 ---------- */
function validate() {
  const email = form.email.trim()
  errors.email = !email ? '请输入邮箱' : EMAIL_RE.test(email) ? '' : '邮箱格式不正确'
  errors.code = /^\d{6}$/.test(form.code.trim()) ? '' : '请输入 6 位数字验证码'
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
      email: form.email.trim(),
      code: form.code.trim(),
      password: form.password,
      nickname: form.nickname.trim()
    })
    toast.success('注册成功，请用邮箱登录')
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

/* 验证码输入框 + 发送按钮并排 */
.code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--sp-2);
  align-items: end;
}

.hint {
  margin-top: calc(-1 * var(--sp-2));
  font-size: var(--fs-xs);
  line-height: 1.7;
  color: var(--accent-ink);
}

.form__error {
  font-size: var(--fs-sm);
  color: var(--danger);
  padding: 8px 10px;
  border-radius: var(--r-sm);
  background: var(--danger-soft);
}

@media (max-width: 420px) {
  .code-row {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
