<template>
  <div class="page">
    <div class="page-title">
      <h1>操作日志</h1>
      <div class="row">
        <AppButton variant="ghost" size="sm" :loading="loading" @click="load">刷新</AppButton>
      </div>
    </div>

    <p class="page-sub">日志只读，不可删除（需求 4.7.4）。</p>

    <div class="tabs">
      <AppButton
        v-for="tab in TABS"
        :key="tab.value"
        :variant="type === tab.value ? 'primary' : 'ghost'"
        size="sm"
        @click="switchTab(tab.value)"
      >
        {{ tab.label }}
      </AppButton>
      <span class="small muted tabs__hint">{{ currentTab.hint }}</span>
    </div>

    <form class="filters" @submit.prevent="applyFilters">
      <div class="filters__search">
        <AppField v-model="keyword" placeholder="搜索操作类型 / 详情 / 操作人" />
      </div>

      <label class="date-field">
        <span class="date-field__label">起始</span>
        <input
          v-model="from"
          class="date-input"
          type="date"
          :max="to || undefined"
          @change="applyFilters"
        />
      </label>

      <span class="muted">至</span>

      <label class="date-field">
        <span class="date-field__label">结束</span>
        <input
          v-model="to"
          class="date-input"
          type="date"
          :min="from || undefined"
          @change="applyFilters"
        />
      </label>

      <AppButton variant="primary" size="sm" type="submit">查询</AppButton>
      <AppButton variant="ghost" size="sm" @click="resetFilters">重置</AppButton>
    </form>

    <div class="table-scroll">
      <AppTable :columns="columns" :rows="rows" :loading="loading">
        <template #cell-type="{ row }">
          <AppTag :tone="row.type === 'admin' ? 'accent' : 'neutral'">
            {{ row.type === 'admin' ? '管理员操作' : '用户操作' }}
          </AppTag>
        </template>

        <template #cell-ip="{ row }">
          <span class="mono">{{ row.ip || '—' }}</span>
        </template>
      </AppTable>
    </div>

    <AppPager v-model:page="page" :total="totalCount" :size="10" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import AppButton from '@/components/AppButton.vue'
import AppField from '@/components/AppField.vue'
import AppPager from '@/components/AppPager.vue'
import AppTable from '@/components/AppTable.vue'
import AppTag from '@/components/AppTag.vue'
import { adminApi } from '@/api/index.js'
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()

const TABS = [
  { value: 'user', label: '用户操作日志', hint: '登录、新建 / 删除笔记、调用 AI 总结等用户行为' },
  { value: 'admin', label: '管理员操作日志', hint: '禁用用户、修改配置、巡检笔记等管理员操作' }
]

const columns = [
  { key: 'createdAt', label: '时间', width: '170px' },
  { key: 'actorName', label: '操作人', width: '120px' },
  { key: 'type', label: '操作类型', width: '130px' },
  { key: 'detail', label: '详情' },
  { key: 'ip', label: 'IP', width: '130px' }
]

const type = ref('user')
const keyword = ref('')
const from = ref('')
const to = ref('')
const page = ref(1)
const rows = ref([])
const totalCount = ref(0)
const loading = ref(false)

const currentTab = computed(() => TABS.find((t) => t.value === type.value) || TABS[0])

async function load() {
  loading.value = true
  try {
    const { data, total } = await adminApi.logs({
      type: type.value,
      keyword: keyword.value,
      from: from.value,
      to: to.value,
      page: page.value,
      size: 10
    })
    rows.value = data
    totalCount.value = total
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

/** 筛选条件变了必须回第 1 页，否则可能停在一个已不存在的页码上 */
function applyFilters() {
  if (page.value === 1) load()
  else page.value = 1
}

function resetFilters() {
  keyword.value = ''
  from.value = ''
  to.value = ''
  applyFilters()
}

function switchTab(value) {
  if (type.value === value) return
  type.value = value
  applyFilters()
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

.tabs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.tabs__hint {
  margin-left: var(--sp-2);
}

.filters {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.filters__search {
  width: 260px;
  max-width: 100%;
}

.date-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.date-field__label {
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

/* 原生 date 控件沿用 AppField 的纸墨样式，避免为它单独包一层组件 */
.date-input {
  padding: 8px 10px;
  background: var(--sheet);
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  font-family: var(--font-sans);
  font-size: var(--fs-base);
  color: var(--ink);
  transition: border-color 0.15s ease;
}

.date-input:hover {
  border-color: var(--ink-3);
}

.date-input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.table-scroll {
  overflow-x: auto;
}

@media (max-width: 820px) {
  .filters__search {
    width: 100%;
  }

  .table-scroll :deep(table) {
    min-width: 860px;
  }
}
</style>
