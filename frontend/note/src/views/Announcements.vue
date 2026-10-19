<template>
  <div class="page">
    <div class="page-title">
      <h1>系统公告</h1>
    </div>

    <p class="page-sub">管理员发布的公告。停用的公告不会出现在这里。</p>

    <div v-if="loading" class="loading-rule" />

    <AppEmpty
      v-else-if="!items.length"
      title="暂时没有公告"
      description="有新的系统通知时会显示在这里。"
    />

    <ul v-else class="list">
      <li v-for="a in items" :key="a.id" class="notice sheet">
        <header class="notice__head">
          <h2 class="notice__title">{{ a.title }}</h2>
          <time class="notice__time">{{ formatDate(a.createdAt) }}</time>
        </header>
        <p class="notice__body">{{ a.content }}</p>
      </li>
    </ul>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AppEmpty from '@/components/AppEmpty.vue'
import { announcementApi } from '@/api/index.js'
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()
const items = ref([])
const loading = ref(false)

function formatDate(iso) {
  return iso ? new Date(iso).toLocaleString('zh-CN', { hour12: false }) : ''
}

onMounted(async () => {
  loading.value = true
  try {
    const { data } = await announcementApi.list()
    items.value = data
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page {
  padding: var(--sp-5) var(--sp-6);
}

.list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-3);
  max-width: 760px;
}

.notice {
  padding: var(--sp-4) var(--sp-5);
}

.notice__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-3);
  padding-bottom: var(--sp-2);
  border-bottom: 1px solid var(--rule);
}

.notice__title {
  font-size: var(--fs-lg);
}

.notice__time {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  white-space: nowrap;
}

.notice__body {
  margin-top: var(--sp-3);
  font-family: var(--font-serif);
  font-size: var(--fs-base);
  line-height: 1.9;
  color: var(--ink-2);
  white-space: pre-wrap;
}
</style>
