<template>
  <div>
    <div class="flex justify-between items-center mb-6 gap-3 flex-wrap">
      <h1 class="text-2xl font-bold">写文章</h1>
      <div class="flex gap-2">
        <button @click="save(0)" :disabled="saving" class="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:border-gray-600 disabled:opacity-50 transition">
          {{ saving ? '保存中…' : '保存草稿' }}
        </button>
        <button @click="save(1)" :disabled="saving" class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 disabled:opacity-50 transition">
          {{ saving ? '发布中…' : '发布' }}
        </button>
      </div>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 sm:p-6 space-y-4">
      <div class="flex flex-col lg:flex-row gap-4">
        <input v-model="form.title" placeholder="文章标题" aria-label="文章标题" class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-lg" />
        <select v-model="form.categoryId" aria-label="文章分类" class="px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600">
          <option :value="null">选择分类</option>
          <option v-if="categories.length === 0" disabled>请先前往【分类管理】创建分类</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <input v-model="form.slug" placeholder="自定义URL(可选)" aria-label="自定义 URL" class="lg:w-40 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
        <input v-model="form.series" placeholder="系列名(可选)" aria-label="系列名" class="lg:w-40 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
      </div>

      <div>
        <label class="text-sm text-gray-500 mb-1 block">标签</label>
        <div class="flex flex-wrap gap-2">
          <label v-for="t in tags" :key="t.id" class="flex items-center gap-1 px-3 py-1.5 rounded-full text-xs border cursor-pointer transition" :class="form.tagIds.includes(t.id) ? 'bg-primary-50 border-primary-300 text-primary-600 dark:bg-primary-900/30 dark:border-primary-600 dark:text-primary-400' : 'border-gray-200 dark:border-gray-600 text-gray-500 hover:border-primary-300'">
            <input type="checkbox" :value="t.id" v-model="form.tagIds" class="hidden" />
            {{ t.name }}
          </label>
        </div>
      </div>

      <input v-model="form.summary" placeholder="文章摘要（可选）" aria-label="文章摘要" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
      <div>
        <label class="text-sm text-gray-500 mb-1 block" for="create-cover">封面图</label>
        <div class="flex gap-2">
          <input id="create-cover" v-model="form.cover" placeholder="封面图URL" class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <label class="px-3 py-2 border-2 border-dashed rounded-lg text-xs cursor-pointer hover:border-primary-500 hover:text-primary-500 whitespace-nowrap flex items-center">
            <input type="file" accept="image/*" class="hidden" @change="uploadCover" />
            {{ coverUploading ? '上传中...' : '上传' }}
          </label>
        </div>
        <img v-if="form.cover" :src="form.cover" alt="封面预览" class="mt-2 h-32 rounded-lg object-cover" />
      </div>

      <div>
        <label class="text-sm text-gray-500 mb-1 block">内容（Markdown）</label>
        <!-- Toolbar -->
        <div class="flex flex-wrap gap-1 mb-2">
          <button type="button" @click="wrapMd('**', '**')" class="px-2 py-1 border rounded text-xs font-bold hover:border-primary-500 hover:text-primary-500 transition" aria-label="加粗">B</button>
          <button type="button" @click="wrapMd('*', '*')" class="px-2 py-1 border rounded text-xs italic hover:border-primary-500 hover:text-primary-500 transition" aria-label="斜体">I</button>
          <button type="button" @click="wrapLine('### ')" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500 transition" aria-label="标题">H</button>
          <button type="button" @click="wrapLine('> ')" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500 transition" aria-label="引用">❝</button>
          <span class="text-gray-300 dark:text-gray-600 mx-1">|</span>
          <label class="px-3 py-1 border border-dashed rounded text-xs cursor-pointer hover:border-primary-500 hover:text-primary-500 transition">
            <input type="file" accept="image/*" class="hidden" @change="insertImage" />
            📷 插图
          </label>
          <button type="button" @click="insertCodeBlock" class="px-3 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500 transition">
            &lt;/&gt; 代码块
          </button>
          <button type="button" @click="insertLink" class="px-3 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500 transition">
            🔗 链接
          </button>
        </div>
        <!-- 窄屏上下堆叠，宽屏左右分栏；高度改为自适应 + 上限 -->
        <div class="grid grid-cols-1 md:grid-cols-2 gap-0 md:gap-4 border rounded-lg dark:border-gray-600 overflow-hidden h-[380px] md:h-[500px]">
          <textarea v-model="form.contentMd" class="w-full h-full px-4 py-3 resize-none focus:outline-none dark:bg-gray-700 dark:text-gray-100 font-mono text-sm border-b md:border-b-0 md:border-r dark:border-gray-600 editor-sync" placeholder="支持 Markdown 语法..." aria-label="Markdown 内容" ref="editorLeft" @scroll="syncScroll('left')"></textarea>
          <div class="h-full overflow-y-auto px-4 py-3 bg-gray-50 dark:bg-gray-700/50 editor-sync" ref="editorRight" @scroll="syncScroll('right')">
            <MarkdownRenderer :content="form.contentMd" />
          </div>
        </div>
      </div>

      <label class="flex items-center gap-2 text-sm">
        <input type="checkbox" v-model="form.isTop" :true-value="1" :false-value="0" />
        置顶
      </label>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const { get, post } = useApi()
const { toast } = useFeedback()
const { upload } = useUpload()
const router = useRouter()

const categories = ref<any[]>([])
const tags = ref<any[]>([])
const saving = ref(false)
const coverUploading = ref(false)
const editorLeft = ref<HTMLElement | null>(null)
const editorRight = ref<HTMLElement | null>(null)
let syncing = false

function syncScroll(source: string) {
  if (syncing) return
  syncing = true
  const src = source === 'left' ? editorLeft.value : editorRight.value
  const dst = source === 'left' ? editorRight.value : editorLeft.value
  if (src && dst) {
    const ratio = src.scrollTop / (src.scrollHeight - src.clientHeight)
    dst.scrollTop = ratio * (dst.scrollHeight - dst.clientHeight)
  }
  requestAnimationFrame(() => syncing = false)
}

const form = reactive({
  title: '',
  summary: '',
  cover: '',
  contentMd: '',
  contentHtml: '',
  categoryId: null as number | null,
  tagIds: [] as number[],
  isTop: 0,
  series: '',
  slug: ''
})

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

async function save(status: number) {
  if (saving.value) return
  if (!form.title.trim()) {
    toast('请输入标题', 'error')
    return
  }
  saving.value = true
  try {
    await post('/article', { ...form, status })
    toast(status === 1 ? '发布成功' : '草稿已保存', 'success')
    setTimeout(() => router.push('/admin/articles'), 800)
  } catch (e: any) {
    toast(e.message || '保存失败', 'error')
  } finally {
    saving.value = false
  }
}

async function uploadCover(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  coverUploading.value = true
  try {
    form.cover = await upload(file)
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
  try {
    const url = await upload(file)
    const textarea = editorLeft.value
    if (textarea) {
      const pos = textarea.selectionStart
      form.contentMd = form.contentMd.slice(0, pos) + `\n![](${url})\n` + form.contentMd.slice(pos)
    }
  } catch (e: any) {
    toast('图片上传失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    input.value = ''
  }
}

function insertCodeBlock() {
  const textarea = editorLeft.value
  if (textarea) {
    const pos = textarea.selectionStart
    form.contentMd = form.contentMd.slice(0, pos) + '\n```\n\n```\n' + form.contentMd.slice(pos)
  }
}

function insertLink() {
  const textarea = editorLeft.value
  if (textarea) {
    const pos = textarea.selectionStart
    form.contentMd = form.contentMd.slice(0, pos) + '[链接文字](https://)' + form.contentMd.slice(pos)
  }
}

function wrapMd(before: string, after: string) {
  const textarea = editorLeft.value
  if (!textarea) return
  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selected = form.contentMd.slice(start, end)
  form.contentMd = form.contentMd.slice(0, start) + before + selected + after + form.contentMd.slice(end)
}

function wrapLine(prefix: string) {
  const textarea = editorLeft.value
  if (!textarea) return
  const pos = textarea.selectionStart
  const lineStart = form.contentMd.lastIndexOf('\n', pos - 1) + 1
  form.contentMd = form.contentMd.slice(0, lineStart) + prefix + form.contentMd.slice(lineStart)
}

onMounted(() => {
  const textarea = editorLeft.value
  if (textarea) {
    textarea.addEventListener('paste', async (e: ClipboardEvent) => {
      const items = e.clipboardData?.items
      if (!items) return
      for (const item of items) {
        if (item.type.startsWith('image/')) {
          e.preventDefault()
          const file = item.getAsFile()
          if (!file) continue
          try {
            const url = await upload(file)
            const el = e.target as HTMLTextAreaElement
            const pos = el.selectionStart
            form.contentMd = form.contentMd.slice(0, pos) + `\n![](${url})\n` + form.contentMd.slice(pos)
          } catch (err: any) {
            toast('粘贴图片上传失败: ' + (err?.message || '未知错误'), 'error')
          }
          break
        }
      }
    })
  }
})

fetchCategories()
fetchTags()
</script>
