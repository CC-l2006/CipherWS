<template>
  <div class="page">
    <div class="page-title">
      <h1>AI 参数配置</h1>
      <div class="row">
        <AppButton variant="ghost" size="sm" :loading="loading" @click="load">重新读取</AppButton>
      </div>
    </div>

    <div class="sheet form-sheet">
      <div class="stack">
        <AppField
          v-model="form.endpoint"
          label="接口地址 endpoint"
          placeholder="https://api.example.com/v1/chat/completions"
          hint="需以 http:// 或 https:// 开头"
        />

        <AppField v-model="form.model" label="模型名 model" placeholder="deepseek-chat" />

        <AppField
          v-model="form.maxChars"
          label="单次总结最大字数 maxChars"
          type="number"
          :error="maxCharsError"
          hint="取 100 - 20000 的整数，超出部分截断后再送 AI"
        />

        <AppField
          v-model="form.apiKey"
          label="接口密钥 apiKey"
          type="password"
          :placeholder="keyPlaceholder"
          hint="出于安全考虑，密钥不会明文回显"
        />

        <div class="form-actions">
          <AppButton variant="primary" size="md" :loading="saving" @click="save">保存配置</AppButton>
          <span v-if="updatedAt" class="small muted">上次更新：{{ updatedAt }}</span>
        </div>
      </div>
    </div>

    <p class="small muted">
      需求 6.4.2「AI 接口参数后台可配置，无需改代码重启服务」；接口密钥只显示掩码，保存后也不会明文回显。
    </p>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import AppButton from '@/components/AppButton.vue'
import AppField from '@/components/AppField.vue'
import { adminApi } from '@/api/index.js'
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()

const form = reactive({
  endpoint: '',
  model: '',
  maxChars: '',
  // 留空 = 不修改，读取时永远不填真值，避免密钥在前端停留
  apiKey: ''
})

const apiKeyMasked = ref('')
const updatedAt = ref('')
const loading = ref(false)
const saving = ref(false)
const maxCharsError = ref('')

const keyPlaceholder = computed(() => {
  if (!apiKeyMasked.value) return '留空表示暂不设置密钥'
  return `当前：${apiKeyMasked.value}（留空表示不修改）`
})

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.aiConfig()
    form.endpoint = data.endpoint || ''
    form.model = data.model || ''
    form.maxChars = data.maxChars != null ? String(data.maxChars) : ''
    form.apiKey = ''
    apiKeyMasked.value = data.apiKeyMasked || ''
    updatedAt.value = data.updatedAt || ''
    maxCharsError.value = ''
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

async function save() {
  maxCharsError.value = ''

  if (!form.endpoint.trim()) {
    toast.error('接口地址不能为空')
    return
  }
  if (!form.model.trim()) {
    toast.error('模型名称不能为空')
    return
  }

  const maxChars = Number(form.maxChars)
  if (!Number.isInteger(maxChars) || maxChars < 100 || maxChars > 20000) {
    maxCharsError.value = '请输入 100 - 20000 之间的整数'
    return
  }

  const payload = {
    endpoint: form.endpoint.trim(),
    model: form.model.trim(),
    maxChars
  }
  // 密钥输入框留空表示沿用旧密钥，此时不提交该字段，后端不会覆盖
  if (form.apiKey.trim()) payload.apiKey = form.apiKey.trim()

  saving.value = true
  try {
    await adminApi.saveAiConfig(payload)
    toast.success('AI 参数已保存，下一次调用即生效')
    await load()
  } catch (err) {
    toast.error(err.message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.form-sheet {
  padding: var(--sp-6);
  width: 100%;
  max-width: 640px;
}

.form-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding-top: var(--sp-2);
}

@media (max-width: 820px) {
  .form-sheet {
    padding: var(--sp-4);
  }
}
</style>
