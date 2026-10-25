<template>
  <div class="page">
    <div class="page-title">
      <h1>笔记巡检</h1>
      <div class="row">
        <AppButton variant="ghost" size="sm" :loading="loading" @click="load">刷新</AppButton>
      </div>
    </div>

    <p class="page-sub">仅巡检，不可修改用户笔记正文。</p>

    <form class="toolbar" @submit.prevent="onSearch">
      <div class="toolbar__search">
        <AppField v-model="keyword" placeholder="搜索标题 / 作者" />
      </div>
      <AppButton variant="primary" size="sm" type="submit">搜索</AppButton>
    </form>

    <div class="table-scroll">
      <AppTable :columns="columns" :rows="rows" :loading="loading">
        <template #cell-title="{ row }">
          <span class="title-cell">{{ row.title }}</span>
        </template>

        <template #cell-tags="{ row }">
          <div v-if="row.tags?.length" class="tags">
            <AppTag v-for="tag in row.tags" :key="tag.id" tone="neutral">{{ tag.name }}</AppTag>
          </div>
          <span v-else class="muted">—</span>
        </template>

        <template #cell-status="{ row }">
          <AppTag v-if="row.flagged" tone="danger">已标记</AppTag>
          <AppTag v-else tone="neutral">正常</AppTag>
        </template>

        <template #cell-actions="{ row }">
          <div class="actions">
            <AppButton variant="quiet" size="sm" @click="openNote(row)">查看正文</AppButton>
            <AppButton
              variant="quiet"
              size="sm"
              :loading="busyId === row.id"
              @click="toggleFlag(row)"
            >
              {{ row.flagged ? '取消违规' : '标记违规' }}
            </AppButton>
          </div>
        </template>
      </AppTable>
    </div>

    <AppPager v-model:page="page" :total="totalCount" :size="10" />

    <!-- 巡检只读：这里刻意没有输入框，正文由用户自己维护 -->
    <AppModal v-model="viewModal" title="笔记正文（只读）" width="720px">
      <div v-if="viewing" class="stack">
        <div>
          <h2 class="view__title">{{ viewing.title }}</h2>
          <p class="small muted view__meta">
            {{ viewing.authorName }} · 最后更新 {{ viewing.updatedAt }}
          </p>
        </div>

        <div v-if="viewing.tags?.length" class="tags">
          <AppTag v-for="tag in viewing.tags" :key="tag.id" tone="neutral">{{ tag.name }}</AppTag>
        </div>

        <div class="view__body">{{ viewing.content || '（正文为空）' }}</div>

        <p class="small muted">巡检页面不提供编辑能力，如需处理违规内容请直接标记。</p>
      </div>

      <template #footer>
        <AppButton variant="ghost" size="sm" @click="viewModal = false">关闭</AppButton>
        <AppButton
          v-if="viewing"
          :variant="viewing.flagged ? 'ghost' : 'danger'"
          size="sm"
          :loading="busyId === viewing.id"
          @click="toggleFlag(viewing)"
        >
          {{ viewing.flagged ? '取消违规标记' : '标记为违规' }}
        </AppButton>
      </template>
    </AppModal>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
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
  { key: 'title', label: '标题', width: '240px' },
  { key: 'authorName', label: '作者', width: '120px' },
  { key: 'tags', label: '标签', width: '180px' },
  { key: 'updatedAt', label: '更新时间', width: '170px' },
  { key: 'status', label: '标记', width: '96px' },
  { key: 'actions', label: '操作', width: '170px' }
]

const keyword = ref('')
const page = ref(1)
const rows = ref([])
const totalCount = ref(0)
const loading = ref(false)

const viewModal = ref(false)
const viewing = ref(null)
const busyId = ref(null)

async function load() {
  loading.value = true
  try {
    const { data, total } = await adminApi.notes({
      keyword: keyword.value,
      page: page.value,
      size: 10
    })
    rows.value = data
    totalCount.value = total
    // 打开着的弹窗跟着列表刷新，否则标记后弹窗里还是旧状态
    if (viewing.value) {
      viewing.value = data.find((n) => n.id === viewing.value.id) || viewing.value
    }
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  if (page.value === 1) load()
  else page.value = 1
}

function openNote(row) {
  viewing.value = row
  viewModal.value = true
}

async function toggleFlag(row) {
  busyId.value = row.id
  const next = !row.flagged
  try {
    await adminApi.flagNote(row.id, next)
    toast.success(next ? '已标记为违规笔记' : '已取消违规标记')
    await load()
    // 笔记可能因筛选而离开当前列表，弹窗里单独同步一次，避免显示旧状态
    if (viewing.value?.id === row.id) {
      viewing.value = { ...viewing.value, flagged: next }
    }
  } catch (err) {
    toast.error(err.message)
  } finally {
    busyId.value = null
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

.toolbar {
  display: flex;
  align-items: flex-end;
  gap: var(--sp-2);
}

.toolbar__search {
  width: 280px;
  max-width: 100%;
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

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-1);
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--sp-1);
  white-space: nowrap;
}

.view__title {
  font-size: var(--fs-lg);
}

.view__meta {
  margin-top: var(--sp-1);
}

/* 保留用户原文的换行与空格：巡检要看到「原样」的正文 */
.view__body {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  line-height: 1.9;
  color: var(--ink-2);
  padding: var(--sp-4);
  background: var(--paper);
  border: 1px solid var(--rule);
  border-radius: var(--r-sm);
  max-height: 46vh;
  overflow-y: auto;
}

@media (max-width: 820px) {
  .toolbar {
    flex-wrap: wrap;
  }

  .toolbar__search {
    width: 100%;
  }

  .table-scroll :deep(table) {
    min-width: 860px;
  }
}
</style>
