<template>
  <div>
    <div class="flex justify-between items-center mb-6 gap-3 flex-wrap">
      <div class="flex items-center gap-3">
        <h1 class="text-2xl font-bold">{{ articleId ? '编辑文章' : '写文章' }}</h1>
        <span v-if="saveState.text" class="text-xs px-2 py-1 rounded" :class="saveState.cls">{{ saveState.text }}</span>
      </div>
      <div class="flex gap-2 flex-wrap">
        <button @click="openPreview" class="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:border-gray-600 transition" title="在新窗口预览草稿">预览</button>
        <button v-if="articleId" @click="goRevisions" class="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:border-gray-600 transition">版本历史</button>
        <button @click="save(0)" :disabled="saving" class="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:border-gray-600 disabled:opacity-50 transition">
          {{ saving ? '保存中…' : '保存草稿' }}
        </button>
        <button @click="save(1)" :disabled="saving" class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 disabled:opacity-50 transition">
          {{ saving ? '发布中…' : '发布' }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="text-center py-10 text-gray-400">加载中...</div>
    <div v-else class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 sm:p-6 space-y-4">
      <div class="flex flex-col lg:flex-row gap-4">
        <input v-model="form.title" placeholder="文章标题" aria-label="文章标题" class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-lg" />
        <div class="flex gap-2 items-center">
          <select v-model="form.categoryId" aria-label="文章分类" class="px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600">
            <option :value="null">选择分类</option>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <button @click="quickCreateCategory" class="px-2 py-2 border rounded-lg text-sm hover:border-primary-500 hover:text-primary-500" title="新建分类">＋</button>
        </div>
      </div>

      <div class="flex flex-col lg:flex-row gap-4">
        <div class="flex-1">
          <input v-model="form.slug" placeholder="自定义URL(可选)" aria-label="自定义 URL" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" @blur="checkSlug" @input="slugState = 'idle'" />
          <p class="mt-1 text-xs" :class="slugHint.cls">{{ slugHint.text }}</p>
        </div>
        <input v-model="form.series" placeholder="系列名(可选)" aria-label="系列名" class="lg:w-40 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
        <input v-model="form.publishAt" type="datetime-local" aria-label="定时发布" class="lg:w-56 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
        <label class="flex items-center gap-1 text-xs text-gray-500 lg:border lg:rounded-lg lg:px-3 lg:py-2 dark:border-gray-600">
          <input type="checkbox" v-model="form.isTop" :true-value="1" :false-value="0" class="mr-1" /> 置顶
        </label>
      </div>

      <div>
        <div class="flex items-center justify-between mb-1">
          <label class="text-sm text-gray-500">标签</label>
          <div class="flex items-center gap-2">
            <input v-model="newTagName" placeholder="新标签名" class="px-2 py-1 border rounded text-xs dark:bg-gray-700 dark:border-gray-600 w-28" @keyup.enter="quickCreateTag" />
            <button @click="quickCreateTag" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500">＋ 新建</button>
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <label v-for="t in tags" :key="t.id" class="flex items-center gap-1 px-3 py-1.5 rounded-full text-xs border cursor-pointer transition" :class="form.tagIds.includes(t.id) ? 'bg-primary-50 border-primary-300 text-primary-600 dark:bg-primary-900/30 dark:border-primary-600 dark:text-primary-400' : 'border-gray-200 dark:border-gray-600 text-gray-500 hover:border-primary-300'">
            <input type="checkbox" :value="t.id" v-model="form.tagIds" class="hidden" />
            {{ t.name }}
          </label>
          <span v-if="tags.length === 0" class="text-xs text-gray-400">暂无标签，可在此直接新建</span>
        </div>
      </div>

      <input v-model="form.summary" placeholder="文章摘要（可选，留空发布时自动取正文前 120 字）" aria-label="文章摘要" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />

      <div>
        <label class="text-sm text-gray-500 mb-1 block">封面图</label>
        <div class="flex gap-2">
          <input v-model="form.cover" placeholder="封面图URL" class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <button @click="mediaOpen = true" class="px-3 py-2 border rounded-lg text-sm hover:border-primary-500 hover:text-primary-500">素材库</button>
          <label class="px-3 py-2 border-2 border-dashed rounded-lg text-xs cursor-pointer hover:border-primary-500 hover:text-primary-500 whitespace-nowrap flex items-center">
            <input type="file" accept="image/*" class="hidden" @change="uploadCover" />
            {{ coverUploading ? '上传中...' : '上传' }}
          </label>
        </div>
        <img v-if="form.cover" :src="form.cover" alt="封面预览" class="mt-2 h-32 rounded-lg object-cover" />
      </div>

      <div>
        <div class="flex items-center justify-between mb-1">
          <label class="text-sm text-gray-500">内容（Markdown）</label>
          <div class="flex items-center gap-2 text-xs text-gray-400">
            <span>{{ wordCount }} 字</span>
            <span>{{ readMinutes }} 分钟阅读</span>
          </div>
        </div>

        <!-- 工具栏 -->
        <div class="flex flex-wrap gap-1 mb-2">
          <button type="button" @click="tool.bold" class="px-2 py-1 border rounded text-xs font-bold hover:border-primary-500 hover:text-primary-500" title="加粗 (Ctrl+B)">B</button>
          <button type="button" @click="tool.italic" class="px-2 py-1 border rounded text-xs italic hover:border-primary-500 hover:text-primary-500" title="斜体 (Ctrl+I)">I</button>
          <button type="button" @click="tool.strike" class="px-2 py-1 border rounded text-xs line-through hover:border-primary-500 hover:text-primary-500" title="删除线">S</button>
          <button type="button" @click="tool.inlineCode" class="px-2 py-1 border rounded text-xs font-mono hover:border-primary-500 hover:text-primary-500" title="行内代码">`</button>
          <span class="text-gray-300 dark:text-gray-600 mx-1">|</span>
          <button type="button" @click="tool.heading(1)" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500" title="一级标题">H1</button>
          <button type="button" @click="tool.heading(2)" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500" title="二级标题">H2</button>
          <button type="button" @click="tool.heading(3)" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500" title="三级标题">H3</button>
          <span class="text-gray-300 dark:text-gray-600 mx-1">|</span>
          <button type="button" @click="tool.list(false)" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500" title="无序列表">• 列表</button>
          <button type="button" @click="tool.list(true)" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500" title="有序列表">1. 列表</button>
          <button type="button" @click="tool.taskList" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500" title="任务列表">☑ 任务</button>
          <button type="button" @click="tool.quote" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500" title="引用">❝</button>
          <button type="button" @click="tool.table" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500" title="插入表格">▦ 表格</button>
          <button type="button" @click="tool.hr" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500" title="分割线">—</button>
          <span class="text-gray-300 dark:text-gray-600 mx-1">|</span>
          <label class="px-3 py-1 border border-dashed rounded text-xs cursor-pointer hover:border-primary-500 hover:text-primary-500" title="插入图片">
            <input type="file" accept="image/*" class="hidden" @change="insertImage" />
            📷 插图
          </label>
          <button type="button" @click="mediaOpen = true" class="px-3 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500">🖼 素材库</button>
          <button type="button" @click="tool.codeBlock" class="px-3 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500">&lt;/&gt; 代码块</button>
          <button type="button" @click="tool.link" class="px-3 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500">🔗 链接</button>
        </div>

        <!-- 上传进度条 -->
        <div v-if="uploading" class="mb-2">
          <div class="flex items-center gap-2 text-xs text-gray-500 mb-1">
            <span>上传中 {{ uploadProgress }}%</span>
          </div>
          <div class="h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
            <div class="h-full bg-primary-500 transition-all" :style="{ width: uploadProgress + '%' }"></div>
          </div>
        </div>

        <!-- 三态布局：编辑 / 并排 / 预览 -->
        <div ref="editorWrap" class="border rounded-lg dark:border-gray-600 overflow-hidden flex flex-col bg-white dark:bg-gray-800">
          <div class="flex items-center justify-between px-3 py-1.5 border-b dark:border-gray-700 bg-gray-50 dark:bg-gray-700/40">
            <div class="flex gap-1 text-xs">
              <button @click="layoutMode = 'split'" :class="layoutMode === 'split' ? 'bg-primary-500 text-white' : 'text-gray-500 hover:text-primary-500'" class="px-2 py-1 rounded">并排</button>
              <button @click="layoutMode = 'edit'" :class="layoutMode === 'edit' ? 'bg-primary-500 text-white' : 'text-gray-500 hover:text-primary-500'" class="px-2 py-1 rounded">编辑</button>
              <button @click="layoutMode = 'preview'" :class="layoutMode === 'preview' ? 'bg-primary-500 text-white' : 'text-gray-500 hover:text-primary-500'" class="px-2 py-1 rounded">预览</button>
            </div>
            <button @click="toggleFullscreen" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500">{{ isFullscreen ? '退出全屏' : '全屏' }}</button>
          </div>

          <div class="grid" :class="layoutClass" ref="editorGrid">
            <div v-if="layoutMode !== 'preview'" class="relative min-h-[300px]" :style="{ height: editorHeight + 'px' }">
              <div ref="cmHost" class="absolute inset-0 cm-editor-host"></div>
            </div>
            <div v-if="layoutMode !== 'edit'" ref="previewEl" @scroll.passive="onPreviewScroll" class="min-h-[300px] overflow-y-auto px-4 py-3 bg-gray-50 dark:bg-gray-700/50" :style="{ height: editorHeight + 'px' }">
              <MarkdownRenderer :content="form.contentMd" />
            </div>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2 text-xs text-gray-400">
        <span>快捷键：Ctrl+S 保存草稿 · Ctrl+B 加粗 · Ctrl+I 斜体 · Tab 缩进</span>
      </div>
    </div>

    <MediaLibraryDialog :open="mediaOpen" @close="mediaOpen = false" @pick="onMediaPick" />
  </div>
</template>

<script setup lang="ts">
import { EditorView, basicSetup } from 'codemirror'
import { markdown } from '@codemirror/lang-markdown'
import { languages } from '@codemirror/language-data'
import { EditorState } from '@codemirror/state'
import { keymap } from '@codemirror/view'

const props = defineProps<{ articleId?: string | number }>()

definePageMeta({ middleware: 'admin', layout: 'admin' })

const { get, post, put } = useApi()
const { toast, confirmDialog } = useFeedback()
const { upload, uploadWithProgress, compressImage } = useUpload()
const router = useRouter()

const categories = ref<any[]>([])
const tags = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const coverUploading = ref(false)
const uploading = ref(false)
const uploadProgress = ref(0)
const mediaOpen = ref(false)
const newTagName = ref('')

const layoutMode = ref<'split' | 'edit' | 'preview'>('split')
const isFullscreen = ref(false)
const editorHeight = ref(500)

const slugState = ref<'idle' | 'checking' | 'ok' | 'taken'>('idle')

const cmHost = ref<HTMLElement | null>(null)
const editorGrid = ref<HTMLElement | null>(null)
const editorWrap = ref<HTMLElement | null>(null)
const previewEl = ref<HTMLElement | null>(null)
let cmView: EditorView | null = null
let syncing = false
let dragging = false
let autosaveTimer: any = null
let dirty = false

const form = reactive({
  title: '',
  summary: '',
  cover: '',
  contentMd: '',
  categoryId: null as number | null,
  tagIds: [] as number[],
  isTop: 0,
  series: '',
  slug: '',
  publishAt: ''
})

const saveState = computed(() => {
  if (saving.value) return { text: '保存中…', cls: 'text-gray-400' }
  if (slugState.value === 'taken') return { text: 'URL 已被占用', cls: 'text-red-500' }
  if (dirty) return { text: '未保存', cls: 'text-amber-500' }
  if (autosaveTimer) return { text: '已自动保存', cls: 'text-green-500' }
  return { text: '', cls: '' }
})

const slugHint = computed(() => {
  if (slugState.value === 'checking') return { text: '检查中…', cls: 'text-gray-400' }
  if (slugState.value === 'ok') return { text: '✓ 该 URL 可用', cls: 'text-green-500' }
  if (slugState.value === 'taken') return { text: '✗ 该 URL 已被占用', cls: 'text-red-500' }
  return { text: '留空则根据标题自动生成', cls: 'text-gray-400' }
})

const layoutClass = computed(() => {
  if (layoutMode.value === 'split') return 'grid-cols-1 md:grid-cols-2'
  return 'grid-cols-1'
})

const wordCount = computed(() => {
  if (!form.contentMd) return 0
  return form.contentMd.replace(/\s/g, '').length
})

const readMinutes = computed(() => {
  const text = (form.contentMd || '').replace(/[#*`>\-\s]/g, '')
  return Math.max(1, Math.ceil(text.length / 500))
})

// ============ 分类 / 标签 ============

async function fetchCategories() {
  try {
    const res = await get<any>('/category/list')
    if (res.code === 200) categories.value = res.data || []
  } catch (e: any) {
    console.error('获取分类失败:', e.message)
  }
}

async function fetchTags() {
  const res = await get<any>('/tag/list')
  if (res.code === 200) tags.value = res.data || []
}

async function quickCreateCategory() {
  const name = prompt('新分类名：')
  if (!name?.trim()) return
  try {
    await post('/category', { name: name.trim() })
    toast('分类已创建', 'success')
    await fetchCategories()
  } catch (e: any) {
    toast('创建分类失败: ' + (e.message || '未知错误'), 'error')
  }
}

async function quickCreateTag() {
  const name = newTagName.value.trim()
  if (!name) {
    toast('请输入标签名', 'error')
    return
  }
  try {
    const res = await post('/tag', { name })
    if (res.code === 200 && res.data) {
      form.tagIds.push(res.data.id)
      newTagName.value = ''
      await fetchTags()
      toast('标签已创建并选中', 'success')
    }
  } catch (e: any) {
    toast('创建标签失败: ' + (e.message || '未知错误'), 'error')
  }
}

// ============ slug 预检 ============

async function checkSlug() {
  const slug = form.slug.trim()
  if (!slug) {
    slugState.value = 'idle'
    return
  }
  slugState.value = 'checking'
  try {
    const excludeId = props.articleId ? Number(props.articleId) : undefined
    const qs = excludeId ? `?slug=${encodeURIComponent(slug)}&excludeId=${excludeId}` : `?slug=${encodeURIComponent(slug)}`
    const res = await get<any>(`/article/slug-available${qs}`)
    slugState.value = res.data?.available ? 'ok' : 'taken'
  } catch {
    slugState.value = 'idle'
  }
}

// ============ CodeMirror 编辑器 ============

function initEditor() {
  if (!cmHost.value) return

  const saveKeymap = keymap.of([
    {
      key: 'Mod-s',
      run: () => { save(0); return true }
    },
    {
      key: 'Mod-b',
      run: () => { tool.bold(); return true }
    },
    {
      key: 'Mod-i',
      run: () => { tool.italic(); return true }
    }
  ])

  const updateListener = EditorView.updateListener.of((update) => {
    if (update.docChanged) {
      form.contentMd = update.state.doc.toString()
      markDirty()
    }
  })

  cmView = new EditorView({
    state: EditorState.create({
      doc: form.contentMd,
      extensions: [
        basicSetup,
        markdown({ codeLanguages: languages }),
        saveKeymap,
        updateListener,
        EditorView.lineWrapping
      ]
    }),
    parent: cmHost.value
  })
  cmView.scrollDOM.addEventListener('scroll', onEditorScroll, { passive: true })
}

function destroyEditor() {
  if (cmView) {
    cmView.scrollDOM.removeEventListener('scroll', onEditorScroll)
    cmView.destroy()
  }
  cmView = null
}

// ============ 滚动同步（编辑区 ↔ 预览区） ============

function onEditorScroll() {
  if (syncing || layoutMode.value !== 'split' || !cmView || !previewEl.value) return
  const scroller = cmView.scrollDOM
  const preview = previewEl.value
  const maxEditor = scroller.scrollHeight - scroller.clientHeight
  const maxPreview = preview.scrollHeight - preview.clientHeight
  if (maxEditor <= 0 || maxPreview <= 0) return
  syncing = true
  preview.scrollTop = (scroller.scrollTop / maxEditor) * maxPreview
  requestAnimationFrame(() => { syncing = false })
}

function onPreviewScroll() {
  if (syncing || layoutMode.value !== 'split' || !cmView || !previewEl.value) return
  const scroller = cmView.scrollDOM
  const preview = previewEl.value
  const maxEditor = scroller.scrollHeight - scroller.clientHeight
  const maxPreview = preview.scrollHeight - preview.clientHeight
  if (maxEditor <= 0 || maxPreview <= 0) return
  syncing = true
  scroller.scrollTop = (preview.scrollTop / maxPreview) * maxEditor
  requestAnimationFrame(() => { syncing = false })
}

function getSelection(): { from: number; to: number; text: string } {
  if (!cmView) return { from: 0, to: 0, text: '' }
  const range = cmView.state.selection.main
  return {
    from: range.from,
    to: range.to,
    text: cmView.state.sliceDoc(range.from, range.to)
  }
}

function replaceRange(from: number, to: number, text: string, selectFrom?: number, selectTo?: number) {
  if (!cmView) return
  cmView.dispatch({
    changes: { from, to, insert: text },
    selection: selectFrom !== undefined && selectTo !== undefined
      ? { anchor: selectFrom, head: selectTo }
      : undefined
  })
}

/** 包裹选中文字；光标保持在新包裹内容的末尾，而不是整段跳到最后 */
function wrapSelection(before: string, after = '', placeholder = '') {
  const { from, to, text } = getSelection()
  const content = text || placeholder
  replaceRange(from, to, before + content + after, from + before.length, from + before.length + content.length)
}

/** 在行首插入前缀（标题/引用/列表） */
function wrapLinePrefix(prefix: string) {
  if (!cmView) return
  const { from } = getSelection()
  const line = cmView.state.doc.lineAt(from)
  replaceRange(line.from, line.from, prefix)
}

/** 把选中的每一行都加上前缀 */
function prefixLines(prefix: string, ordered = false) {
  if (!cmView) return
  const { from, to } = getSelection()
  const startLine = cmView.state.doc.lineAt(from)
  const endLine = cmView.state.doc.lineAt(to)
  const parts: string[] = []
  let num = 1
  for (let i = startLine.number; i <= endLine.number; i++) {
    const line = cmView.state.doc.line(i)
    parts.push((ordered ? `${num}. ` : prefix) + line.text)
    num++
  }
  replaceRange(startLine.from, endLine.to, parts.join('\n'))
}

const tool = {
  bold: () => wrapSelection('**', '**', '加粗文字'),
  italic: () => wrapSelection('*', '*', '斜体文字'),
  strike: () => wrapSelection('~~', '~~', '删除线文字'),
  inlineCode: () => wrapSelection('`', '`', '代码'),
  heading: (level: number) => wrapLinePrefix('#'.repeat(level) + ' '),
  quote: () => prefixLines('> '),
  list: (ordered: boolean) => prefixLines('- ', ordered),
  taskList: () => prefixLines('- [ ] '),
  hr: () => { const { from } = getSelection(); replaceRange(from, from, '\n\n---\n\n') },
  codeBlock: () => { const { from } = getSelection(); replaceRange(from, from, '\n```\n\n```\n', from + 4, from + 4) },
  link: () => wrapSelection('[', '](https://)', '链接文字'),
  table: () => {
    const table = '\n| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n| 内容 | 内容 | 内容 |\n'
    const { from } = getSelection()
    replaceRange(from, from, table)
  }
}

// ============ 图片 ============

async function uploadCover(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  coverUploading.value = true
  try {
    const compressed = await compressImage(file)
    form.cover = await uploadWithProgress(compressed, p => { uploadProgress.value = p })
    toast('封面上传成功', 'success')
  } catch (e: any) {
    toast('封面上传失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    coverUploading.value = false
    input.value = ''
  }
}

async function insertImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  await insertImageFile(file)
  input.value = ''
}

async function insertImageFile(file: File) {
  uploading.value = true
  uploadProgress.value = 0
  const placeholder = `![上传中…]()`
  const { from } = getSelection()
  replaceRange(from, from, `\n${placeholder}\n`)
  try {
    const compressed = await compressImage(file)
    const url = await uploadWithProgress(compressed, p => { uploadProgress.value = p })
    // 用真实 URL 替换占位符
    if (cmView) {
      const doc = cmView.state.doc.toString()
      const idx = doc.indexOf(placeholder)
      if (idx >= 0) {
        replaceRange(idx, idx + placeholder.length, `![](${url})`)
      }
    }
  } catch (e: any) {
    toast('图片上传失败: ' + (e.message || '未知错误'), 'error')
    // 移除占位符
    if (cmView) {
      const doc = cmView.state.doc.toString()
      const idx = doc.indexOf(placeholder)
      if (idx >= 0) replaceRange(idx, idx + placeholder.length, '')
    }
  } finally {
    uploading.value = false
  }
}

function onMediaPick(url: string) {
  if (form.cover === '') {
    form.cover = url
    return
  }
  const { from } = getSelection()
  replaceRange(from, from, `\n![](${url})\n`)
}

// ============ 粘贴 / 拖拽 ============

async function onPaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      e.preventDefault()
      const file = item.getAsFile()
      if (file) await insertImageFile(file)
      break
    }
  }
}

function onDrop(e: DragEvent) {
  const files = e.dataTransfer?.files
  if (!files || files.length === 0) return
  e.preventDefault()
  for (const file of Array.from(files)) {
    if (file.type.startsWith('image/')) {
      insertImageFile(file)
      break
    }
  }
}

// ============ 保存 / 自动保存 ============

function buildPayload(status: number) {
  return {
    title: form.title,
    summary: form.summary,
    cover: form.cover,
    contentMd: form.contentMd,
    categoryId: form.categoryId,
    tagIds: form.tagIds,
    isTop: form.isTop,
    series: form.series,
    slug: form.slug,
    publishAt: form.publishAt || null,
    status
  }
}

function autoSummary() {
  if (form.summary.trim()) return
  const text = form.contentMd.replace(/[#*`>\[\]()!\-|\\]/g, '').replace(/\s+/g, ' ').trim()
  if (text) form.summary = text.slice(0, 120)
}

async function save(status: number, autosave = false) {
  if (saving.value) return
  if (!form.title.trim()) {
    toast('请输入标题', 'error')
    return
  }
  if (status === 1) {
    if (!form.contentMd.trim()) {
      toast('发布前请填写正文；如果还没写完，可以先「保存草稿」', 'error')
      return
    }
    autoSummary()
  }
  saving.value = true
  try {
    if (props.articleId) {
      await put(`/article/${props.articleId}?autosave=${autosave}`, buildPayload(status))
    } else {
      const res = await post('/article', buildPayload(status))
      // 新建成功后拿到 id，之后的自动保存都走更新
      if (res.code === 200 && res.data?.id) {
        ;(props as any).articleId = res.data.id
        await router.replace(`/admin/articles/${res.data.id}`)
      }
    }
    dirty = false
    if (!autosave) {
      toast(status === 1 ? '发布成功' : '已保存', 'success')
      if (status === 1) {
        // 发布成功后短暂停留再回列表
        setTimeout(() => router.push('/admin/articles'), 800)
      }
    }
  } catch (e: any) {
    toast(e.message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

function markDirty() {
  dirty = true
  scheduleAutosave()
}

function scheduleAutosave() {
  if (!props.articleId) return // 新建尚未拿到 id，不自动保存
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = setTimeout(async () => {
    autosaveTimer = null
    if (!dirty) return
    try {
      await save(0, true)
    } catch {}
  }, 5000)
}

// ============ 布局 ============

function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen?.()
  } else {
    editorWrap.value?.requestFullscreen?.()
  }
}

function onFullscreenChange() {
  const active = !!document.fullscreenElement
  isFullscreen.value = active
  editorHeight.value = active ? Math.max(320, window.innerHeight - 96) : 500
}

function openPreview() {
  // 草稿预览：新窗口打开（admin 能绕过 status 限制读取草稿）
  if (props.articleId && form.slug) {
    window.open(`/post/${form.slug}`, '_blank')
  } else if (props.articleId) {
    window.open(`/article/${props.articleId}`, '_blank')
  } else {
    toast('请先保存草稿，再预览', 'info')
  }
}

function goRevisions() {
  if (props.articleId) router.push(`/admin/articles/revisions/${props.articleId}`)
}

// ============ 未保存拦截 ============

function onBeforeUnload(e: BeforeUnloadEvent) {
  if (dirty) {
    e.preventDefault()
    e.returnValue = ''
  }
}

// ============ 数据加载 ============

async function fetchArticle() {
  if (!props.articleId) return
  try {
    const res = await get<any>(`/article/${props.articleId}`)
    if (res.code === 200 && res.data) {
      const a = res.data.article || res.data
      form.title = a.title || ''
      form.summary = a.summary || ''
      form.cover = a.cover || ''
      form.contentMd = a.contentMd || ''
      form.categoryId = a.categoryId ?? null
      form.tagIds = (res.data.tags || []).map((t: any) => t.id)
      form.isTop = a.isTop ?? 0
      form.series = a.series || ''
      form.slug = a.slug || ''
      form.publishAt = a.publishAt ? a.publishAt.replace(' ', 'T').slice(0, 16) : ''
    }
  } catch (e: any) {
    toast('加载文章失败: ' + (e.message || '未知错误'), 'error')
  }
}

// ============ 生命周期 ============

onMounted(async () => {
  loading.value = true
  await Promise.all([fetchArticle(), fetchCategories(), fetchTags()])
  loading.value = false
  nextTick(() => initEditor())
  window.addEventListener('beforeunload', onBeforeUnload)
  document.addEventListener('fullscreenchange', onFullscreenChange)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  if (autosaveTimer) clearTimeout(autosaveTimer)
  destroyEditor()
})
</script>

<style scoped>
.cm-editor-host :deep(.cm-editor) {
  height: 100%;
}
.cm-editor-host :deep(.cm-scroller) {
  overflow-y: auto;
  overflow-x: hidden;
}
</style>
