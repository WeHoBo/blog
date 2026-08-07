<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold">写文章</h1>
      <div class="flex gap-2">
        <button @click="save(0)" class="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700">保存草稿</button>
        <button @click="save(1)" class="px-4 py-2 bg-primary-500 text-white rounded-lg text-sm hover:bg-primary-600">发布</button>
      </div>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 space-y-4">
      <div class="flex gap-4">
        <input v-model="form.title" placeholder="文章标题" class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-lg" />
        <select v-model="form.categoryId" class="px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600">
          <option :value="null">选择分类</option>
          <option v-if="categories.length === 0" disabled>请先前往【分类管理】创建分类</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <input v-model="form.slug" placeholder="自定义URL(可选)" class="w-40 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
        <input v-model="form.series" placeholder="系列名(可选)" class="w-40 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
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

      <input v-model="form.summary" placeholder="文章摘要（可选）" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
      <div>
        <label class="text-sm text-gray-500 mb-1 block">封面图</label>
        <div class="flex gap-2">
          <input v-model="form.cover" placeholder="封面图URL" class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
          <label class="px-3 py-2 border-2 border-dashed rounded-lg text-xs cursor-pointer hover:border-primary-500 hover:text-primary-500 whitespace-nowrap flex items-center">
            <input type="file" accept="image/*" class="hidden" @change="uploadCover" />
            {{ coverUploading ? '上传中...' : '上传' }}
          </label>
        </div>
        <img v-if="form.cover" :src="form.cover" class="mt-2 h-32 rounded-lg object-cover" />
      </div>

        <div>
          <label class="text-sm text-gray-500 mb-1 block">内容（Markdown）</label>
          <!-- Toolbar -->
          <div class="flex flex-wrap gap-1 mb-2">
            <button type="button" @click="wrapMd('**', '**')" class="px-2 py-1 border rounded text-xs font-bold hover:border-primary-500 hover:text-primary-500 transition">B</button>
            <button type="button" @click="wrapMd('*', '*')" class="px-2 py-1 border rounded text-xs italic hover:border-primary-500 hover:text-primary-500 transition">I</button>
            <button type="button" @click="wrapLine('### ')" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500 transition">H</button>
            <button type="button" @click="wrapLine('> ')" class="px-2 py-1 border rounded text-xs hover:border-primary-500 hover:text-primary-500 transition">❝</button>
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
          <div class="grid grid-cols-2 gap-4 border rounded-lg dark:border-gray-600 overflow-hidden" style="height: 500px">
            <textarea v-model="form.contentMd" class="w-full h-full px-4 py-3 resize-none focus:outline-none dark:bg-gray-700 dark:text-gray-100 font-mono text-sm border-r dark:border-gray-600 editor-sync" placeholder="支持 Markdown 语法..." ref="editorLeft" @scroll="syncScroll('left')"></textarea>
            <div class="h-full overflow-y-auto px-4 py-3 bg-gray-50 dark:bg-gray-750 editor-sync" ref="editorRight" @scroll="syncScroll('right')">
              <MarkdownRenderer :content="form.contentMd" />
            </div>
          </div>
        </div>

      <label class="flex items-center gap-2 text-sm">
        <input type="checkbox" v-model="form.isTop" :true-value="1" :false-value="0" />
        置顶
      </label>

      <p v-if="msg" class="text-sm" :class="msgType === 'ok' ? 'text-green-500' : 'text-red-500'">{{ msg }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const { get, post } = useApi()
const { upload } = useUpload()
const router = useRouter()

const categories = ref<any[]>([])
const tags = ref<any[]>([])
const msg = ref('')
const msgType = ref('')
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
  if (!form.title.trim()) { msg.value = '请输入标题'; msgType.value = 'err'; return }
  try {
    await post('/article', { ...form, status })
    msg.value = '保存成功'
    msgType.value = 'ok'
    setTimeout(() => router.push('/admin/articles'), 800)
  } catch (e: any) {
    msg.value = e.message || '保存失败'
    msgType.value = 'err'
  }
}

async function uploadCover(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  coverUploading.value = true
  try {
    form.cover = await upload(file)
  } catch (e: any) {
    alert('封面上传失败: ' + (e.message || ''))
  } finally {
    coverUploading.value = false
  }
}

async function insertImage(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    const url = await upload(file)
    const textarea = editorLeft.value
    if (textarea) {
      const pos = textarea.selectionStart
      form.contentMd = form.contentMd.slice(0, pos) + `\n![](${url})\n` + form.contentMd.slice(pos)
    }
  } catch (e: any) { alert('上传失败') }
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
          } catch {}
          break
        }
      }
    })
  }
})

fetchCategories()
fetchTags()
</script>
