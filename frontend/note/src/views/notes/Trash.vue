<template>
  <div class="page">
    <div class="page-title">
      <h1>回收站</h1>
      <div class="row">
        <RouterLink to="/notes" class="back">← 返回笔记</RouterLink>
        <AppButton v-if="rows.length" variant="danger" size="sm" @click="showClear = true">
          清空回收站
        </AppButton>
      </div>
    </div>

    <p class="page-sub">
      删除的笔记会先进回收站，可以恢复。清空回收站后无法找回。
    </p>

    <div class="stack">
      <AppTable
        :columns="columns"
        :rows="rows"
        :loading="loading"
        empty-title="回收站是空的"
        empty-description="被删除的笔记会出现在这里，可以随时恢复。"
      >
        <template #cell-title="{ row }">
          <span class="cell-title">{{ row.title }}</span>
        </template>
        <template #cell-updatedAt="{ row }">
          <span class="mono">{{ formatDate(row.updatedAt) }}</span>
        </template>
        <template #cell-actions="{ row }">
          <div class="row">
            <AppButton variant="quiet" size="sm" @click="restore(row)">恢复</AppButton>
            <AppButton variant="quiet" size="sm" class="danger-text" @click="askPurge(row)">
              永久删除
            </AppButton>
          </div>
        </template>
      </AppTable>
    </div>

    <!-- 永久删除单篇 -->
    <AppModal v-model="showPurge" title="永久删除这篇笔记？">
      <p class="confirm-text">
        《{{ target?.title }}》将被彻底删除，<strong>无法恢复</strong>。
      </p>
      <template #footer>
        <AppButton @click="showPurge = false">取消</AppButton>
        <AppButton variant="primary" :loading="working" @click="confirmPurge">确认删除</AppButton>
      </template>
    </AppModal>

    <!-- 清空回收站 -->
    <AppModal v-model="showClear" title="清空回收站？">
      <p class="confirm-text">
        回收站里的 <strong>{{ rows.length }}</strong> 篇笔记会被彻底删除，<strong>无法恢复</strong>。
      </p>
      <template #footer>
        <AppButton @click="showClear = false">取消</AppButton>
        <AppButton variant="primary" :loading="working" @click="confirmClear">确认清空</AppButton>
      </template>
    </AppModal>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import AppTable from '@/components/AppTable.vue'
import AppButton from '@/components/AppButton.vue'
import AppModal from '@/components/AppModal.vue'
import { noteApi } from '@/api/index.js'
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()

const rows = ref([])
const loading = ref(false)
const working = ref(false)
const showPurge = ref(false)
const showClear = ref(false)
const target = ref(null)

const columns = [
  { key: 'title', label: '标题' },
  { key: 'updatedAt', label: '删除时间', width: '160px' },
  { key: 'actions', label: '操作', width: '180px' }
]

async function load() {
  loading.value = true
  try {
    const { data } = await noteApi.trash({ size: 100 })
    rows.value = data
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

async function restore(row) {
  try {
    await noteApi.restore(row.id)
    toast.success(`已恢复《${row.title}》`)
    await load()
  } catch (err) {
    toast.error(err.message)
  }
}

function askPurge(row) {
  target.value = row
  showPurge.value = true
}

async function confirmPurge() {
  working.value = true
  try {
    await noteApi.remove(target.value.id, true)
    toast.success('已永久删除')
    showPurge.value = false
    await load()
  } catch (err) {
    toast.error(err.message)
  } finally {
    working.value = false
  }
}

async function confirmClear() {
  working.value = true
  try {
    await noteApi.clearTrash()
    toast.success('回收站已清空')
    showClear.value = false
    await load()
  } catch (err) {
    toast.error(err.message)
  } finally {
    working.value = false
  }
}

function formatDate(iso) {
  return iso ? new Date(iso).toLocaleString('zh-CN', { hour12: false }) : '—'
}

onMounted(load)
</script>

<style scoped>
.page {
  padding: var(--sp-5) var(--sp-6);
}

.cell-title {
  font-family: var(--font-serif);
  color: var(--ink);
}

.danger-text {
  color: var(--danger);
}

.back {
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.confirm-text {
  font-size: var(--fs-sm);
  line-height: 1.8;
  color: var(--ink-2);
}

.confirm-text strong {
  color: var(--danger);
  font-weight: 600;
}
</style>
