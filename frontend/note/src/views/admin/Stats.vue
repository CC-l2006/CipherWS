<template>
  <div class="page">
    <div class="page-title">
      <h1>AI 调用统计</h1>
      <div class="row">
        <AppButton variant="ghost" size="sm" :loading="loading" @click="load">刷新</AppButton>
      </div>
    </div>

    <div class="figures">
      <div v-for="item in figures" :key="item.key" class="sheet figure">
        <span class="figure__value">{{ totals[item.key] ?? 0 }}</span>
        <span class="small muted">{{ item.label }}</span>
      </div>
    </div>

    <section class="block">
      <div class="row-between block__head">
        <h2>近 7 天 AI 调用</h2>
        <span class="small muted">合计 {{ weekTotal }} 次</span>
      </div>

      <div v-if="daily.length" class="chart">
        <div v-for="day in daily" :key="day.date" class="chart__col">
          <span class="chart__count">{{ day.count }}</span>
          <div class="chart__plot">
            <div
              class="chart__bar"
              :class="{ 'is-empty': !day.count }"
              :style="{ height: barHeight(day.count) }"
              :title="`${day.date}：${day.count} 次`"
            />
          </div>
          <span class="chart__date">{{ shortDate(day.date) }}</span>
        </div>
      </div>
      <p v-else class="small muted">暂无调用记录</p>
    </section>

    <section class="block">
      <div class="row-between block__head">
        <h2>用户调用排行</h2>
        <span class="small muted">按 AI 调用次数取前 5 名</span>
      </div>

      <div class="table-scroll">
        <AppTable :columns="columns" :rows="topUsers" row-key="userId" :loading="loading">
          <template #cell-rank="{ row }">
            <span class="rank">{{ topUsers.indexOf(row) + 1 }}</span>
          </template>
        </AppTable>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import AppButton from '@/components/AppButton.vue'
import AppTable from '@/components/AppTable.vue'
import { adminApi } from '@/api/index.js'
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()

const figures = [
  { key: 'users', label: '用户数' },
  { key: 'notes', label: '笔记数' },
  { key: 'aiCalls', label: 'AI 调用次数' },
  { key: 'flaggedNotes', label: '被标记笔记数' }
]

const columns = [
  { key: 'rank', label: '名次', width: '72px' },
  { key: 'name', label: '用户' },
  { key: 'count', label: 'AI 调用次数', width: '130px', align: 'right' }
]

const loading = ref(false)
const daily = ref([])
const topUsers = ref([])
const totals = ref({ users: 0, notes: 0, aiCalls: 0, flaggedNotes: 0 })

const weekTotal = computed(() => daily.value.reduce((sum, d) => sum + (d.count || 0), 0))
const maxCalls = computed(() => Math.max(1, ...daily.value.map((d) => d.count || 0)))

function barHeight(count) {
  return `${Math.round(((count || 0) / maxCalls.value) * 100)}%`
}

// 柱子上只留月-日，年份对 7 天趋势没有信息量
function shortDate(date) {
  return String(date || '').slice(5)
}

async function load() {
  loading.value = true
  try {
    const { data } = await adminApi.stats()
    daily.value = data.daily || []
    topUsers.value = data.topUsers || []
    totals.value = { ...totals.value, ...(data.totals || {}) }
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
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

.figures {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--sp-3);
}

.figure {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--sp-4) var(--sp-5);
}

/* 数字用宋体大字，标签用小字压住，像书页上的统计栏而不是仪表盘卡片 */
.figure__value {
  font-family: var(--font-serif);
  font-size: var(--fs-3xl);
  line-height: 1.15;
  color: var(--ink);
  font-variant-numeric: tabular-nums;
}

.block {
  padding: var(--sp-5);
  border: 1px solid var(--rule);
  border-radius: var(--r-md);
  background: var(--sheet);
}

.block__head {
  padding-bottom: var(--sp-3);
  margin-bottom: var(--sp-4);
  border-bottom: 1px solid var(--rule);
}

.block__head h2 {
  font-size: var(--fs-lg);
}

.chart {
  display: flex;
  align-items: stretch;
  gap: var(--sp-3);
  height: 200px;
}

.chart__col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
}

.chart__count {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

/* 柱子以一条底线为基准，像稿纸上的刻度线 */
.chart__plot {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  border-bottom: 1px solid var(--rule-strong);
}

.chart__bar {
  width: 100%;
  min-height: 3px;
  background: var(--accent);
  border-radius: 1px 1px 0 0;
  transition: height 0.3s ease;
}

.chart__bar.is-empty {
  background: var(--rule);
}

.chart__date {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--ink-3);
  white-space: nowrap;
}

.rank {
  font-family: var(--font-mono);
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.table-scroll {
  overflow-x: auto;
}

@media (max-width: 820px) {
  .figures {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .figure {
    padding: var(--sp-3) var(--sp-4);
  }

  .figure__value {
    font-size: var(--fs-2xl);
  }

  .block {
    padding: var(--sp-4);
  }

  .chart {
    height: 160px;
    gap: var(--sp-1);
  }

  .table-scroll :deep(table) {
    min-width: 480px;
  }
}
</style>
