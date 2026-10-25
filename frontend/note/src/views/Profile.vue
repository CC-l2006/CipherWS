<template>
  <div class="page">
    <div class="page-title">
      <h1>个人资料</h1>
    </div>
    <p class="page-sub">这些信息会显示在你自己的页面上。邮箱与密码属于账号安全，在「设置」里改。</p>

    <!-- 单列自上而下，不再左右并排 -->
    <div class="stack">
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

      <!-- AI 额度：单列时用「左说明 + 右数字」的一行，比巨大的数字更耐看 -->
      <section class="card sheet">
        <h2 class="card__title">AI 额度</h2>
        <div class="stat">
          <div class="stat__text">
            <p class="stat__label">剩余 AI 总结次数</p>
            <p class="card__hint">每调用一次 AI 总结消耗 1 次。额度用完后需要联系管理员分配。</p>
          </div>
          <p class="stat__value" :class="{ 'is-low': userStore.aiQuota <= 2 }">
            {{ userStore.aiQuota }}<span class="stat__unit">次</span>
          </p>
        </div>
      </section>

      <!-- 账号与安全：真正的操作都在设置页 -->
      <section class="card sheet">
        <h2 class="card__title">账号与安全</h2>
        <p class="card__hint">
          登录邮箱、更换绑定邮箱、修改密码都在设置页 —— 这些操作要用邮箱验证码，所以单独放一页。
        </p>
        <dl class="meta">
          <dt>登录邮箱</dt>
          <dd class="meta__mono">{{ userStore.userInfo?.email || '尚未绑定' }}</dd>
          <dt>登录密码</dt>
          <dd>已设置（••••••）</dd>
        </dl>
        <div class="form__actions">
          <RouterLink to="/settings" class="btn-link">前往设置</RouterLink>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppField from '@/components/AppField.vue'
import AppButton from '@/components/AppButton.vue'
import { userApi } from '@/api/index.js'
import { useUserStore } from '@/stores/user.js'
import { useToastStore } from '@/stores/toast.js'

const userStore = useUserStore()
const toast = useToastStore()

const initial = computed(() => (userStore.displayName || '？').slice(0, 1))
const avatarUrl = computed(() => userStore.userInfo?.avatar || '')

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
</script>

<style scoped>
/* 与其它页面一致：整页留白 */
.page {
  padding: var(--sp-5) var(--sp-6);
}

/* 单列自上而下。
   宽度限制：表单行太长不好读，所以给一个阅读宽度；
   居中（margin-inline: auto）让两侧留白相等，不然 760 摆在 1040 的内容区里
   右边会空出一大块，看着偏。
   想铺满内容区：删掉下面三行里的 max-width 与 margin-inline 即可。 */
.stack {
  margin-top: var(--sp-4);
  max-width: 880px;
  margin-inline: auto;
}

/* .sheet 只负责「纸」的外观，内边距由页面自己给 */
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

/* ---------------- AI 额度：一行左右分布 ---------------- */
.stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
}

.stat__text {
  min-width: 0;
}

.stat__label {
  font-size: var(--fs-sm);
  color: var(--ink);
  margin-bottom: 4px;
}

.stat__value {
  flex: 0 0 auto;
  font-family: var(--font-serif);
  font-size: 34px;
  line-height: 1;
  color: var(--accent);
}

.stat__value.is-low {
  color: var(--danger);
}

.stat__unit {
  font-family: var(--font-sans);
  font-size: var(--fs-sm);
  color: var(--ink-3);
  margin-left: 4px;
}

/* ---------------- 账号与安全 ---------------- */
.meta {
  display: grid;
  grid-template-columns: 5.5em minmax(0, 1fr);
  gap: 6px var(--sp-3);
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
}

.meta dt {
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.meta dd {
  margin: 0;
  color: var(--ink);
  word-break: break-all;
}

.meta__mono {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
}

.btn-link {
  display: inline-flex;
  align-items: center;
  padding: 7px 14px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  color: var(--accent-ink);
  font-size: var(--fs-sm);
  text-decoration: none;
  transition: border-color 0.15s ease, background 0.15s ease;
}

.btn-link:hover {
  border-color: var(--accent);
  background: var(--accent-soft);
  text-decoration: none;
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
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

@media (max-width: 720px) {
  .page {
    padding: var(--sp-4) var(--sp-3);
  }

  .card {
    padding: var(--sp-4) var(--sp-3);
  }

  .stat {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--sp-2);
  }
}
</style>
