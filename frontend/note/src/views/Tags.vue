<template>
  <div class="page">
    <div class="page-title">
      <h1>标签</h1>
      <AppButton variant="primary" size="sm" @click="openCreate">新建标签</AppButton>
    </div>

    <p class="page-sub">
      标签属于你的私有分类，不同用户的同名标签互不影响。删除标签不会删除笔记，只解除关联。
    </p>

    <div class="stack">
      <div v-if="loading" class="loading-rule" />

      <AppEmpty
        v-else-if="!tags.length"
        title="还没有标签"
        description="建几个标签（比如「毕业设计」「读书」），写笔记时就能一键归类。"
      >
        <template #action>
          <AppButton variant="primary" @click="openCreate">新建标签</AppButton>
        </template>
      </AppEmpty>

      <ul v-else class="tag-list">
        <li v-for="t in tags" :key="t.id" class="tag-row">
          <span class="tag-row__name">{{ t.name }}</span>
          <span class="tag-row__count">{{ t.noteCount }} 篇笔记</span>
          <span class="tag-row__actions">
            <AppButton variant="quiet" size="sm" @click="openRename(t)">重命名</AppButton>
            <AppButton variant="quiet" size="sm" class="danger-text" @click="askRemove(t)">
              删除
            </AppButton>
          </span>
        </li>
      </ul>
    </div>

    <AppModal v-model="showForm" :title="editing ? '重命名标签' : '新建标签'">
      <AppField
        v-model="form.name"
        label="标签名"
        placeholder="最多 12 个字符"
        :maxlength="12"
        :error="formError"
        required
        @submit="submit"
      />
      <template #footer>
        <AppButton @click="showForm = false">取消</AppButton>
        <AppButton variant="primary" :loading="working" @click="submit">
          {{ editing ? '保存' : '创建' }}
        </AppButton>
      </template>
    </AppModal>

    <AppModal v-model="showRemove" title="删除这个标签？">
      <p class="confirm-text">
        删除「{{ target?.name }}」后，它和 {{ target?.noteCount }} 篇笔记的关联会解除，
        但<strong>笔记本身不会被删除</strong>。
      </p>
      <template #footer>
        <AppButton @click="showRemove = false">取消</AppButton>
        <AppButton variant="primary" :loading="working" @click="confirmRemove">确认删除</AppButton>
      </template>
    </AppModal>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import AppButton from '@/components/AppButton.vue'
import AppField from '@/components/AppField.vue'
import AppModal from '@/components/AppModal.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import { tagApi } from '@/api/index.js'
import { useToastStore } from '@/stores/toast.js'

const toast = useToastStore()

const tags = ref([])
const loading = ref(false)
const working = ref(false)

const showForm = ref(false)
const showRemove = ref(false)
const editing = ref(null)
const target = ref(null)
const form = reactive({ name: '' })
const formError = ref('')

async function load() {
  loading.value = true
  try {
    const { data } = await tagApi.list()
    tags.value = data
  } catch (err) {
    toast.error(err.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.name = ''
  formError.value = ''
  showForm.value = true
}

function openRename(tag) {
  editing.value = tag
  form.name = tag.name
  formError.value = ''
  showForm.value = true
}

async function submit() {
  const name = form.name.trim()
  if (!name) {
    formError.value = '标签名不能为空'
    return
  }
  working.value = true
  try {
    if (editing.value) {
      await tagApi.update(editing.value.id, name)
      toast.success('标签已重命名')
    } else {
      await tagApi.create(name)
      toast.success('标签已创建')
    }
    showForm.value = false
    await load()
  } catch (err) {
    formError.value = err.message
  } finally {
    working.value = false
  }
}

function askRemove(tag) {
  target.value = tag
  showRemove.value = true
}

async function confirmRemove() {
  working.value = true
  try {
    await tagApi.remove(target.value.id)
    toast.success('标签已删除')
    showRemove.value = false
    await load()
  } catch (err) {
    toast.error(err.message)
  } finally {
    working.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  padding: var(--sp-5) var(--sp-6);
}

.tag-list {
  border: 1px solid var(--rule);
  border-radius: var(--r-md);
  background: var(--sheet);
  overflow: hidden;
}

.tag-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: 11px var(--sp-4);
  border-bottom: 1px solid var(--rule);
}

.tag-row:last-child {
  border-bottom: 0;
}

.tag-row:hover {
  background: var(--paper);
}

.tag-row__name {
  font-family: var(--font-serif);
  font-size: var(--fs-base);
  color: var(--ink);
}

.tag-row__count {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.tag-row__actions {
  margin-left: auto;
  display: flex;
  gap: var(--sp-1);
}

.danger-text {
  color: var(--danger);
}

.confirm-text {
  font-size: var(--fs-sm);
  line-height: 1.8;
  color: var(--ink-2);
}

.confirm-text strong {
  color: var(--ink);
}
</style>
