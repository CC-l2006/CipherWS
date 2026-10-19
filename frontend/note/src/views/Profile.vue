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
          <span class="avatar" aria-hidden="true">{{ initial }}</span>
          <div>
            <p class="who__name">{{ userStore.displayName }}</p>
            <p class="who__role">{{ userStore.isAdmin ? '管理员' : '普通用户' }}</p>
          </div>
        </div>

        <form class="form" @submit.prevent="saveProfile">
          <AppField v-model="profile.nickname" label="昵称" :maxlength="20" :error="profileErrors.nickname" required />
          <AppField v-model="profile.email" label="绑定邮箱" type="email" :error="profileErrors.email" />
          <AppField
            v-model="profile.bio"
            label="个人简介"
            type="textarea"
            :rows="3"
            :maxlength="120"
            hint="最多 120 个字符"
          />
          <div class="form__actions">
            <AppButton type="submit" variant="primary" :loading="savingProfile">保存资料</AppButton>
          </div>
        </form>
      </section>

      <!-- 密码与额度 -->
      <div class="side">
        <section class="card sheet">
          <h2 class="card__title">AI 额度</h2>
          <p class="quota" :class="{ 'is-low': userStore.aiQuota <= 2 }">
            {{ userStore.aiQuota }}<span class="quota__unit">次</span>
          </p>
          <p class="card__hint">
            每调用一次 AI 总结消耗 1 次额度。额度用完后需要联系管理员分配。
          </p>
        </section>

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
import { computed, reactive, ref } from 'vue'
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

/* ---------- 资料 ---------- */
const profile = reactive({
  nickname: userStore.userInfo?.nickname || '',
  email: userStore.userInfo?.email || '',
  bio: userStore.userInfo?.bio || ''
})
const profileErrors = reactive({ nickname: '', email: '' })
const savingProfile = ref(false)

async function saveProfile() {
  profileErrors.nickname = profile.nickname.trim() ? '' : '昵称不能为空'
  profileErrors.email =
    !profile.email.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(profile.email.trim())
      ? ''
      : '邮箱格式不正确'
  if (profileErrors.nickname || profileErrors.email) return

  savingProfile.value = true
  try {
    const { data } = await userApi.updateProfile({
      nickname: profile.nickname.trim(),
      email: profile.email.trim(),
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

.who {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-bottom: var(--sp-4);
}

.avatar {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--accent-soft);
  color: var(--accent-ink);
  font-family: var(--font-serif);
  font-size: var(--fs-lg);
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
