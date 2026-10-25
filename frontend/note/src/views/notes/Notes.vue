<template>
  <div class="workspace">
    <!-- ==================== 左：笔记列表 ==================== -->
    <section class="list" :class="{ 'is-resizing': resizingFolders }" aria-label="笔记列表">
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

      <!-- 文件夹：绑定标签，带这些标签的笔记自动归入 -->
      <div class="folders" :style="{ height: foldersHeight + 'px' }">
        <div class="folders__head">
          <span>文件夹</span>
          <div class="folders__actions">
            <div ref="tagMenuEl" class="tag-menu">
              <button
                type="button"
                class="tag-toggle"
                :class="{ 'is-active': tagPanelOpen || tagFilter.length }"
                :aria-expanded="tagPanelOpen"
                @click="tagPanelOpen = !tagPanelOpen"
              >
                标签<span v-if="tagFilter.length">（{{ tagFilter.length }}）</span>
              </button>

              <!-- 标签筛选：挂在按钮下方，点空白处收起 -->
              <div v-if="tagPanelOpen" class="tag-panel">
                <div class="tag-panel__head">
                  <span>按标签筛选</span>
                  <button
                    v-if="tagFilter.length"
                    type="button"
                    class="tag-panel__clear"
                    @click="tagFilter = []"
                  >
                    清除
                  </button>
                </div>
                <div class="tag-panel__chips">
                  <button
                    v-for="t in tags"
                    :key="t.id"
                    type="button"
                    class="tag-chip"
                    :class="{ 'is-active': tagFilter.includes(t.id) }"
                    @click="toggleTagFilter(t.id)"
                  >
                    {{ t.name }}
                  </button>
                  <span v-if="!tags.length" class="tag-filter__empty">暂无标签</span>
                </div>
              </div>
            </div>

            <button type="button" class="folders__add" title="新建文件夹" @click="openFolderForm()">
              ＋
            </button>
          </div>
        </div>

        <div class="folders__list">
          <button
            type="button"
            class="folder"
            :class="{ 'is-active': selectedFolder === 'all' }"
            @click="selectedFolder = 'all'"
          >
            全部笔记
          </button>
          <button
            type="button"
            class="folder"
            :class="{ 'is-active': selectedFolder === 'untagged' }"
            @click="selectedFolder = 'untagged'"
          >
            未分类
          </button>
          <div
            v-for="f in folders"
            :key="f.id"
            class="folder-row"
            :class="{ 'is-active': selectedFolder === f.id }"
          >
            <button type="button" class="folder-row__main" @click="selectedFolder = f.id">
              <span class="folder-row__name">{{ f.name }}</span>
              <span class="folder-row__count">{{ f.noteCount }}</span>
            </button>
            <button
              type="button"
              class="folder-row__edit"
              aria-label="编辑文件夹"
              @click.stop="openFolderForm(f)"
            >
              ✎
            </button>
          </div>
        </div>
      </div>

      <!-- 文件夹区与笔记列表的上下分界，拖动调整高度，双击复位 -->
      <div
        class="v-resizer"
        role="separator"
        aria-orientation="horizontal"
        title="拖动调整文件夹高度，双击复位"
        @mousedown="startFoldersResize"
        @dblclick="resetFoldersHeight"
      />

      <div class="list__scroll">
        <div v-if="listLoading" class="loading-rule" />

        <p v-if="!listLoading && !notes.length" class="list__empty">
          {{ keyword ? '没有匹配的笔记' : '还没有笔记' }}
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
      <!-- 打开的笔记标签页：未保存的用实心圆点标记 -->
      <div v-if="openTabs.length" class="tabs" role="tablist" aria-label="打开的笔记">
        <div
          v-for="t in openTabs"
          :key="t.id"
          class="tab"
          :class="{ 'is-active': draft?.id === t.id }"
        >
          <button
            type="button"
            class="tab__main"
            role="tab"
            :aria-selected="draft?.id === t.id"
            @click="switchTab(t.id)"
          >
            <span v-if="draft?.id === t.id && dirty" class="tab__dot" aria-label="未保存" />
            <span class="tab__title">{{ t.title }}</span>
          </button>
          <button type="button" class="tab__close" aria-label="关闭标签" @click="closeTab(t.id)">
            ×
          </button>
        </div>
      </div>

      <template v-if="hasNote">
        <header class="bar">
          <div class="bar__left">
            <button type="button" class="tags__btn" @click="tagModalOpen = true">
              标签<span v-if="draftTagNames.length">（{{ draftTagNames.length }}）</span>
            </button>
          </div>

          <div class="bar__right">
            <span class="bar__save" :class="`is-${saveState}`">{{ saveLabel }}</span>
            <AppButton
              variant="primary"
              size="sm"
              :loading="saveState === 'saving'"
              @click="saveNow(true)"
            >
              保存
            </AppButton>
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

        <!-- ==================== Markdown 工具栏 ==================== -->
        <div ref="toolbarEl" class="md-bar" aria-label="Markdown 工具栏" @keydown.esc="menu = ''">
          <div class="md-bar__group">
            <button
              type="button"
              class="md-btn"
              :class="{ 'is-open': menu === 'paragraph' }"
              :disabled="mode !== 'edit'"
              aria-haspopup="menu"
              :aria-expanded="menu === 'paragraph'"
              @mousedown.prevent
              @click="toggleMenu('paragraph')"
            >
              段落<span class="md-caret" aria-hidden="true">▾</span>
            </button>
            <div v-if="menu === 'paragraph'" class="md-menu" role="menu" aria-label="段落">
              <button
                v-for="opt in paragraphOptions"
                :key="opt.key"
                type="button"
                class="md-menu__item"
                role="menuitem"
                @mousedown.prevent
                @click="applyParagraph(opt.key)"
              >
                <span>{{ opt.label }}</span>
                <span v-if="opt.hint" class="md-menu__hint">{{ opt.hint }}</span>
              </button>
            </div>
          </div>

          <span class="md-bar__rule" aria-hidden="true" />

          <div class="md-bar__group">
            <button
              type="button"
              class="md-btn"
              :class="{ 'is-open': menu === 'format' }"
              :disabled="mode !== 'edit'"
              aria-haspopup="menu"
              :aria-expanded="menu === 'format'"
              @mousedown.prevent
              @click="toggleMenu('format')"
            >
              格式<span class="md-caret" aria-hidden="true">▾</span>
            </button>
            <div v-if="menu === 'format'" class="md-menu" role="menu" aria-label="格式">
              <button
                v-for="opt in formatOptions"
                :key="opt.key"
                type="button"
                class="md-menu__item"
                role="menuitem"
                @mousedown.prevent
                @click="applyFormat(opt.key)"
              >
                <span>{{ opt.label }}</span>
                <span class="md-menu__hint">{{ opt.hint }}</span>
              </button>
            </div>
          </div>

          <span class="md-bar__rule" aria-hidden="true" />

          <button
            type="button"
            class="md-btn"
            :class="{ 'is-open': outlineOpen }"
            :aria-pressed="outlineOpen"
            @click="toggleOutline"
          >
            大纲视图
          </button>

          <button type="button" class="md-btn md-btn--mode" @click="toggleMode">
            {{ mode === 'edit' ? '预览' : '编辑' }}
          </button>
        </div>

        <div class="paper-wrap">
          <!-- 稿纸：大纲在左，正文居中，AI 批注在右 -->
          <div
            class="paper"
            :class="{ 'is-outlining': outlineOpen, 'is-resizing': resizing }"
            :style="{ '--margin-w': marginWidth + 'px' }"
          >
            <!-- 大纲视图：从正文的 # 标题实时生成 -->
            <aside v-if="outlineOpen" class="outline" aria-label="大纲">
              <p class="outline__head">
                大纲
                <button type="button" class="outline__close" aria-label="关闭大纲" @click="outlineOpen = false">
                  ×
                </button>
              </p>

              <p v-if="!outline.length" class="outline__empty">用 # 写标题</p>

              <nav v-else class="outline__list">
                <button
                  v-for="(item, i) in outline"
                  :key="item.offset"
                  type="button"
                  class="outline__item"
                  :class="{ 'is-active': activeOutline === i }"
                  :style="{ paddingLeft: `${6 + (item.level - 1) * 10}px` }"
                  @click="jumpToHeading(item, i)"
                >
                  {{ item.text }}
                </button>
              </nav>
            </aside>

            <div class="paper__write">
              <input
                v-model="draft.title"
                class="paper__title"
                type="text"
                placeholder="无标题笔记"
                maxlength="80"
                aria-label="笔记标题"
              />

              <!-- 编辑态也显示渲染结果：内容所见即所得，Markdown 存在 draft.content -->
              <div
                v-if="mode === 'edit'"
                ref="editorEl"
                class="preview preview--edit"
                contenteditable="true"
                role="textbox"
                aria-multiline="true"
                data-placeholder="从这里开始写"
                @input="onEditorInput"
                @keydown="onEditorKeydown"
                @paste="onPaste"
                @click="onEditorClick"
              />

              <div
                v-else-if="hasContent"
                ref="previewEl"
                class="preview"
                @click="onPreviewClick"
                v-html="previewHtml"
              />

              <button v-else type="button" class="preview preview--empty" @click="startEdit">
                从这里开始写
              </button>
            </div>

            <!-- 正文与 AI 批注之间的分栏把手：拖动改宽度，双击复位 -->
            <div
              class="resizer"
              role="separator"
              aria-orientation="vertical"
              title="拖动调整宽度，双击复位"
              @mousedown="startResize"
              @dblclick="resetMarginWidth"
            />

            <aside class="paper__margin" aria-label="AI 批注">
              <p class="paper__margin-head">
                AI 批注
                <span class="paper__margin-quota">剩余 {{ userStore.aiQuota }} 次</span>
              </p>

              <p v-if="!summaries.length" class="paper__margin-empty">点「AI 总结」生成摘要</p>

              <article v-for="s in summaries" :key="s.id" class="marginalia">
                <div class="marginalia__head">
                  <button type="button" class="marginalia__use" @click="useSummary(s)">
                    替换正文
                  </button>
                  <span class="marginalia__time">{{ relTime(s.createdAt) }}</span>
                </div>
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
        <AppEmpty title="选一篇笔记，或新建一篇">
          <template #action>
            <AppButton variant="primary" @click="createNote">新笔记</AppButton>
          </template>
        </AppEmpty>
      </div>
    </section>

    <!-- 编辑笔记时打标签：勾选 + 直接新建 -->
    <AppModal v-model="tagModalOpen" title="笔记标签" width="420px">
      <div class="tag-picker">
        <label v-for="t in tags" :key="t.id" class="tag-picker__item">
          <input v-model="draft.tagIds" type="checkbox" :value="t.id" />
          <span>{{ t.name }}</span>
        </label>
        <p v-if="!tags.length" class="tag-picker__empty">还没有标签，在下面新建一个</p>
      </div>
      <div class="tag-picker__new">
        <input
          v-model="newTagName"
          class="tag-picker__input"
          type="text"
          maxlength="12"
          placeholder="新标签名"
          @keyup.enter="createTagInline"
        />
        <AppButton size="sm" :loading="newTagSaving" @click="createTagInline">新建</AppButton>
      </div>
      <template #footer>
        <AppButton variant="primary" @click="tagModalOpen = false">完成</AppButton>
      </template>
    </AppModal>

    <!-- 新建 / 编辑文件夹：文件夹绑定标签，笔记按标签自动归入 -->
    <AppModal
      v-model="folderModalOpen"
      :title="folderForm.id ? '编辑文件夹' : '新建文件夹'"
      width="460px"
    >
      <AppField
        v-model="folderForm.name"
        label="文件夹名"
        placeholder="例如 Java"
        :maxlength="20"
        required
      />
      <p class="folder-hint">勾选标签后，带这些标签的笔记会自动归入。</p>
      <div class="tag-picker">
        <label v-for="t in tags" :key="t.id" class="tag-picker__item">
          <input v-model="folderForm.tagIds" type="checkbox" :value="t.id" />
          <span>{{ t.name }}</span>
        </label>
        <p v-if="!tags.length" class="tag-picker__empty">还没有标签，先去「标签」页新建</p>
      </div>
      <template #footer>
        <AppButton v-if="folderForm.id" variant="danger" @click="deleteFolder">删除</AppButton>
        <span class="modal-spacer" />
        <AppButton @click="folderModalOpen = false">取消</AppButton>
        <AppButton variant="primary" :loading="folderSaving" @click="saveFolder">保存</AppButton>
      </template>
    </AppModal>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch, nextTick, onBeforeUnmount, onMounted } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import AppButton from '@/components/AppButton.vue'
import AppTag from '@/components/AppTag.vue'
import AppEmpty from '@/components/AppEmpty.vue'
import AppField from '@/components/AppField.vue'
import AppModal from '@/components/AppModal.vue'
import { noteApi, tagApi, aiApi, folderApi } from '@/api/index.js'
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
const folders = ref([])
/** 'all' | 'untagged' | 文件夹 id */
const selectedFolder = ref('all')
/** 多选的标签筛选，和文件夹条件取交集 */
const tagFilter = ref([])
const tagPanelOpen = ref(false)
const tagMenuEl = ref(null)

/* 文件夹区高度：拖动上下分界调整，双击复位，记住上次的值 */
const FOLDERS_MIN = 120
const foldersHeight = ref(220)
const resizingFolders = ref(false)
try {
  const saved = Number(localStorage.getItem('note_folders_h'))
  if (Number.isFinite(saved) && saved >= FOLDERS_MIN && saved <= 800) foldersHeight.value = saved
} catch {
  /* 隐私模式下读不到就用默认值 */
}

function startFoldersResize(e) {
  const list = e.currentTarget.closest('.list')
  if (!list) return
  e.preventDefault()
  resizingFolders.value = true
  const startY = e.clientY
  const startHeight = foldersHeight.value
  const maxHeight = Math.max(FOLDERS_MIN + 40, list.clientHeight - 180)

  const onMove = (ev) => {
    const next = Math.min(maxHeight, Math.max(FOLDERS_MIN, startHeight + (ev.clientY - startY)))
    foldersHeight.value = Math.round(next)
  }
  const onUp = () => {
    resizingFolders.value = false
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    try {
      localStorage.setItem('note_folders_h', String(foldersHeight.value))
    } catch {
      /* 忽略 */
    }
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

function resetFoldersHeight() {
  foldersHeight.value = 220
  try {
    localStorage.setItem('note_folders_h', '220')
  } catch {
    /* 忽略 */
  }
}

async function loadList() {
  listLoading.value = true
  try {
    const { data } = await noteApi.list({
      keyword: keyword.value.trim() || undefined,
      folderId: typeof selectedFolder.value === 'number' ? selectedFolder.value : undefined,
      tagIds: tagFilter.value.length ? tagFilter.value.join(',') : undefined,
      untagged: selectedFolder.value === 'untagged' ? true : undefined
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
watch(selectedFolder, loadList)
watch(() => tagFilter.value.join(','), loadList)

async function loadFolders() {
  try {
    const { data } = await folderApi.list()
    folders.value = data
  } catch (err) {
    toast.error(err.message)
  }
}

function toggleTagFilter(id) {
  const index = tagFilter.value.indexOf(id)
  if (index >= 0) tagFilter.value.splice(index, 1)
  else tagFilter.value.push(id)
}

/* ---------------- 标签 ---------------- */

const tags = ref([])
const tagModalOpen = ref(false)
const newTagName = ref('')
const newTagSaving = ref(false)
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

/** 编辑笔记时直接新建标签，并自动勾选到当前笔记 */
async function createTagInline() {
  const name = newTagName.value.trim()
  if (!name) return
  newTagSaving.value = true
  try {
    const { data } = await tagApi.create(name)
    tags.value.push(data)
    if (draft.value && !draft.value.tagIds.includes(data.id)) draft.value.tagIds.push(data.id)
    newTagName.value = ''
    await loadFolders()
  } catch (err) {
    toast.error(err.message)
  } finally {
    newTagSaving.value = false
  }
}

/* ---------------- 文件夹 ---------------- */

const folderModalOpen = ref(false)
const folderSaving = ref(false)
const folderForm = reactive({ id: null, name: '', tagIds: [] })

function openFolderForm(folder = null) {
  folderForm.id = folder?.id ?? null
  folderForm.name = folder?.name ?? ''
  folderForm.tagIds = folder ? [...folder.tagIds] : []
  folderModalOpen.value = true
}

async function saveFolder() {
  const name = folderForm.name.trim()
  if (!name) {
    toast.warn('文件夹名不能为空')
    return
  }
  folderSaving.value = true
  try {
    const body = { name, tagIds: folderForm.tagIds }
    if (folderForm.id) await folderApi.update(folderForm.id, body)
    else await folderApi.create(body)
    await loadFolders()
    await loadList()
    folderModalOpen.value = false
    toast.success(folderForm.id ? '已保存' : '已创建')
  } catch (err) {
    toast.error(err.message)
  } finally {
    folderSaving.value = false
  }
}

async function deleteFolder() {
  if (!folderForm.id) return
  if (!window.confirm(`删除文件夹「${folderForm.name}」？笔记和标签都不会被删除。`)) return
  try {
    const id = folderForm.id
    await folderApi.remove(id)
    if (selectedFolder.value === id) selectedFolder.value = 'all'
    folderModalOpen.value = false
    await loadFolders()
    await loadList()
    toast.success('已删除')
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

/* 打开的笔记标签页；dirty 只跟踪当前这篇（切走前会先保存） */
const openTabs = ref([])
const dirty = ref(false)

/* ---------------- Markdown 工具栏 / 大纲 ---------------- */

const toolbarEl = ref(null)
const editorEl = ref(null)
const previewEl = ref(null)
const mode = ref('preview') // edit | preview
const menu = ref('') // '' | 'paragraph' | 'format'
const outlineOpen = ref(false)
const activeOutline = ref(-1)

/* AI 批注栏宽度：拖动分栏把手调整，双击复位，记住上次的值 */
const MARGIN_MIN = 190
const MARGIN_MAX = 520
const marginWidth = ref(232)
const resizing = ref(false)
try {
  const saved = Number(localStorage.getItem('note_margin_w'))
  if (Number.isFinite(saved) && saved >= MARGIN_MIN && saved <= MARGIN_MAX) marginWidth.value = saved
} catch {
  /* 隐私模式下读不到就用默认值 */
}

function startResize(e) {
  const paper = e.currentTarget.closest('.paper')
  if (!paper) return
  e.preventDefault()
  resizing.value = true
  const startX = e.clientX
  const startWidth = marginWidth.value
  const maxWidth = Math.max(MARGIN_MIN, Math.min(MARGIN_MAX, paper.getBoundingClientRect().width - 360))

  const onMove = (ev) => {
    const next = Math.min(maxWidth, Math.max(MARGIN_MIN, startWidth - (ev.clientX - startX)))
    marginWidth.value = Math.round(next)
  }
  const onUp = () => {
    resizing.value = false
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    try {
      localStorage.setItem('note_margin_w', String(marginWidth.value))
    } catch {
      /* 忽略 */
    }
  }
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
}

function resetMarginWidth() {
  marginWidth.value = 232
  try {
    localStorage.setItem('note_margin_w', '232')
  } catch {
    /* 忽略 */
  }
}

const paragraphOptions = [
  { key: 'p', label: '正文', hint: '' },
  { key: 'h1', label: '标题 1', hint: '# ' },
  { key: 'h2', label: '标题 2', hint: '## ' },
  { key: 'h3', label: '标题 3', hint: '### ' },
  { key: 'quote', label: '引用', hint: '> ' },
  { key: 'ul', label: '无序列表', hint: '- ' },
  { key: 'ol', label: '有序列表', hint: '1. ' },
  { key: 'code', label: '代码块', hint: '```' }
]

const formatOptions = [
  { key: 'bold', label: '加粗', hint: '**' },
  { key: 'italic', label: '斜体', hint: '*' },
  { key: 'strike', label: '删除线', hint: '~~' },
  { key: 'code', label: '行内代码', hint: '`' },
  { key: 'link', label: '链接', hint: '[ ]( )' }
]

/* ---------------- Markdown 预览 ---------------- */

function escapeHtml(text) {
  return String(text).replace(/[&<>"']/g, (ch) => {
    if (ch === '&') return '&amp;'
    if (ch === '<') return '&lt;'
    if (ch === '>') return '&gt;'
    if (ch === '"') return '&quot;'
    return '&#39;'
  })
}

/** 行内语法：先保护行内代码，再处理加粗 / 删除线 / 斜体 / 链接 */
function renderInline(text) {
  const codes = []
  let s = text.replace(/`([^`\n]+)`/g, (_, code) => {
    codes.push(code)
    return `\u0000C${codes.length - 1}\u0000`
  })

  s = s.replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
  s = s.replace(/~~([^~\n]+)~~/g, '<del>$1</del>')
  s = s.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>')
  s = s.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, label, url) => {
    const safe = /^(?:https?:\/\/|mailto:|\/|#)/i.test(url) ? url : '#'
    return `<a href="${safe}" target="_blank" rel="noopener noreferrer">${label}</a>`
  })

  return s.replace(/\u0000C(\d+)\u0000/g, (_, i) => `<code>${codes[Number(i)]}</code>`)
}

/**
 * 极简 Markdown -> HTML。
 * 先整体转义再套标签，所以正文里的 HTML 永远不会被当成标签执行。
 * 覆盖笔记常用的几种块：标题、段落、列表、引用、代码块、分隔线。
 */
function renderMarkdown(source) {
  const lines = escapeHtml(source || '').replace(/\r\n?/g, '\n').split('\n')
  const html = []
  let paragraph = []
  let headingIndex = 0

  const flushParagraph = () => {
    if (!paragraph.length) return
    html.push(`<p>${renderInline(paragraph.join('<br>'))}</p>`)
    paragraph = []
  }

  for (let i = 0; i < lines.length; ) {
    const line = lines[i]

    // 代码块：原样输出，不参与行内解析
    if (/^\s*```/.test(line)) {
      flushParagraph()
      const code = []
      i += 1
      while (i < lines.length && !/^\s*```/.test(lines[i])) {
        code.push(lines[i])
        i += 1
      }
      i += 1
      html.push(`<pre><code>${code.join('\n')}</code></pre>`)
      continue
    }

    if (!line.trim()) {
      flushParagraph()
      i += 1
      continue
    }

    const heading = /^(#{1,6})\s+(.*)$/.exec(line)
    if (heading) {
      flushParagraph()
      const level = heading[1].length
      const text = renderInline(heading[2].replace(/\s+#+\s*$/, ''))
      html.push(`<h${level} data-h="${headingIndex}">${text}</h${level}>`)
      headingIndex += 1
      i += 1
      continue
    }

    if (/^\s*(?:-{3,}|\*{3,})\s*$/.test(line)) {
      flushParagraph()
      html.push('<hr>')
      i += 1
      continue
    }

    if (/^\s*>\s?/.test(line)) {
      flushParagraph()
      const quote = []
      while (i < lines.length && /^\s*>\s?/.test(lines[i])) {
        quote.push(lines[i].replace(/^\s*>\s?/, ''))
        i += 1
      }
      html.push(`<blockquote>${renderInline(quote.join('<br>'))}</blockquote>`)
      continue
    }

    if (/^\s*[-*+]\s+/.test(line)) {
      flushParagraph()
      const items = []
      while (i < lines.length && /^\s*[-*+]\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*[-*+]\s+/, ''))
        i += 1
      }
      html.push(`<ul>${items.map((t) => `<li>${renderInline(t)}</li>`).join('')}</ul>`)
      continue
    }

    if (/^\s*\d+\.\s+/.test(line)) {
      flushParagraph()
      const items = []
      while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*\d+\.\s+/, ''))
        i += 1
      }
      html.push(`<ol>${items.map((t) => `<li>${renderInline(t)}</li>`).join('')}</ol>`)
      continue
    }

    paragraph.push(line.trim())
    i += 1
  }

  flushParagraph()
  return html.join('\n')
}

const hasContent = computed(() => String(draft.value?.content || '').trim().length > 0)

const previewHtml = computed(() => renderMarkdown(draft.value?.content))

/** 大纲：跳过代码块里的 #，offset 用于编辑模式下定位光标 */
const outline = computed(() => {
  const lines = String(draft.value?.content || '').split('\n')
  const items = []
  let offset = 0
  let fenced = false

  for (const line of lines) {
    if (/^\s*```/.test(line)) {
      fenced = !fenced
      offset += line.length + 1
      continue
    }
    if (!fenced) {
      const m = /^(#{1,6})\s+(.+?)\s*$/.exec(line)
      if (m) {
        items.push({
          level: m[1].length,
          text: m[2].replace(/\s+#+\s*$/, ''),
          offset,
          index: items.length
        })
      }
    }
    offset += line.length + 1
  }
  return items
})

const canSummarize = computed(
  () => !!draft.value?.id && String(draft.value.content || '').trim().length > 0 && userStore.aiQuota > 0
)

const saveLabel = computed(() => {
  if (saveState.value === 'saving') return '保存中…'
  if (saveState.value === 'error') return '保存失败'
  if (dirty.value) return '未保存'
  if (saveState.value === 'saved') return '已保存'
  return ''
})

async function openNote(id) {
  if (Number(route.params.id) === Number(id)) return
  await flushSave()
  router.push(`/notes/${id}`)
}

function upsertTab(id, title) {
  const tab = openTabs.value.find((t) => t.id === id)
  if (tab) tab.title = title || '无标题笔记'
  else openTabs.value.push({ id, title: title || '无标题笔记' })
}

async function switchTab(id) {
  if (draft.value?.id === id) return
  await openNote(id)
}

/** 关闭标签：当前这篇先落盘，再切到相邻的标签 */
async function closeTab(id) {
  const isCurrent = draft.value?.id === id
  if (isCurrent) await flushSave()
  const index = openTabs.value.findIndex((t) => t.id === id)
  if (index < 0) return
  openTabs.value.splice(index, 1)
  if (!isCurrent) return

  const next = openTabs.value[index] || openTabs.value[index - 1]
  if (next) router.push(`/notes/${next.id}`)
  else router.replace('/notes')
}

watch(
  () => draft.value?.title,
  (title) => {
    if (!draft.value) return
    const tab = openTabs.value.find((t) => t.id === draft.value.id)
    if (tab) tab.title = title || '无标题笔记'
  }
)

async function loadNote(id) {
  if (draft.value?.id && Number(draft.value.id) !== Number(id || 0)) await flushSave()
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
      status: 'private',
      tagIds: (data.tags || []).map((t) => t.id)
    }
    summaries.value = data.summaries || []
    saveState.value = 'idle'
    activeOutline.value = -1
    menu.value = ''
    dirty.value = false
    upsertTab(data.id, data.title)
    // 默认始终是预览：点「编辑」才进入可编辑的渲染视图
    mode.value = 'preview'
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

/* ---------------- 修改标记（不自动保存，等用户点「保存」） ---------------- */

// 由 loadNote 置位：屏蔽「程序写入 draft」引起的未保存标记
let suppressAutoSave = false

watch(
  () => [draft.value?.title, draft.value?.content, draft.value?.tagIds],
  () => {
    if (suppressAutoSave) return
    if (!draft.value?.id) return
    dirty.value = true
    saveState.value = 'idle'
  },
  { deep: true }
)

/**
 * 真正写库，只在用户点「保存」或切换笔记时调用。
 * - `announce` 为 true 时是用户点了「保存」，要给出明确反馈。
 */
async function saveNow(announce = false) {
  if (!draft.value?.id || saveState.value === 'saving') return
  saveState.value = 'saving'
  try {
    const { data } = await noteApi.update(draft.value.id, {
      title: draft.value.title,
      content: draft.value.content,
      status: 'private',
      tagIds: draft.value.tagIds
    })
    // 用服务端返回的标题兜底（空标题会被后端补成「无标题笔记」）
    draft.value.title = data.title
    // 让列表里的标题/摘要同步更新
    const i = notes.value.findIndex((n) => n.id === data.id)
    if (i >= 0) notes.value[i] = { ...notes.value[i], ...data }
    saveState.value = 'saved'
    dirty.value = false
    loadFolders()
    if (announce) toast.success('已保存')
  } catch (err) {
    saveState.value = 'error'
    toast.error(`保存失败：${err.message}`)
  }
}

/** 切走之前把未保存的改动落盘，避免切标签时丢内容 */
async function flushSave() {
  if (dirty.value) await saveNow(false)
}

/* ---------------- 所见即所得编辑 ---------------- */

const BLOCK_TAGS = /^(?:P|DIV|H[1-6]|UL|OL|LI|BLOCKQUOTE|PRE)$/
const TEXT_NODE = 3
const ELEMENT_NODE = 1

/** 工具栏点开菜单时按钮不抢焦点，命令仍然作用在正文选区上 */
function toggleMenu(name) {
  menu.value = menu.value === name ? '' : name
}

function placeCaret(node) {
  const range = document.createRange()
  range.selectNodeContents(node)
  range.collapse(true)
  const sel = window.getSelection()
  sel.removeAllRanges()
  sel.addRange(range)
}

function execCommand(command, value = null) {
  const el = editorEl.value
  if (!el) return
  el.focus()
  document.execCommand(command, false, value)
  onEditorInput()
}

/**
 * 把一个空块直接换成目标块（标题 / 引用 / 代码块 / 列表）。
 * 不走 execCommand：空块上它有时会作用到前一个块，而且不同浏览器结果不一致。
 */
function convertBlockTo(tag, removeChars = 0) {
  const editor = editorEl.value
  if (!editor) return
  const block = currentBlock()
  const raw = block && block !== editor ? block.textContent : ''
  const rest = raw.slice(removeChars)
  if (!block || block === editor) {
    // 空文档里直接输入 # / ```：先把刚敲的标记从根节点删掉
    if (removeChars > 0) replaceBeforeCaret(removeChars)
  }

  let replacement
  if (tag === 'ul' || tag === 'ol') {
    replacement = document.createElement(tag)
    const li = document.createElement('li')
    if (rest) li.textContent = rest
    else li.appendChild(document.createElement('br'))
    replacement.appendChild(li)
  } else {
    replacement = document.createElement(tag)
    if (rest) replacement.textContent = rest
    else replacement.appendChild(document.createElement('br'))
  }

  if (block && block !== editor) block.replaceWith(replacement)
  else editor.appendChild(replacement)

  editor.focus()
  const range = document.createRange()
  if (rest) {
    range.selectNodeContents(replacement)
    range.collapse(false)
  } else {
    // 空块里光标放在 <br> 前面，敲字才会落在块内
    range.setStart(replacement, 0)
    range.collapse(true)
  }
  const sel = window.getSelection()
  sel.removeAllRanges()
  sel.addRange(range)
  onEditorInput()
}

/** 光标所在的块级元素（标题 / 段落 / 列表项 / 引用 / 代码块） */
function currentBlock() {
  const sel = window.getSelection()
  if (!sel || !sel.rangeCount) return null
  let node = sel.anchorNode
  if (node && node.nodeType === TEXT_NODE) node = node.parentNode
  while (node && node !== editorEl.value) {
    if (node.nodeType === ELEMENT_NODE && BLOCK_TAGS.test(node.nodeName)) return node
    node = node.parentNode
  }
  return editorEl.value
}

/** 代码块转文字：<br> 必须是换行，否则多行代码会被拼成一行 */
function preToText(pre) {
  let text = ''
  pre.childNodes.forEach((child) => {
    if (child.nodeType === TEXT_NODE) {
      text += child.nodeValue.replace(/\u00a0/g, ' ')
    } else if (child.nodeName === 'BR') {
      text += '\n'
    } else {
      text += child.textContent
    }
  })
  return text.replace(/\n+$/, '')
}
function textBeforeCaret() {
  const sel = window.getSelection()
  if (!sel || !sel.rangeCount) return ''
  const block = currentBlock()
  if (!block) return ''
  const range = document.createRange()
  range.selectNodeContents(block)
  range.setEnd(sel.getRangeAt(0).startContainer, sel.getRangeAt(0).startOffset)
  return range.toString()
}

function replaceBeforeCaret(count) {
  const sel = window.getSelection()
  if (!sel || !sel.rangeCount) return
  const range = sel.getRangeAt(0)
  if (range.startContainer.nodeType !== TEXT_NODE) return
  if (range.startOffset < count) return
  range.setStart(range.startContainer, range.startOffset - count)
  range.deleteContents()
  range.collapse(true)
  sel.removeAllRanges()
  sel.addRange(range)
}

/** 编辑区 -> Markdown 源文（保存的仍然是纯 Markdown） */
function inlineToMd(node) {
  let out = ''
  node.childNodes.forEach((child) => {
    if (child.nodeType === TEXT_NODE) {
      out += child.nodeValue.replace(/\u00a0/g, ' ')
      return
    }
    if (child.nodeType !== ELEMENT_NODE) return
    const tag = child.nodeName
    if (tag === 'BR') {
      out += '\n'
      return
    }
    const inner = inlineToMd(child)
    if (tag === 'STRONG' || tag === 'B') out += `**${inner}**`
    else if (tag === 'EM' || tag === 'I') out += `*${inner}*`
    else if (tag === 'DEL' || tag === 'S' || tag === 'STRIKE') out += `~~${inner}~~`
    else if (tag === 'CODE') out += '`' + child.textContent.replace(/\u00a0/g, ' ') + '`'
    else if (tag === 'A') out += `[${inner}](${child.getAttribute('href') || ''})`
    else out += inner
  })
  return out
}

function blockToMd(node) {
  if (node.nodeType === TEXT_NODE) return node.nodeValue.replace(/\u00a0/g, ' ').trim()
  if (node.nodeType !== ELEMENT_NODE) return ''

  const tag = node.nodeName
  if (/^H[1-6]$/.test(tag)) {
    // 浏览器偶尔会把列表塞进标题里（<h2><ul>…），这时按块展开，别把列表吞掉
    if (node.querySelector(':scope > ul, :scope > ol, :scope > pre, :scope > blockquote')) {
      return [...node.childNodes].map(blockToMd).filter((s) => s !== '').join('\n\n')
    }
    return '#'.repeat(Number(tag[1])) + ' ' + inlineToMd(node).trim()
  }
  if (tag === 'UL' || tag === 'OL') {
    const items = [...node.children].filter((c) => c.nodeName === 'LI')
    return items
      .map((li, i) => (tag === 'OL' ? `${i + 1}. ` : '- ') + inlineToMd(li).trim())
      .join('\n')
  }
  if (tag === 'BLOCKQUOTE') {
    const text = inlineToMd(node).trim() || [...node.childNodes].map(blockToMd).join('\n\n')
    return text
      .split('\n')
      .map((line) => '> ' + line)
      .join('\n')
  }
  if (tag === 'PRE') {
    const code = node.querySelector('code')
    return '```\n' + preToText(code || node) + '\n```'
  }
  if (tag === 'HR') return '---'
  if (tag === 'P' || tag === 'DIV' || tag === 'SECTION') {
    const hasBlockChild = [...node.children].some((c) => BLOCK_TAGS.test(c.nodeName) || c.nodeName === 'HR')
    if (hasBlockChild) {
      return [...node.childNodes]
        .map(blockToMd)
        .filter((s) => s !== '')
        .join('\n\n')
    }
    return inlineToMd(node).trim()
  }
  return inlineToMd(node).trim()
}

function serializeEditor(root) {
  if (!root) return ''
  const blocks = []
  root.childNodes.forEach((node) => {
    const md = blockToMd(node)
    if (md !== null && md.trim() !== '') blocks.push(md.replace(/\s+$/, ''))
  })
  return blocks.join('\n\n').replace(/\n{3,}/g, '\n\n').replace(/\n+$/, '')
}

function onEditorInput() {
  const el = editorEl.value
  if (!el) return
  draft.value.content = serializeEditor(el)
  syncEditorEmpty(el)
}

/** 空内容时显示占位提示（不能靠 :empty —— 文档里通常还留着一个空段落） */
function syncEditorEmpty(el) {
  if (!el) return
  el.classList.toggle('is-empty', !el.textContent.trim())
}

/** 输入 # / > / - / 1. / ``` 后自动变成对应块，像在预览里直接写 */
function onEditorKeydown(e) {
  if (e.isComposing) return
  const block = currentBlock()
  const before = textBeforeCaret()

  if (e.key === 'Enter') {
    // 标题里回车：落到正文段落，而不是继续生成标题
    if (block && /^H[1-6]$/.test(block.nodeName)) {
      e.preventDefault()
      const p = document.createElement('p')
      p.innerHTML = '<br>'
      block.after(p)
      editorEl.value.focus()
      placeCaret(p)
      onEditorInput()
      return
    }
    if (before === '```') {
      e.preventDefault()
      if (block && block.nodeName === 'PRE') {
        replaceBeforeCaret(3)
        const p = document.createElement('p')
        p.innerHTML = '<br>'
        block.after(p)
        editorEl.value.focus()
        placeCaret(p)
        onEditorInput()
      } else {
        convertBlockTo('pre', 3)
      }
      return
    }
    // 代码块里在空行上再按一次 Enter 就跳出来
    if (block && block.nodeName === 'PRE' && block.lastChild?.nodeName === 'BR') {
      e.preventDefault()
      const p = document.createElement('p')
      p.innerHTML = '<br>'
      block.after(p)
      editorEl.value.focus()
      placeCaret(p)
      onEditorInput()
    }
    return
  }

  if (e.key !== ' ' || (block && block.nodeName === 'PRE')) return

  let target = ''
  if (/^#{1,6}$/.test(before)) target = `h${before.length}`
  else if (before === '>') target = 'blockquote'
  else if (before === '-' || before === '*') target = 'ul'
  else if (/^\d+\.$/.test(before)) target = 'ol'
  if (!target) return

  e.preventDefault()
  convertBlockTo(target, before.length)
}

function onPaste(e) {
  e.preventDefault()
  const text = (e.clipboardData || window.clipboardData)?.getData('text/plain') || ''
  if (!text) return
  // 粘进来的是 Markdown 就按 Markdown 渲染，否则当纯文本
  if (/(^|\n)(#{1,6}\s|```|[-*+]\s|>\s)/.test(text)) {
    document.execCommand('insertHTML', false, renderMarkdown(text))
  } else {
    document.execCommand('insertText', false, text)
  }
  onEditorInput()
}

function onEditorClick(e) {
  if (e.target.closest && e.target.closest('a')) e.preventDefault()
}

/**
 * 用语义标签包住选区。
 * 不用 execCommand('bold')：在 <h1> 里它认为「已经是粗体」，点一下反而会取消加粗。
 */
function wrapSelection(tag, placeholder, attrs = {}) {
  const el = editorEl.value
  const sel = window.getSelection()
  if (!el || !sel || !sel.rangeCount) return
  const range = sel.getRangeAt(0)
  const wrapper = document.createElement(tag)
  Object.entries(attrs).forEach(([key, value]) => wrapper.setAttribute(key, value))

  if (range.collapsed) {
    wrapper.textContent = placeholder
    range.insertNode(wrapper)
  } else {
    wrapper.appendChild(range.extractContents())
    range.insertNode(wrapper)
  }

  const next = document.createRange()
  next.selectNodeContents(wrapper)
  sel.removeAllRanges()
  sel.addRange(next)
  onEditorInput()
}

function applyFormat(key) {
  menu.value = ''
  if (!draft.value || mode.value !== 'edit') return
  if (key === 'bold') wrapSelection('strong', '加粗文字')
  else if (key === 'italic') wrapSelection('em', '斜体文字')
  else if (key === 'strike') wrapSelection('del', '删除线文字')
  else if (key === 'code') wrapSelection('code', '代码')
  else if (key === 'link') {
    const url = window.prompt('链接地址', 'https://')
    if (!url) return
    wrapSelection('a', url, { href: url })
  }
}

/** 列表必须先落到普通段落上，否则浏览器会生成 <h2><ul>…</ul></h2> 这种非法结构 */
function ensureParagraphBlock() {
  const editor = editorEl.value
  const block = currentBlock()
  if (!editor || !block || block === editor) return
  if (block.nodeName === 'P' || block.nodeName === 'DIV' || block.nodeName === 'LI') return

  const paragraph = document.createElement('p')
  paragraph.textContent = block.textContent
  block.replaceWith(paragraph)
  editor.focus()
  const range = document.createRange()
  range.selectNodeContents(paragraph)
  range.collapse(false)
  const sel = window.getSelection()
  sel.removeAllRanges()
  sel.addRange(range)
}

function applyParagraph(key) {
  menu.value = ''
  if (!draft.value || mode.value !== 'edit') return

  const block = currentBlock()

  if (key === 'ul' || key === 'ol') {
    ensureParagraphBlock()
    execCommand(key === 'ul' ? 'insertUnorderedList' : 'insertOrderedList')
    return
  }

  if (key === 'p') {
    // 在列表里选「正文」= 退出列表
    if (block && block.nodeName === 'LI') {
      execCommand(block.parentNode?.nodeName === 'OL' ? 'insertOrderedList' : 'insertUnorderedList')
      return
    }
    execCommand('formatBlock', 'p')
    return
  }

  // 列表项直接套标题会产生 <li><h2>，先把列表关掉
  if (block && block.nodeName === 'LI') {
    execCommand(block.parentNode?.nodeName === 'OL' ? 'insertOrderedList' : 'insertUnorderedList')
  }

  const tag = { h1: 'h1', h2: 'h2', h3: 'h3', quote: 'blockquote', code: 'pre' }[key]
  if (tag) execCommand('formatBlock', tag)
}

function toggleOutline() {
  outlineOpen.value = !outlineOpen.value
  if (outlineOpen.value) nextTick(syncActiveHeading)
}

/** 预览 -> 编辑：把 Markdown 渲染成可直接编辑的内容，光标放到文末 */
async function startEdit(anchorIndex = -1) {
  if (mode.value === 'edit') return
  menu.value = ''
  mode.value = 'edit'
  activeOutline.value = -1
  await nextTick()
  const el = editorEl.value
  if (!el) return
  el.innerHTML = renderMarkdown(draft.value?.content) || ''
  syncEditorEmpty(el)
  el.focus()

  const headings = el.querySelectorAll('h1,h2,h3,h4,h5,h6')
  const anchor = anchorIndex >= 0 ? headings[anchorIndex] : null
  const range = document.createRange()
  if (anchor) {
    range.selectNodeContents(anchor)
    range.collapse(false)
  } else {
    range.selectNodeContents(el)
    range.collapse(false)
  }
  const sel = window.getSelection()
  sel.removeAllRanges()
  sel.addRange(range)
  anchor?.scrollIntoView({ block: 'start' })
}

/** 点预览里的正文直接进编辑；点链接仍然按链接走 */
function onPreviewClick(e) {
  if (e.target.closest?.('a')) return
  const heading = e.target.closest?.('h1,h2,h3,h4,h5,h6')
  let index = -1
  if (heading && previewEl.value) {
    index = [...previewEl.value.querySelectorAll('h1,h2,h3,h4,h5,h6')].indexOf(heading)
  }
  startEdit(index)
}

async function toggleMode() {
  menu.value = ''
  if (mode.value === 'edit') {
    // 只切换视图，不落盘；保存交给「保存」按钮
    mode.value = 'preview'
    activeOutline.value = -1
  } else {
    await startEdit()
  }
}

/** 光标所在的最近一个标题，用来在大纲里标出当前位置 */
function syncActiveHeading() {
  if (!outlineOpen.value || mode.value !== 'edit' || !editorEl.value) return
  const sel = window.getSelection()
  if (!sel || !sel.rangeCount) return
  let node = sel.anchorNode
  if (node && node.nodeType === TEXT_NODE) node = node.parentNode
  while (node && node !== editorEl.value && !/^H[1-6]$/.test(node.nodeName)) {
    node = node.parentNode
  }
  if (!node || node === editorEl.value) {
    activeOutline.value = -1
    return
  }
  const headings = [...editorEl.value.querySelectorAll('h1,h2,h3,h4,h5,h6')]
  activeOutline.value = headings.indexOf(node)
}

function jumpToHeading(item, index) {
  activeOutline.value = index

  if (mode.value === 'preview') {
    previewEl.value?.querySelector(`[data-h="${item.index}"]`)?.scrollIntoView({ block: 'start' })
    return
  }

  const headings = editorEl.value?.querySelectorAll('h1,h2,h3,h4,h5,h6')
  const target = headings?.[item.index]
  if (!target) return
  editorEl.value.focus()
  const range = document.createRange()
  range.selectNodeContents(target)
  range.collapse(true)
  const sel = window.getSelection()
  sel.removeAllRanges()
  sel.addRange(range)
  target.scrollIntoView({ block: 'start' })
}

function onSelectionChange() {
  if (mode.value !== 'edit') return
  syncActiveHeading()
}

/** 有未保存修改时关页面 / 刷新，让浏览器弹确认 */
function onBeforeUnload(e) {
  if (!dirty.value) return
  e.preventDefault()
  e.returnValue = ''
}

function onDocumentClick(e) {
  if (menu.value && !toolbarEl.value?.contains(e.target)) menu.value = ''
  if (tagPanelOpen.value && !tagMenuEl.value?.contains(e.target)) tagPanelOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('selectionchange', onSelectionChange)
  window.addEventListener('beforeunload', onBeforeUnload)
})

/* ---------------- 新建 / 删除 ---------------- */

const creating = ref(false)

async function createNote() {
  if (creating.value) return
  creating.value = true
  try {
    await flushSave()
    const { data } = await noteApi.create({ title: '', content: '', status: 'private', tagIds: [] })
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
    const deletedId = draft.value.id
    await noteApi.remove(deletedId)
    openTabs.value = openTabs.value.filter((t) => t.id !== deletedId)
    dirty.value = false
    toast.success('已移入回收站，可在回收站恢复')
    await loadList()
    await loadFolders()
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
    toast.success('已生成摘要')
  } catch (err) {
    toast.error(err.message)
  } finally {
    summarizing.value = false
  }
}

/**
 * AI 摘要回填正文（需求 4.3.5）。
 * 覆盖是不可撤销的，所以先确认；摘要本身仍留在右侧批注里，正文被覆盖也不会丢记录。
 */
async function useSummary(s) {
  if (!draft.value) return
  if (!window.confirm('用这条摘要替换正文？当前正文会被覆盖。')) return
  draft.value.content = s.summary
  if (mode.value === 'edit' && editorEl.value) {
    editorEl.value.innerHTML = renderMarkdown(s.summary)
  }
  await saveNow(false)
  if (saveState.value !== 'error') toast.success('已替换正文')
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
loadFolders()

onBeforeUnmount(() => {
  clearTimeout(searchTimer)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('selectionchange', onSelectionChange)
  window.removeEventListener('beforeunload', onBeforeUnload)
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

.folders {
  flex: none;
  display: flex;
  flex-direction: column;
  min-height: 120px;
  max-height: calc(100% - 180px);
  padding: 0 var(--sp-3) var(--sp-2);
  border-bottom: 1px solid var(--rule);
}

.folders__head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--sp-1) 6px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.folders__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.tag-toggle {
  padding: 2px 7px;
  border: 1px solid transparent;
  border-radius: var(--r-pill);
  background: transparent;
  color: var(--ink-3);
  font-size: var(--fs-xs);
  cursor: pointer;
}

.tag-toggle:hover {
  background: var(--paper-deep);
  color: var(--ink);
}

.tag-toggle.is-active {
  border-color: var(--rule-strong);
  background: var(--sheet);
  color: var(--accent-ink);
}

.folders__add {
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.folders__add:hover {
  background: var(--paper-deep);
  color: var(--ink);
}

.folders__list {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
  overflow-y: auto;
}

.folder {
  width: 100%;
  padding: 5px var(--sp-2);
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  text-align: left;
  cursor: pointer;
}

.folder:hover {
  background: var(--paper-deep);
}

.folder.is-active {
  background: var(--accent-soft);
  color: var(--accent-ink);
}

.folder-row {
  display: flex;
  align-items: center;
  border-radius: var(--r-sm);
}

.folder-row:hover {
  background: var(--paper-deep);
}

.folder-row.is-active {
  background: var(--accent-soft);
  color: var(--accent-ink);
}

.folder-row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px var(--sp-2);
  border: 0;
  background: transparent;
  color: inherit;
  font-size: var(--fs-sm);
  text-align: left;
  cursor: pointer;
}

.folder-row__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-row__count {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--ink-3);
}

.folder-row__edit {
  flex: none;
  width: 22px;
  height: 22px;
  margin-right: 4px;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  cursor: pointer;
  opacity: 0;
}

.folder-row:hover .folder-row__edit,
.folder-row.is-active .folder-row__edit {
  opacity: 1;
}

.folder-row__edit:hover {
  background: var(--paper-sink);
  color: var(--ink);
}

.tag-menu {
  position: relative;
}

.tag-panel {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 30;
  width: 216px;
  max-height: 260px;
  overflow-y: auto;
  padding: var(--sp-2);
  background: var(--sheet);
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  box-shadow: var(--shadow-pop);
}

.tag-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--sp-1) 6px;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.tag-panel__clear {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: var(--fs-xs);
  cursor: pointer;
}

.tag-panel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-height: 92px;
  overflow-y: auto;
}

.tag-chip {
  padding: 2px 8px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-pill);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  cursor: pointer;
}

.tag-chip:hover {
  border-color: var(--ink-3);
  color: var(--ink);
}

.tag-chip.is-active {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--ink-inverse);
}

.tag-filter__empty {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

/* 文件夹区与笔记列表之间的上下拖拽条 */
.v-resizer {
  flex: none;
  position: relative;
  height: 7px;
  cursor: row-resize;
  background: transparent;
}

.v-resizer::before {
  content: '';
  position: absolute;
  top: 50%;
  left: var(--sp-3);
  right: var(--sp-3);
  height: 1px;
  transform: translateY(-50%);
  background: var(--rule);
  transition: height 0.15s ease, background-color 0.15s ease;
}

.v-resizer:hover::before,
.list.is-resizing .v-resizer::before {
  height: 2px;
  background: var(--accent);
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

/* ---------- 打开的笔记标签 ---------- */

.tabs {
  flex: none;
  display: flex;
  align-items: flex-end;
  gap: 2px;
  padding: 6px var(--sp-3) 0;
  background: var(--paper-deep);
  border-bottom: 1px solid var(--rule);
  overflow-x: auto;
  scrollbar-width: none;
}

.tabs::-webkit-scrollbar {
  display: none;
}

.tab {
  flex: none;
  display: flex;
  align-items: center;
  max-width: 200px;
  border: 1px solid transparent;
  border-bottom: 0;
  border-radius: var(--r-sm) var(--r-sm) 0 0;
  color: var(--ink-3);
}

.tab:hover {
  color: var(--ink-2);
}

.tab.is-active {
  background: var(--paper);
  border-color: var(--rule);
  color: var(--ink);
}

.tab__main {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  padding: 6px 4px 6px 10px;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: var(--fs-xs);
  cursor: pointer;
}

.tab__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 未保存：实心圆点 */
.tab__dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--accent);
}

.tab__close {
  flex: none;
  width: 18px;
  height: 18px;
  margin-right: 4px;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.tab__close:hover {
  background: var(--paper-sink);
  color: var(--ink);
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

.bar__save {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  min-width: 60px;
  text-align: right;
}

.bar__save.is-error {
  color: var(--danger);
}

/* ---------- Markdown 工具栏 ---------- */

.md-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  padding: 5px var(--sp-4);
  border-bottom: 1px solid var(--rule);
  background: var(--paper);
  flex-wrap: wrap;
}

.md-bar__group {
  position: relative;
}

.md-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  cursor: pointer;
}

.md-btn:hover {
  background: var(--paper-deep);
  border-color: var(--rule);
  color: var(--ink);
}

.md-btn.is-open {
  background: var(--accent-soft);
  border-color: var(--rule-strong);
  color: var(--accent-ink);
}

.md-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.md-btn:disabled:hover {
  background: transparent;
  border-color: transparent;
  color: var(--ink-2);
}

/* 预览 / 编辑切换放在最右，像稿纸上的一个模式开关 */
.md-btn--mode {
  margin-left: auto;
  color: var(--accent);
}

.md-btn--mode:hover {
  background: var(--accent-soft);
  border-color: var(--rule-strong);
}

.md-caret {
  color: var(--ink-3);
  font-size: 9px;
  line-height: 1;
}

.md-btn.is-open .md-caret {
  color: var(--accent-ink);
}

.md-bar__rule {
  width: 1px;
  height: 16px;
  background: var(--rule);
}

.md-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  min-width: 168px;
  padding: 4px;
  background: var(--sheet);
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  box-shadow: var(--shadow-pop);
}

.md-menu__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-4);
  width: 100%;
  padding: 6px 8px;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  text-align: left;
  cursor: pointer;
}

.md-menu__item:hover {
  background: var(--paper);
  color: var(--ink);
}

.md-menu__hint {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--ink-3);
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
  position: relative;
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 9px var(--margin-w, 232px);
  background: var(--sheet);
  border: 1px solid var(--rule);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-sheet);
  overflow: hidden;
}

.paper.is-resizing {
  cursor: col-resize;
  user-select: none;
}

/* 大纲打开时在左侧插一列，正文与 AI 批注的位置不变 */
.paper.is-outlining {
  grid-template-columns: 196px minmax(0, 1fr) 9px var(--margin-w, 232px);
}

/* 分栏把手：一条可拖的线，悬停变墨蓝 */
.resizer {
  position: relative;
  z-index: 2;
  cursor: col-resize;
  background: transparent;
  touch-action: none;
}

.resizer::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  transform: translateX(-50%);
  background: var(--rule);
  transition: background-color 0.15s ease, width 0.15s ease;
}

.resizer:hover::before,
.paper.is-resizing .resizer::before {
  width: 2px;
  background: var(--accent);
}

.paper__write {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: var(--sp-6) var(--sp-6) var(--sp-4);
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

/* ---------- Markdown 预览 ---------- */

.preview {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-top: var(--sp-2);
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  line-height: 1.9;
  color: var(--ink);
}

/* 编辑态：同一套排版，只是多了一个可编辑光标 */
.preview--edit {
  outline: none;
  cursor: text;
}

.preview--edit.is-empty::before {
  content: attr(data-placeholder);
  color: var(--ink-3);
  pointer-events: none;
}

.preview--edit a {
  cursor: text;
}

/* 预览也做成「点一下就写」，光标提示可编辑 */
.preview:not(.preview--edit) {
  cursor: text;
}

.preview--empty {
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  width: 100%;
  padding: var(--sp-2) 0 0;
  border: 0;
  background: transparent;
  color: var(--ink-3);
  font-family: var(--font-serif);
  font-size: var(--fs-md);
  line-height: 1.9;
  text-align: left;
  cursor: text;
}

.preview :deep(h1),
.preview :deep(h2),
.preview :deep(h3),
.preview :deep(h4),
.preview :deep(h5),
.preview :deep(h6) {
  margin: var(--sp-4) 0 var(--sp-2);
  scroll-margin-top: var(--sp-2);
}

.preview :deep(h1) {
  font-size: var(--fs-xl);
}

.preview :deep(h2) {
  font-size: var(--fs-lg);
}

.preview :deep(h3) {
  font-size: var(--fs-md);
}

.preview :deep(h4),
.preview :deep(h5),
.preview :deep(h6) {
  font-size: var(--fs-base);
}

.preview :deep(h1:first-child),
.preview :deep(h2:first-child),
.preview :deep(h3:first-child) {
  margin-top: 0;
}

.preview :deep(p) {
  margin: 0 0 var(--sp-3);
}

.preview :deep(ul),
.preview :deep(ol) {
  margin: 0 0 var(--sp-3);
  padding-left: 1.4em;
}

.preview :deep(ul) {
  list-style: disc;
}

.preview :deep(ol) {
  list-style: decimal;
}

.preview :deep(li) {
  margin: 2px 0;
}

.preview :deep(blockquote) {
  margin: 0 0 var(--sp-3);
  padding-left: var(--sp-3);
  border-left: 2px solid var(--rule-strong);
  color: var(--ink-2);
}

.preview :deep(code) {
  padding: 1px 4px;
  border-radius: var(--r-sm);
  background: var(--paper-deep);
  font-family: var(--font-mono);
  font-size: 0.86em;
}

.preview :deep(pre) {
  margin: 0 0 var(--sp-3);
  padding: var(--sp-3) var(--sp-4);
  overflow-x: auto;
  border-left: 2px solid var(--rule-strong);
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
  background: var(--paper);
}

.preview :deep(pre code) {
  padding: 0;
  background: transparent;
  font-size: 0.84em;
  line-height: 1.7;
}

.preview :deep(a) {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.preview :deep(hr) {
  margin: var(--sp-4) 0;
  border: 0;
  border-top: 1px solid var(--rule);
}

.preview :deep(strong) {
  font-weight: 600;
}

.preview :deep(del) {
  color: var(--ink-3);
}

/* ---------- 页边批注 ---------- */

.paper__margin {
  min-height: 0;
  overflow-y: auto;
  padding: var(--sp-6) var(--sp-4);
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

/* ---------- 大纲 ---------- */

.outline {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: var(--sp-6) var(--sp-4);
  border-right: 1px solid var(--rule);
  background: var(--paper);
  overflow: hidden;
}

.outline__head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  padding-bottom: var(--sp-2);
  margin-bottom: var(--sp-3);
  border-bottom: 1px solid var(--rule);
  font-family: var(--font-serif);
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

.outline__close {
  width: 20px;
  height: 20px;
  padding: 0;
  border: 0;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--ink-3);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.outline__close:hover {
  background: var(--paper-deep);
  color: var(--ink);
}

.outline__empty {
  font-size: var(--fs-xs);
  line-height: 1.8;
  color: var(--ink-3);
}

.outline__empty code {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--accent-ink);
}

.outline__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin: 0 calc(var(--sp-1) * -1);
}

.outline__item {
  display: block;
  width: 100%;
  padding: 4px 6px;
  border: 0;
  border-left: 2px solid transparent;
  border-radius: 0 var(--r-sm) var(--r-sm) 0;
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  line-height: 1.6;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.outline__item:hover {
  background: var(--paper-deep);
  color: var(--ink);
}

.outline__item.is-active {
  border-left-color: var(--accent);
  background: var(--accent-soft);
  color: var(--accent-ink);
}

.marginalia {
  position: relative;
  padding: 0 0 var(--sp-4) var(--sp-3);
  border-left: 1px solid var(--rule-strong);
  margin-bottom: var(--sp-3);
}

.marginalia__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sp-2);
  margin-bottom: 3px;
}

.marginalia__time {
  display: block;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.marginalia__text {
  font-family: var(--font-serif);
  font-size: var(--fs-sm);
  line-height: 1.8;
  color: var(--ink-2);
  white-space: pre-wrap;
}

/* 替换正文：每条摘要的左上角 */
.marginalia__use {
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: var(--fs-xs);
  cursor: pointer;
}

.marginalia__use:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

.marginalia.is-pending {
  opacity: 0.75;
}

/* ---------- 标签 / 文件夹弹窗 ---------- */

.folder-hint {
  margin: var(--sp-3) 0 var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.tag-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 220px;
  overflow-y: auto;
}

.tag-picker__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 9px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-pill);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.tag-picker__item:hover {
  border-color: var(--ink-3);
}

.tag-picker__item input {
  margin: 0;
}

.tag-picker__empty {
  font-size: var(--fs-sm);
  color: var(--ink-3);
}

.tag-picker__new {
  display: flex;
  gap: var(--sp-2);
  margin-top: var(--sp-4);
}

.tag-picker__input {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--rule-strong);
  border-radius: var(--r-sm);
  background: var(--sheet);
  font-size: var(--fs-sm);
}

.tag-picker__input:focus {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-soft);
}

.modal-spacer {
  flex: 1;
}

/* 触屏设备没有 hover，文件夹的编辑按钮要常显 */
@media (hover: none) {
  .folder-row__edit {
    opacity: 1;
  }
}


/* 三列（大纲 + 正文 + 批注）只在足够宽时使用。
   再窄就收起批注列，把宽度留给正文与大纲。 */
@media (max-width: 1400px) {
  .paper.is-outlining {
    grid-template-columns: 184px minmax(0, 1fr);
  }

  .paper.is-outlining .paper__margin,
  .paper.is-outlining .resizer {
    display: none;
  }
}

@media (max-width: 1180px) {
  .paper {
    grid-template-columns: minmax(0, 1fr) 9px clamp(180px, var(--margin-w, 232px), 200px);
  }

  .paper.is-outlining {
    grid-template-columns: 168px minmax(0, 1fr);
  }
}

@media (max-width: 900px) {
  .paper {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) auto;
  }

  /* 单列时大纲在上、正文居中、批注在下 */
  .paper.is-outlining {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr) auto;
  }

  .paper.is-outlining .paper__margin {
    display: block;
  }

  .resizer {
    display: none;
  }

  .paper__margin {
    max-height: 38vh;
    border-left: 0;
    border-top: 1px solid var(--rule);
  }

  .outline {
    max-height: 22vh;
    padding: var(--sp-3) var(--sp-4);
    border-right: 0;
    border-bottom: 1px solid var(--rule);
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

  /* 窄屏把文件夹和标签区收短，给笔记列表留出空间；上下拖拽条也不再用 */
  .folders {
    height: auto !important;
    max-height: 200px;
  }

  .folders__list {
    flex: none;
    max-height: 96px;
  }

  .tag-panel__chips {
    max-height: 56px;
  }

  .v-resizer {
    display: none;
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
