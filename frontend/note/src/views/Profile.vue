<template>
  <div class="page">
    <div class="page-title">
      <h1>个人资料</h1>
    </div>

    <div class="cols">
      <!-- 基本资料 -->
      <section class="card sheet">
        <h2 class="card__title">基本资料</h2>

        <div class="who">
          <!-- 头像：点了就是选文件，没图时用昵称首字兜底 -->
          <button
            class="avatar"
            type="button"
            :disabled="uploading"
            :title="uploading ? '上传中…' : '点击更换头像'"
            @click="pickAvatar"
          >
            <img v-if="avatarUrl" class="avatar__img" :src="avatarUrl" alt="当前头像" />
            <span v-else class="avatar__text">{{ initial }}</span>
            <span class="avatar__mask">{{ uploading ? '上传中…' : '更换' }}</span>
          </button>
          <input
            ref="fileInput"
            class="sr-only"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            @change="onAvatarChange"
          />

          <div class="who__meta">
            <p class="who__name">{{ userStore.displayName }}</p>
            <p class="who__role">{{ userStore.isAdmin ? '管理员' : '普通用户' }}</p>
            <p class="who__hint">支持 PNG / JPG / WebP，2MB 以内</p>
          </div>
        </div>

        <form class="form" @submit.prevent="saveProfile">
          <AppField
            v-model="profile.nickname"
            label="昵称"
            :maxlength="20"
            :hint="`${count(profile.nickname)}/20，可用中文`"
            :error="profileErrors.nickname"
            required
          />
          <AppField
            v-model="profile.bio"
            label="个人简介"
            type="textarea"
            :rows="3"
            :maxlength="120"
            :hint="`${count(profile.bio)}/120`"
            placeholder="一句话介绍自己，会显示在个人页上"
          />
          <div class="form__actions">
            <AppButton type="submit" variant="primary" :loading="savingProfile">保存资料</AppButton>
          </div>
        </form>
      </section>

      <div class="side">
        <!-- 绑定邮箱：换绑必须走验证码 -->
        <section class="card sheet">
          <h2 class="card__title">绑定邮箱</h2>

          <p class="email">
            <span :class="{ 'email__empty': !currentEmail }">{{ currentEmail || '尚未绑定' }}</span>
            <button v-if="!bindOpen" class="link" type="button" @click="openBind">
              {{ currentEmail ? '修改' : '去绑定' }}
            </button>
            <button v-else class="link" type="button" @click="closeBind">取消</button>
          </p>
          <p class="card__hint">
            邮箱用于找回密码。修改时会给<strong>新邮箱</strong>发一个验证码，验证通过才会生效 ——
            这样才能保证这个邮箱确实是你的。
          </p>

          <form v-if="bindOpen" class="form" @submit.prevent="confirmBind">
            <AppField
              v-model="bind.email"
              label="新邮箱"
              type="email"
              placeholder="要绑定的邮箱地址"
              :error="bindErrors.email"
            />
            <div class="code-row">
              <AppField
                v-model="bind.code"
                label="验证码"
                :maxlength="6"
                placeholder="6 位数字"
                :error="bindErrors.code"
              />
              <AppButton
                variant="ghost"
                :disabled="countdown > 0 || sendingCode"
                :loading="sendingCode"
                @click="sendBindCode"
              >
                {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </AppButton>
            </div>
            <p v-if="codeHint" class="card__hint card__hint--ok">{{ codeHint }}</p>
            <div class="form__actions">
              <AppButton type="submit" variant="primary" :loading="binding">确认绑定</AppButton>
            </div>
          </form>
        </section>

        <!-- AI 额度 -->
        <section class="card sheet">
          <h2 class="card__title">AI 额度</h2>
          <p class="quota" :class="{ 'is-low': userStore.aiQuota <= 2 }">
            {{ userStore.aiQuota }}<span class="quota__unit">次</span>
          </p>
          <p class="card__hint">
            每调用一次 AI 总结消耗 1 次额度。额度用完后需要联系管理员分配。
          </p>
        </section>

        <!-- 修改密码 -->
        <section class="card sheet">
          <h2 class="card__title">修改密码</h2>
          <form class="form" @submit.prevent="savePassword">
            <AppField
              v-model="password.oldPassword"
              label="原密码"
              type="password"
              :error="passwordErrors.oldPassword"
              required
            />
            <AppField
              v-model="password.newPassword"
              label="新密码"
              type="password"
              hint="至少 6 位"
              :error="passwordErrors.newPassword"
              required
            />
            <AppField
              v-model="password.confirm"
              label="确认新密码"
              type="password"
              :error="passwordErrors.confirm"
              required
            />
            <div class="form__actions">
              <AppButton type="submit" variant="primary" :loading="savingPassword">更新密码</AppButton>
            </div>
          </form>
        </section>

        <!-- 演示环境专用：把 mock 数据恢复到初始状态 -->
        <section v-if="isMock" class="card sheet">
          <h2 class="card__title">演示数据</h2>
          <p class="card__hint">
            当前数据保存在浏览器本地。重置后会回到初始的示例笔记与账号。
          </p>
          <div class="form__actions">
            <AppButton variant="ghost" :loading="resetting" @click="resetDemo">重置演示数据</AppButton>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppField from '@/components/AppField.vue'
import AppButton from '@/components/AppButton.vue'
import { userApi, adminApi } from '@/api/index.js'
import { useUserStore } from '@/stores/user.js'
import { useToastStore } from '@/stores/toast.js'
import { isMock } from '@/api/request.js'

const router = useRouter()
const userStore = useUserStore()
const toast = useToastStore()

const initial = computed(() => (userStore.displayName || '？').slice(0, 1))
const avatarUrl = computed(() => userStore.userInfo?.avatar || '')
const currentEmail = computed(() => userStore.userInfo?.email || '')

/** 按「字符」计数：中文昵称 3 个字就是 3，不是 9 个字节 */
function count(value) {
  return [...String(value || '')].length
}

/* ---------- 头像上传 ---------- */
const fileInput = ref(null)
const uploading = ref(false)

// 与后端 AvatarService 保持一致；前端先拦一道，省得白传一趟
const ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/webp']
const MAX_BYTES = 2 * 1024 * 1024

function pickAvatar() {
  if (uploading.value) return
  fileInput.value?.click()
}

async function onAvatarChange(event) {
  const file = event.target.files?.[0]
  // 选完立刻清空 input，否则同一个文件第二次选不会触发 change
  event.target.value = ''
  if (!file) return

  if (!ALLOWED_TYPES.includes(file.type)) {
    toast.error('只支持 PNG / JPG / WebP 格式的图片')
    return
  }
  if (file.size > MAX_BYTES) {
    toast.error('图片不能超过 2MB')
    return
  }

  uploading.value = true
  try {
    const { data } = await userApi.uploadAvatar(file)
    userStore.patchUserInfo(data)
    toast.success('头像已更新')
  } catch (err) {
    toast.error(err.message)
  } finally {
    uploading.value = false
  }
}

/* ---------- 资料 ---------- */
const profile = reactive({
  nickname: userStore.userInfo?.nickname || '',
  bio: userStore.userInfo?.bio || ''
})
const profileErrors = reactive({ nickname: '' })
const savingProfile = ref(false)

async function saveProfile() {
  profileErrors.nickname = profile.nickname.trim() ? '' : '昵称不能为空'
  if (count(profile.nickname.trim()) > 20) profileErrors.nickname = '昵称最多 20 个字符'
  if (profileErrors.nickname) return

  savingProfile.value = true
  try {
    const { data } = await userApi.updateProfile({
      nickname: profile.nickname.trim(),
      bio: profile.bio.trim()
    })
    userStore.patchUserInfo(data)
    toast.success('资料已保存')
  } catch (err) {
    toast.error(err.message)
  } finally {
    savingProfile.value = false
  }
}

/* ---------- 绑定邮箱 ---------- */
const bindOpen = ref(false)
const bind = reactive({ email: '', code: '' })
const bindErrors = reactive({ email: '', code: '' })
const sendingCode = ref(false)
const binding = ref(false)
const codeHint = ref('')
const countdown = ref(0)
let timer = null

function openBind() {
  bindOpen.value = true
  bind.email = ''
  bind.code = ''
  bindErrors.email = ''
  bindErrors.code = ''
  codeHint.value = ''
}

function closeBind() {
  bindOpen.value = false
}

function startCountdown(seconds) {
  countdown.value = seconds
  clearInterval(timer)
  timer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) clearInterval(timer)
  }, 1000)
}

onUnmounted(() => clearInterval(timer))

async function sendBindCode() {
  const email = bind.email.trim()
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    bindErrors.email = '邮箱格式不正确'
    return
  }
  bindErrors.email = ''

  sendingCode.value = true
  try {
    const { data } = await userApi.sendBindCode(email)
    codeHint.value = data?.hint || '验证码已发送'
    // 本地没配 SMTP 时后端会把码直接返回，直接填进输入框省一次手输
    if (data?.code) {
      bind.code = data.code
      codeHint.value = `${data.hint}（已自动填入）`
    }
    startCountdown(data?.cooldown || 60)
  } catch (err) {
    bindErrors.email = err.message
  } finally {
    sendingCode.value = false
  }
}

async function confirmBind() {
  const email = bind.email.trim()
  const code = bind.code.trim()
  bindErrors.email = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? '' : '邮箱格式不正确'
  bindErrors.code = /^\d{6}$/.test(code) ? '' : '请输入 6 位数字验证码'
  if (bindErrors.email || bindErrors.code) return

  binding.value = true
  try {
    const { data } = await userApi.bindEmail({ email, code })
    userStore.patchUserInfo(data)
    toast.success('邮箱已绑定')
    bindOpen.value = false
    clearInterval(timer)
    countdown.value = 0
  } catch (err) {
    bindErrors.code = err.message
  } finally {
    binding.value = false
  }
}

/* ---------- 密码 ---------- */
const password = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const passwordErrors = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const savingPassword = ref(false)

async function savePassword() {
  passwordErrors.oldPassword = password.oldPassword ? '' : '请输入原密码'
  passwordErrors.newPassword = password.newPassword.length >= 6 ? '' : '新密码至少 6 位'
  passwordErrors.confirm = password.confirm === password.newPassword ? '' : '两次输入不一致'
  if (Object.values(passwordErrors).some(Boolean)) return

  savingPassword.value = true
  try {
    await userApi.changePassword({
      oldPassword: password.oldPassword,
      newPassword: password.newPassword
    })
    password.oldPassword = ''
    password.newPassword = ''
    password.confirm = ''
    toast.success('密码已更新')
  } catch (err) {
    passwordErrors.oldPassword = err.message
  } finally {
    savingPassword.value = false
  }
}

/* ---------- 演示数据重置 ---------- */
const resetting = ref(false)

async function resetDemo() {
  if (!window.confirm('重置后当前的笔记与修改都会丢失，确定吗？')) return
  resetting.value = true
  try {
    await adminApi.resetMock()
    await userStore.logout()
    toast.success('演示数据已重置，请重新登录')
    router.replace('/login')
  } catch (err) {
    toast.error(err.message)
  } finally {
    resetting.value = false
  }
}
</script>

<style scoped>
.page {
  padding: var(--sp-5) var(--sp-6);
}

.cols {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: var(--sp-4);
  align-items: start;
}

.side {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.card {
  padding: var(--sp-5);
}

.card__title {
  font-size: var(--fs-md);
  padding-bottom: var(--sp-2);
  margin-bottom: var(--sp-4);
  border-bottom: 1px solid var(--rule);
}

.card__hint {
  font-size: var(--fs-xs);
  line-height: 1.8;
  color: var(--ink-3);
}

.card__hint--ok {
  color: var(--accent-ink);
}

/* ---------------- 头像 ---------------- */
.who {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.avatar {
  position: relative;
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  flex: 0 0 64px;
  padding: 0;
  border: 1px solid var(--rule);
  border-radius: 50%;
  overflow: hidden;
  background: var(--accent-soft);
  color: var(--accent-ink);
  font-family: var(--font-serif);
  font-size: var(--fs-lg);
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.avatar:hover:not(:disabled),
.avatar:focus-visible {
  border-color: var(--accent);
}

.avatar:disabled {
  cursor: progress;
}

.avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar__text {
  line-height: 1;
}

/* 悬停时压一层遮罩，提示「这里可以点」；触屏没有 hover，靠 title 与文案兜底 */
.avatar__mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  background: rgb(45 74 114 / 62%);
  color: #fff;
  font-family: var(--font-sans);
  font-size: 11px;
  letter-spacing: 0.05em;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.avatar:hover .avatar__mask,
.avatar:focus-visible .avatar__mask {
  opacity: 1;
}

.who__meta {
  min-width: 0;
}

.who__name {
  font-family: var(--font-serif);
  font-size: var(--fs-base);
  color: var(--ink);
}

.who__role {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.who__hint {
  margin-top: 2px;
  font-size: 11px;
  color: var(--ink-3);
}

/* ---------------- 邮箱 ---------------- */
.email {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-3) 0;
  margin-bottom: var(--sp-1);
  border-bottom: 1px solid var(--rule);
  font-size: var(--fs-sm);
  word-break: break-all;
}

.email__empty {
  color: var(--ink-3);
}

.link {
  flex: 0 0 auto;
  padding: 0;
  border: 0;
  background: none;
  color: var(--accent);
  font-size: var(--fs-xs);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: var(--sp-2);
  align-items: end;
}

/* ---------------- 其余 ---------------- */
.form {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
}

.form__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--sp-1);
}

.quota {
  font-family: var(--font-serif);
  font-size: 40px;
  line-height: 1.1;
  color: var(--accent);
  margin-bottom: var(--sp-2);
}

.quota.is-low {
  color: var(--danger);
}

.quota__unit {
  font-family: var(--font-sans);
  font-size: var(--fs-sm);
  color: var(--ink-3);
  margin-left: 4px;
}

@media (max-width: 900px) {
  .cols {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
