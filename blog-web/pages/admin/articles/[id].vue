<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold">编辑文章</h1>
      <div class="flex gap-2">
        <button @click="save(0)" class="px-4 py-2 border rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700">保存草稿</button>
        <button @click="save(1)" class="px-4 py-2 bg-primary-500 text-white rounded-lg text-sm hover:bg-primary-600">发布</button>
      </div>
    </div>

    <div v-if="loading" class="text-center py-10 text-gray-400">加载中...</div>
    <div v-else class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-6 space-y-4">
      <div class="flex gap-4">
        <input v-model="form.title" class="flex-1 px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-lg" />
        <select v-model="form.categoryId" class="px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600">
          <option :value="null">选择分类</option>
          <option v-if="categories.length === 0" disabled>请先前往【分类管理】创建分类</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </div>
      <input v-model="form.summary" placeholder="摘要" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
      <input v-model="form.cover" placeholder="封面图URL" class="w-full px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm" />
      <div>
        <label class="text-sm text-gray-500 mb-2 block">内容（Markdown）</label>
        <div class="grid grid-cols-2 gap-4 border rounded-lg dark:border-gray-600 overflow-hidden" style="height: 500px">
          <textarea v-model="form.contentMd" class="w-full h-full px-4 py-3 resize-none focus:outline-none dark:bg-gray-700 dark:text-gray-100 font-mono text-sm border-r dark:border-gray-600" placeholder="支持 Markdown 语法..."></textarea>
          <div class="h-full overflow-y-auto px-4 py-3 bg-gray-50 dark:bg-gray-750">
            <MarkdownRenderer :content="form.contentMd" />
          </div>
        </div>
      </div>
      <label class="flex items-center gap-2 text-sm">
        <input type="checkbox" v-model="form.isTop" :true-value="1" :false-value="0" /> 置顶
      </label>
      <p v-if="msg" class="text-sm" :class="msgType === 'ok' ? 'text-green-500' : 'text-red-500'">{{ msg }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const route = useRoute()
const { get, put } = useApi()

const categories = ref<any[]>([])
const msg = ref('')
const msgType = ref('')
const loading = ref(true)

const form = reactive({
  title: '',
  summary: '',
  cover: '',
  contentMd: '',
  contentHtml: '',
  categoryId: null as number | null,
  isTop: 0
})

async function fetchArticle() {
  const res = await get<any>(`/article/${route.params.id}`)
  if (res.code === 200 && res.data) {
    const a = res.data
    form.title = a.title
    form.summary = a.summary || ''
    form.cover = a.cover || ''
    form.contentMd = a.contentMd || ''
    form.categoryId = a.categoryId
    form.isTop = a.isTop
  }
  loading.value = false
}

async function fetchCategories() {
  try {
    const res = await get<any>('/category/list')
    if (res.code === 200) categories.value = res.data || []
  } catch (e: any) {
    console.error('获取分类失败:', e.message)
  }
}

async function save(status: number) {
  if (!form.title.trim()) { msg.value = '请输入标题'; msgType.value = 'err'; return }
  try {
    await put(`/article/${route.params.id}`, { ...form, status })
    msg.value = '保存成功'; msgType.value = 'ok'
  } catch (e: any) {
    msg.value = e.message || '保存失败'; msgType.value = 'err'
  }
}

fetchArticle()
fetchCategories()
</script>
