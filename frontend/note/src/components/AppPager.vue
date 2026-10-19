<template>
  <nav v-if="total > size" class="pager" aria-label="分页">
    <button class="pager__btn" type="button" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
    <span class="pager__info">
      第 <strong>{{ page }}</strong> / {{ pages }} 页，共 {{ total }} 条
    </span>
    <button class="pager__btn" type="button" :disabled="page >= pages" @click="go(page + 1)">下一页</button>
  </nav>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  total: { type: Number, default: 0 }
})

const emit = defineEmits(['update:page'])
const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)))

function go(next) {
  if (next < 1 || next > pages.value || next === props.page) return
  emit('update:page', next)
}
</script>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--sp-3);
  padding-top: var(--sp-4);
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.pager__btn {
  padding: 5px 12px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font-size: var(--fs-sm);
}

.pager__btn:hover:not(:disabled) {
  border-color: var(--ink-3);
  color: var(--ink);
  background: var(--paper-deep);
}

.pager__btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pager__info strong {
  color: var(--ink);
  font-weight: 600;
}
</style>
