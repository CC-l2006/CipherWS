<template>
  <div class="page">
    <div class="page-title">
      <h1>公告管理</h1>
      <div class="row">
        <AppButton variant="ghost" size="sm" :loading="loading" @click="load">刷新</AppButton>
        <AppButton variant="primary" size="sm" @click="openCreate">新建公告</AppButton>
      </div>
    </div>

    <div class="table-scroll">
      <AppTable :columns="columns" :rows="rows" :loading="loading">
        <template #cell-title="{ row }">
          <span class="title-cell">{{ row.title }}</span>
        </template>

        <template #cell-content="{ row }">
          <span class="summary">{{ summary(row.content) }}</span>
        </template>

        <template #cell-enabled="{ row }">
          <AppTag :tone="row.enabled ? 'ok' : 'neutral'">
            {{ row.enabled ? '已启用' : '已下线' }}
          </AppTag>
        </template>

        <template #cell-actions="{ row }">
          <div class="actions">
            <AppButton variant="quiet" size="sm" @click="openEdit(row)">编辑</AppButton>
            <AppButton variant="quiet" size="sm" @click="openRemove(row)">删除</AppButton>
          </div>
        </template>
      </AppTable>
    </div>

    <AppPager v-model:page="page" :total="totalCount" :size="10" />

    <AppModal v-model="formModal" :title="form.id ? '编辑公告' : '发布公告'" width="560px">
      <div class="stack">
        <AppField
          v-model="form.title"
          label="标题"
          placeholder="例如：系统维护通知"
          :maxlength="80"
          :error="titleError"
        />

        <AppField
          v-model="form.content"
          label="内容"
          type="textarea"
          :rows="6"
          :maxlength="1000"
          :error="contentError"
          placeholder="公告正文，用户登录后可在公告页看到"
        />

        <label class="check">
          <input v-model="form.enabled" type="checkbox" />
          <span>立即启用（用户登录后可见）</span>
        </label>
      </div>

      <template #footer>
        <AppButton variant="ghost" size="sm" @click="formModal = false">取消</AppButton>
        <AppButton variant="primary" size="sm" :loading="saving" @click="submit">
          {{ form.id ? '保存修改' : '发布' }}
        </AppButton>
      </template>
    </AppModal>

    <AppModal v-model="removeModal" title="删除公告">
      <p v-if="removeTarget">
        确定要删除《{{ removeTarget.title }}》吗？删除后用户将无法再看到这条公告，且不可恢复。
      </p>

      <template #footer>
        <AppButton variant="ghost" size="sm" @click="removeModal = false">取消</AppButton>
        <AppButton variant="danger" size="sm" :loading="removing" @click="confirmRemove">
          确认删除
        </AppButton>
      </template>
    </AppModal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import AppButton from '@/components/AppButton.vue'
import AppField from '@/components/AppField.vue'
import AppModal from '@/components/AppModal.vue'
import AppPager from '@/components/AppPager.vue'
import AppTable from '@/components/AppTable.vue'
import AppTag from '@/components/AppTag.vue'
import { adminApi } from '@/api/index.js'
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()

const columns = [
  { key: 'title', label: '标题', width: '220px' },
  { key: 'content', label: '内容摘要' },
  { key: 'enabled', label: '状态', width: '96px' },
  { key: 'createdAt', label: '发布时间', width: '170px' },
  { key: 'actions', label: '操作', width: '120px' }
]

const page = ref(1)
const rows = ref([])
const totalCount = ref(0)
const loading = ref(false)

const formModal = ref(false)
const saving = ref(false)
const titleError = ref('')
const contentError = ref('')
const form = reactive({ id: null, title: '', content: '', enabled: true })

const removeModal = ref(false)
const removeTarget = ref(null)
const removing = ref(false)

function summary(content) {
  const text = String(content || '').replace(/\s+/g, ' ').trim()
  if (!text) return '—'
  return text.length > 40 ? `${text.slice(0, 40)}…` : text
}

async function load() {
  loading.value = true
  try {
    const { data, total } = await adminApi.announcements({ page: page.value, size: 10 })
    rows.value = data
    totalCount.value = total
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

/** 写操作后回到第 1 页，新公告和改过的公告都在第一页最显眼处 */
function reload() {
  if (page.value === 1) load()
  else page.value = 1
}

function openCreate() {
  form.id = null
  form.title = ''
  form.content = ''
  form.enabled = true
  titleError.value = ''
  contentError.value = ''
  formModal.value = true
}

function openEdit(row) {
  form.id = row.id
  form.title = row.title
  form.content = row.content
  form.enabled = row.enabled
  titleError.value = ''
  contentError.value = ''
  formModal.value = true
}

async function submit() {
  titleError.value = ''
  contentError.value = ''

  if (!form.title.trim()) {
    titleError.value = '标题不能为空'
    return
  }
  if (!form.content.trim()) {
    contentError.value = '内容不能为空'
    return
  }

  const payload = {
    title: form.title.trim(),
    content: form.content.trim(),
    enabled: form.enabled
  }

  saving.value = true
  try {
    if (form.id) {
      await adminApi.updateAnnouncement(form.id, payload)
      toast.success('公告已更新')
    } else {
      await adminApi.createAnnouncement(payload)
      toast.success(form.enabled ? '公告已发布' : '公告已保存（未启用）')
    }
    formModal.value = false
    reload()
  } catch (err) {
    toast.error(err.message)
  } finally {
    saving.value = false
  }
}

function openRemove(row) {
  removeTarget.value = row
  removeModal.value = true
}

async function confirmRemove() {
  const row = removeTarget.value
  if (!row) return

  removing.value = true
  try {
    await adminApi.removeAnnouncement(row.id)
    toast.success('公告已删除')
    removeModal.value = false
    reload()
  } catch (err) {
    toast.error(err.message)
  } finally {
    removing.value = false
  }
}

watch(page, load)

onMounted(load)
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

.table-scroll {
  overflow-x: auto;
}

.title-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--ink);
}

.summary {
  color: var(--ink-3);
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  white-space: nowrap;
}

.check {
  display: inline-flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-sm);
  color: var(--ink-2);
  cursor: pointer;
}

.check input {
  width: 15px;
  height: 15px;
  accent-color: var(--accent);
  cursor: pointer;
}

@media (max-width: 820px) {
  .table-scroll :deep(table) {
    min-width: 760px;
  }
}
</style>
