<template>
  <div class="table-wrap">
    <table class="table">
      <thead>
        <tr>
          <th
            v-for="col in columns"
            :key="col.key"
            :style="{ width: col.width, textAlign: col.align || 'left' }"
          >
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row[rowKey]">
          <td
            v-for="col in columns"
            :key="col.key"
            :style="{ textAlign: col.align || 'left' }"
          >
            <!-- 每个单元格优先用 cell-<key> 插槽，否则直接输出值 -->
            <slot :name="`cell-${col.key}`" :row="row" :value="row[col.key]">
              {{ row[col.key] ?? '—' }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>

    <div v-if="loading" class="loading-rule" />

    <AppEmpty v-else-if="!rows.length" :title="emptyTitle" :description="emptyDescription">
      <template v-if="$slots.empty" #action>
        <slot name="empty" />
      </template>
    </AppEmpty>
  </div>
</template>

<script setup>
import AppEmpty from './AppEmpty.vue'

defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  loading: { type: Boolean, default: false },
  emptyTitle: { type: String, default: '没有数据' },
  emptyDescription: { type: String, default: '' }
})
</script>

<style scoped>
.table-wrap {
  border: 1px solid var(--rule);
  border-radius: var(--r-md);
  background: var(--sheet);
  overflow: hidden;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--fs-sm);
}

.table th {
  padding: 10px 14px;
  text-align: left;
  font-weight: 500;
  color: var(--ink-3);
  background: var(--paper-deep);
  border-bottom: 1px solid var(--rule);
  white-space: nowrap;
}

.table td {
  padding: 11px 14px;
  border-bottom: 1px solid var(--rule);
  color: var(--ink-2);
  vertical-align: middle;
}

/* 最后一行不要双线 */
.table tbody tr:last-child td {
  border-bottom: 0;
}

.table tbody tr:hover td {
  background: var(--paper);
}
</style>
