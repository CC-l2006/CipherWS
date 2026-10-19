<template>
  <div class="workspace">
    <!-- ==================== 左：笔记列表 ==================== -->
    <section class="list" aria-label="笔记列表">
      <div class="list__head">
        <input
          v-model="keyword"
          class="list__search"
          type="search"
          placeholder="搜索标题或正文"
          aria-label="搜索笔记"
        />
        <AppButton variant="primary" size="sm" @click="createNote">新笔记</AppButton>
      </div>

      <div class="list__filters">
        <button
          v-for="f in statusFilters"
          :key="f.value"
          type="button"
          class="chip"
          :class="{ 'is-active': statusFilter === f.value }"
          @click="statusFilter = f.value"
        >
          {{ f.label }}
        </button>
      </div>

      <div class="list__scroll">
        <div v-if="listLoading" class="loading-rule" />

        <p v-if="!listLoading && !notes.length" class="list__empty">
          {{ keyword ? '没有匹配的笔记' : '还没有笔记，点上面的「新笔记」开始写' }}
        </p>

        <button
          v-for="n in notes"
          :key="n.id"
          type="button"
          class="item"
          :class="{ 'is-active': n.id === Number(route.params.id) }"
          @click="openNote(n.id)"
        >
          <span class="item__top">
            <span class="item__title">{{ n.title }}</span>
            <span v-if="n.status === 'draft'" class="item__draft">草稿</span>
          </span>
          <span class="item__snippet">{{ snippet(n.content) }}</span>
          <span class="item__meta">
            <span class="item__time">{{ relTime(n.updatedAt) }}</span>
            <span v-if="n.tags?.length" class="item__tags">
              <AppTag v-for="t in n.tags" :key="t.id" tone="accent">{{ t.name }}</AppTag>
            </span>
          </span>
        </button>
      </div>

      <RouterLink to="/notes/trash" class="list__trash">
        回收站
        <span aria-hidden="true">→</span>
      </RouterLink>
    </section>

    <!-- ==================== 右：编辑区 ==================== -->
    <section class="editor" aria-label="笔记编辑">
      <template v-if="hasNote">
        <header class="bar">
          <div class="bar__left">
            <button
              v-if="draft.status === 'draft'"
              type="button"
              class="bar__status is-draft"
              title="点击发布为私有笔记"
              @click="draft.status = 'private'"
            >
              草稿
            </button>
            <button
              v-else
              type="button"
              class="bar__status"
              title="点击退回草稿"
              @click="draft.status = 'draft'"
            >
              已发布
            </button>

            <div class="tags">
              <button type="button" class="tags__btn" @click="tagPickerOpen = !tagPickerOpen">
                标签<span v-if="draftTagNames.length">（{{ draftTagNames.length }}）</span>
              </button>
              <div v-if="tagPickerOpen" class="tags__pop">
                <p v-if="!tags.length" class="tags__empty">还没有标签，去「标签」页新建</p>
                <label v-for="t in tags" :key="t.id" class="tags__opt">
                  <input v-model="draft.tagIds" type="checkbox" :value="t.id" />
                  <span>{{ t.name }}</span>
                </label>
              </div>
            </div>
          </div>

          <div class="bar__right">
            <span class="bar__save" :class="`is-${saveState}`">{{ saveLabel }}</span>
            <AppButton
              variant="ghost"
              size="sm"
              :loading="summarizing"
              :disabled="!canSummarize"
              @click="summarize"
            >
              AI 总结
            </AppButton>
            <AppButton variant="ghost" size="sm" @click="removeNote">删除</AppButton>
          </div>
        </header>

        <div class="paper-wrap">
          <!-- 稿纸：左侧一条竖边线，正文在左，AI 批注在右留白 -->
          <div class="paper">
            <div class="paper__write">
              <input
                v-model="draft.title"
                class="paper__title"
                type="text"
                placeholder="无标题笔记"
                maxlength="80"
                aria-label="笔记标题"
              />
              <textarea
                v-model="draft.content"
                class="paper__body"
                placeholder="从这里开始写。内容会自动保存为草稿。"
                aria-label="笔记正文"
              />
            </div>

            <aside class="paper__margin" aria-label="AI 批注">
              <p class="paper__margin-head">
                AI 批注
                <span class="paper__margin-quota">剩余 {{ userStore.aiQuota }} 次</span>
              </p>

              <p v-if="!summaries.length" class="paper__margin-empty">
                写完一段后点右上角「AI 总结」，摘要会以批注的形式出现在这里，和正文并排阅读。
              </p>

              <article v-for="s in summaries" :key="s.id" class="marginalia">
                <span class="marginalia__time">{{ relTime(s.createdAt) }}</span>
                <p class="marginalia__text">{{ s.summary }}</p>
              </article>

              <div v-if="summarizing" class="marginalia is-pending">
                <span class="marginalia__time">正在生成…</span>
                <div class="loading-rule" />
              </div>
            </aside>
          </div>
        </div>
      </template>

      <!-- 没选中任何笔记 -->
      <div v-else class="editor__blank">
        <AppEmpty
          title="选一篇笔记开始读，或者新建一篇"
          description="左侧是全部私有笔记。AI 总结会把摘要写在正文右侧的页边，方便对照原文看。"
        >
          <template #action>
            <AppButton variant="primary" @click="createNote">新笔记</AppButton>
          </template>
        </AppEmpty>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/AppButton.vue'
import AppTag from '@/components/AppTag.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import { noteApi, tagApi, aiApi } from '@/api/index.js'
import { useUserStore } from '@/stores/user.js'
import { useToastStore } from '@/stores/toast.js'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const toast = useToastStore()

/* ---------------- 列表 ---------------- */

const notes = ref([])
const listLoading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const statusFilters = [
  { value: '', label: '全部' },
  { value: 'draft', label: '草稿' },
  { value: 'private', label: '已发布' }
]

async function loadList() {
  listLoading.value = true
  try {
    const { data } = await noteApi.list({
      keyword: keyword.value.trim(),
      status: statusFilter.value
    })
    notes.value = data
  } catch (err) {
    toast.error(err.message)
  } finally {
    listLoading.value = false
  }
}

let searchTimer = null
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadList, 260)
})
watch(statusFilter, loadList)

/* ---------------- 标签 ---------------- */

const tags = ref([])
const tagPickerOpen = ref(false)
const draftTagNames = computed(() => {
  const ids = draft.value?.tagIds || []
  return tags.value.filter((t) => ids.includes(t.id)).map((t) => t.name)
})

async function loadTags() {
  try {
    const { data } = await tagApi.list()
    tags.value = data
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------------- 当前笔记 ---------------- */

const draft = ref(null)
const summaries = ref([])
const saveState = ref('idle') // idle | saving | saved | error
const summarizing = ref(false)
const hasNote = computed(() => !!draft.value)

const canSummarize = computed(
  () => !!draft.value?.id && String(draft.value.content || '').trim().length > 0 && userStore.aiQuota > 0
)

const saveLabel = computed(() => {
  if (saveState.value === 'saving') return '保存中…'
  if (saveState.value === 'saved') return '已自动保存'
  if (saveState.value === 'error') return '保存失败'
  return ''
})

async function openNote(id) {
  if (Number(route.params.id) === Number(id)) return
  await flushSave()
  router.push(`/notes/${id}`)
}

async function loadNote(id) {
  if (!id) {
    draft.value = null
    summaries.value = []
    return
  }
  try {
    const { data } = await noteApi.detail(id)
    // 加载时写入 draft 会触发下面的自动保存 watcher，导致「刚打开就保存一次」。
    // 用一个标志在本次渲染周期内屏蔽它，nextTick 后再放开。
    suppressAutoSave = true
    draft.value = {
      id: data.id,
      title: data.title,
      content: data.content,
      status: data.status,
      tagIds: (data.tags || []).map((t) => t.id)
    }
    summaries.value = data.summaries || []
    saveState.value = 'idle'
    nextTick(() => {
      suppressAutoSave = false
    })
  } catch (err) {
    toast.error(err.message)
    router.replace('/notes')
  }
}

watch(
  () => route.params.id,
  (id) => {
    if (route.name === 'note-detail' || route.name === 'notes') loadNote(id)
  },
  { immediate: true }
)

/* ---------------- 自动保存（需求 6.3.2：防止内容丢失） ---------------- */

let saveTimer = null
let pendingSave = false
// 由 loadNote 置位：屏蔽「程序写入 draft」引起的自动保存
let suppressAutoSave = false

watch(
  () => [draft.value?.title, draft.value?.content, draft.value?.status, draft.value?.tagIds],
  () => {
    if (suppressAutoSave) return
    if (!draft.value?.id) return
    pendingSave = true
    saveState.value = 'idle'
    clearTimeout(saveTimer)
    saveTimer = setTimeout(() => doSave(), 1200)
  },
  { deep: true }
)

async function doSave() {
  if (!draft.value?.id || !pendingSave) return
  pendingSave = false
  saveState.value = 'saving'
  try {
    const { data } = await noteApi.update(draft.value.id, {
      title: draft.value.title,
      content: draft.value.content,
      status: draft.value.status,
      tagIds: draft.value.tagIds
    })
    // 用服务端返回的标题兜底（空标题会被后端补成「无标题笔记」）
    draft.value.title = data.title
    // 让列表里的标题/摘要同步更新
    const i = notes.value.findIndex((n) => n.id === data.id)
    if (i >= 0) notes.value[i] = { ...notes.value[i], ...data }
    saveState.value = 'saved'
  } catch (err) {
    saveState.value = 'error'
    toast.error(`保存失败：${err.message}`)
  }
}

/** 切换笔记前把未落盘的改动写回去 */
async function flushSave() {
  clearTimeout(saveTimer)
  if (pendingSave) await doSave()
}

/* ---------------- 新建 / 删除 ---------------- */

const creating = ref(false)

async function createNote() {
  if (creating.value) return
  creating.value = true
  try {
    await flushSave()
    const { data } = await noteApi.create({ title: '', content: '', status: 'draft', tagIds: [] })
    await loadList()
    router.push(`/notes/${data.id}`)
  } catch (err) {
    toast.error(err.message)
  } finally {
    creating.value = false
  }
}

async function removeNote() {
  if (!draft.value?.id) return
  // 删除是不可逆动作（先进回收站），用确认框而不是直接删
  if (!window.confirm(`把《${draft.value.title || '无标题笔记'}》移入回收站？`)) return
  try {
    clearTimeout(saveTimer)
    pendingSave = false
    await noteApi.remove(draft.value.id)
    toast.success('已移入回收站，可在回收站恢复')
    await loadList()
    router.replace('/notes')
  } catch (err) {
    toast.error(err.message)
  }
}

/* ---------------- AI 总结 ---------------- */

async function summarize() {
  if (!draft.value?.id || summarizing.value) return
  if (userStore.aiQuota <= 0) {
    toast.warn('AI 额度已用完，请联系管理员分配')
    return
  }
  await flushSave()
  summarizing.value = true
  try {
    const { data } = await aiApi.summarize(draft.value.id)
    summaries.value.unshift(data)
    userStore.patchUserInfo({ aiQuota: data.quotaLeft })
    toast.success('已生成摘要，写在右侧页边')
  } catch (err) {
    toast.error(err.message)
  } finally {
    summarizing.value = false
  }
}

/* ---------------- 小工具 ---------------- */

function snippet(text) {
  const s = String(text || '').replace(/\s+/g, ' ').trim()
  return s.length > 46 ? `${s.slice(0, 46)}…` : s || '（空）'
}

function relTime(iso) {
  if (!iso) return ''
  const diff = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min} 分钟前`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour} 小时前`
  const day = Math.floor(hour / 24)
  if (day < 8) return `${day} 天前`
  return new Date(iso).toLocaleDateString('zh-CN')
}

/* ---------------- 生命周期 ---------------- */

loadList()
loadTags()

onBeforeUnmount(() => {
  clearTimeout(saveTimer)
  clearTimeout(searchTimer)
})
</script>

<style scoped>
.workspace {
  display: flex;
  height: 100vh;
  min-height: 0;
}

/* ==================== 列表 ==================== */
.list {
  flex: none;
  width: var(--list-w);
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--rule);
  background: var(--paper);
}

.list__head {
  display: flex;
  gap: var(--sp-2);
  padding: var(--sp-4) var(--sp-4) var(--sp-2);
}

.list__search {
  flex: 1;
  min-width: 0;
  padding: 7px 10px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  background: var(--sheet);
  font-size: var(--fs-sm);
}

.list__search:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.list__filters {
  display: flex;
  gap: var(--sp-1);
  padding: 0 var(--sp-4) var(--sp-3);
}

.chip {
  padding: 3px 10px;
  border: 1px solid transparent;
  border-radius: var(--r-pill);
  background: transparent;
  color: var(--ink-3);
  font-size: var(--fs-xs);
  cursor: pointer;
}

.chip:hover {
  color: var(--ink);
  background: var(--paper-deep);
}

.chip.is-active {
  border-color: var(--rule-strong);
  color: var(--ink);
  background: var(--sheet);
}

.list__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 var(--sp-2);
}

.list__empty {
  padding: var(--sp-6) var(--sp-3);
  font-size: var(--fs-sm);
  color: var(--ink-3);
  line-height: 1.7;
  text-align: center;
}

.item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 10px var(--sp-3);
  margin-bottom: 2px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  cursor: pointer;
}

.item:hover {
  background: var(--paper-deep);
}

/* 选中：浅墨底 + 左侧短墨线，和侧栏导航同一套语言 */
.item.is-active {
  background: var(--accent-soft);
  box-shadow: inset 2px 0 0 var(--accent);
}

.item__top {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.item__title {
  flex: 1;
  min-width: 0;
  font-family: var(--font-serif);
  font-size: var(--fs-base);
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item__draft {
  flex: none;
  font-size: var(--fs-xs);
  color: var(--warn);
}

.item__snippet {
  display: block;
  margin-top: 2px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-top: 6px;
}

.item__time {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.item__tags {
  display: inline-flex;
  gap: 4px;
  overflow: hidden;
}

.list__trash {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px var(--sp-4);
  border-top: 1px solid var(--rule);
  font-size: var(--fs-sm);
  color: var(--ink-3);
  text-decoration: none;
}

.list__trash:hover {
  color: var(--ink);
  background: var(--paper-deep);
  text-decoration: none;
}

/* ==================== 编辑区 ==================== */
.editor {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--paper-deep);
}

.editor__blank {
  flex: 1;
  display: grid;
  place-items: center;
}

.bar {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-3);
  padding: var(--sp-2) var(--sp-4);
  border-bottom: 1px solid var(--rule);
  background: var(--paper);
  flex-wrap: wrap;
}

.bar__left,
.bar__right {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
}

.bar__status {
  padding: 3px 10px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-pill);
  background: transparent;
  font-size: var(--fs-xs);
  color: var(--ok);
  cursor: pointer;
}

.bar__status.is-draft {
  color: var(--warn);
  border-color: var(--warn);
}

.tags {
  position: relative;
}

.tags__btn {
  padding: 3px 10px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-pill);
  background: transparent;
  font-size: var(--fs-xs);
  color: var(--ink-2);
  cursor: pointer;
}

.tags__btn:hover {
  border-color: var(--ink-3);
  color: var(--ink);
}

.tags__pop {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 20;
  min-width: 160px;
  max-height: 240px;
  overflow-y: auto;
  padding: var(--sp-2);
  background: var(--sheet);
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  box-shadow: var(--shadow-pop);
}

.tags__empty {
  padding: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.tags__opt {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 5px var(--sp-2);
  border-radius: var(--r-sm);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.tags__opt:hover {
  background: var(--paper);
}

.bar__save {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  min-width: 60px;
  text-align: right;
}

.bar__save.is-error {
  color: var(--danger);
}

/* ---------- 稿纸 ---------- */

.paper-wrap {
  flex: 1;
  min-height: 0;
  padding: var(--sp-4);
  display: flex;
}

/* 全站唯一带阴影的「一张纸」，并且是唯一有左侧竖边线的地方 */
.paper {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 232px;
  background: var(--sheet);
  border: 1px solid var(--rule);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-sheet);
  overflow: hidden;
}

.paper__write {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--sp-6) var(--sp-6) var(--sp-4) var(--sp-8);
  /* 稿纸的页边线：一条竖线，靠它还原文书的书写空间 */
  background-image: linear-gradient(90deg, transparent 0 40px, var(--rule) 40px 41px, transparent 41px);
}

.paper__title {
  flex: none;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0 0 var(--sp-3);
  font-family: var(--font-serif);
  font-size: var(--fs-2xl);
  font-weight: 600;
  color: var(--ink);
  line-height: 1.3;
}

.paper__title::placeholder {
  color: var(--rule-strong);
}

.paper__title:focus {
  outline: none;
}

.paper__body {
  flex: 1;
  min-height: 0;
  width: 100%;
  border: 0;
  background: transparent;
  resize: none;
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  line-height: 1.9;
  color: var(--ink);
  padding-top: var(--sp-2);
}

.paper__body::placeholder {
  color: var(--ink-3);
}

.paper__body:focus {
  outline: none;
}

/* ---------- 页边批注 ---------- */

.paper__margin {
  min-height: 0;
  overflow-y: auto;
  padding: var(--sp-6) var(--sp-4);
  border-left: 1px solid var(--rule);
  background: var(--paper);
}

.paper__margin-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--sp-2);
  padding-bottom: var(--sp-2);
  margin-bottom: var(--sp-3);
  border-bottom: 1px solid var(--rule);
  font-family: var(--font-serif);
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

.paper__margin-quota {
  font-family: var(--font-sans);
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.paper__margin-empty {
  font-size: var(--fs-xs);
  line-height: 1.8;
  color: var(--ink-3);
}

.marginalia {
  position: relative;
  padding: 0 0 var(--sp-4) var(--sp-3);
  border-left: 1px solid var(--rule-strong);
  margin-bottom: var(--sp-3);
}

.marginalia__time {
  display: block;
  font-size: var(--fs-xs);
  color: var(--ink-3);
  margin-bottom: 3px;
}

.marginalia__text {
  font-family: var(--font-serif);
  font-size: var(--fs-sm);
  line-height: 1.8;
  color: var(--ink-2);
  white-space: pre-wrap;
}

.marginalia.is-pending {
  opacity: 0.75;
}

/* ==================== 响应式 ==================== */

@media (max-width: 1180px) {
  .paper {
    grid-template-columns: minmax(0, 1fr) 200px;
  }
}

@media (max-width: 900px) {
  .paper {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) auto;
  }

  .paper__margin {
    max-height: 38vh;
    border-left: 0;
    border-top: 1px solid var(--rule);
  }

  .paper__write {
    padding-left: var(--sp-6);
    background-image: none;
  }
}

@media (max-width: 820px) {
  .workspace {
    height: auto;
    flex-direction: column;
  }

  .list {
    width: 100%;
    max-height: 42vh;
    border-right: 0;
    border-bottom: 1px solid var(--rule);
  }

  .editor {
    min-height: 58vh;
  }

  .paper-wrap {
    padding: var(--sp-2);
  }

  .paper__write {
    padding: var(--sp-4);
  }
}
</style>
