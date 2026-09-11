<template>
  <div>
    <div class="flex justify-between items-center mb-6 gap-3 flex-wrap">
      <h1 class="text-2xl font-bold gradient-text">文章管理</h1>
      <div class="flex gap-2 flex-wrap">
        <label class="px-4 py-2 border-2 border-dashed rounded-lg text-sm cursor-pointer hover:border-primary-500 hover:text-primary-500 transition">
          <input type="file" accept=".md" class="hidden" @change="handleImport" ref="fileInput" />
          导入 .md
        </label>
        <label class="px-4 py-2 border-2 border-dashed rounded-lg text-sm cursor-pointer hover:border-green-500 hover:text-green-500 transition">
          <input type="file" accept=".docx,.doc" class="hidden" @change="handleWordImport" ref="wordInput" />
          导入 Word
        </label>
        <NuxtLink to="/admin/articles/create" class="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 text-sm">写文章</NuxtLink>
      </div>
    </div>

    <!-- Batch actions -->
    <div v-if="selectedIds.length > 0" class="bg-primary-50 dark:bg-primary-900/20 rounded-lg px-4 py-3 mb-4 flex items-center gap-4 text-sm flex-wrap">
      <span class="text-primary-600 dark:text-primary-400 font-medium">已选 {{ selectedIds.length }} 篇</span>
      <button @click="handleBatchExport" class="px-3 py-1.5 bg-green-500 text-white rounded-lg hover:bg-green-600 transition text-xs">批量导出</button>
      <button @click="handleBatchDelete" class="px-3 py-1.5 bg-red-500 text-white rounded-lg hover:bg-red-600 transition text-xs">批量删除</button>
      <button @click="selectedIds = []" class="text-gray-400 hover:text-gray-600 transition text-xs">取消选择</button>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 mb-4 flex gap-3 flex-wrap">
      <input v-model="keyword" placeholder="搜索标题" class="px-3 py-1.5 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" @keyup.enter="search" />
      <select v-model="statusFilter" @change="search" class="px-3 py-1.5 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm">
        <option :value="undefined">全部状态</option>
        <option :value="0">草稿</option>
        <option :value="1">已发布</option>
      </select>
    </div>

    <div v-if="loading" class="text-center py-10 text-gray-400">加载中...</div>
    <div v-else class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <!-- 横向滚动包裹层：外层 overflow-hidden 只负责圆角，窄屏下表格可左右滑动而不被裁掉 -->
      <div class="overflow-x-auto">
        <table class="w-full min-w-[640px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <th class="px-4 py-3 w-10">
                <input type="checkbox" :checked="isAllSelected" @change="toggleAll" class="rounded" aria-label="全选" />
              </th>
              <th class="px-4 py-3 text-left">标题</th>
              <th class="px-4 py-3 text-left w-24">状态</th>
              <th class="px-4 py-3 text-left w-20">阅读</th>
              <th class="px-4 py-3 text-left w-36">时间</th>
              <th class="px-4 py-3 text-left w-36">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in articles" :key="a.id" class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <td class="px-4 py-3">
                <input type="checkbox" :value="a.id" v-model="selectedIds" class="rounded" :aria-label="`选择文章 ${a.title}`" />
              </td>
              <td class="px-4 py-3">
                <span v-if="a.isTop" class="text-red-500 text-xs mr-1">[置顶]</span>
                {{ a.title }}
              </td>
              <td class="px-4 py-3">
                <span :class="a.status === 1 ? 'text-green-500' : 'text-yellow-500'" class="text-xs">
                  {{ a.status === 1 ? '已发布' : '草稿' }}
                </span>
              </td>
              <td class="px-4 py-3 text-gray-400">{{ a.viewCount }}</td>
              <td class="px-4 py-3 text-gray-400 whitespace-nowrap">{{ a.createTime?.substring(0, 10) }}</td>
              <td class="px-4 py-3 whitespace-nowrap">
                <NuxtLink :to="`/admin/articles/${a.id}`" class="text-primary-500 hover:underline text-xs mr-3">编辑</NuxtLink>
                <button @click="handleExportOne(a)" class="text-green-500 hover:underline text-xs mr-3">导出</button>
                <button @click="handleDelete(a.id)" class="text-red-500 hover:underline text-xs">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Pagination v-if="totalPages > 1" :current="pageNum" :total="totalPages" @change="pageNum = $event" />
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const { get, del, post } = useApi()
const { toast, confirmDialog } = useFeedback()
const config = useRuntimeConfig()
const apiBase = config.public.apiBase
const token = useCookie('token')
const fileInput = ref<HTMLInputElement | null>(null)
const pageNum = ref(1)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)
const articles = ref<any[]>([])
const totalPages = ref(0)
const loading = ref(true)

async function fetchList() {
  loading.value = true
  try {
    const params: any = { pageNum: pageNum.value, pageSize: 10 }
    if (keyword.value) params.keyword = keyword.value
    if (statusFilter.value !== undefined) params.status = statusFilter.value
    const qs = new URLSearchParams(params).toString()
    const res = await get<any>(`/article/admin/list?${qs}`)
    if (res.code === 200 && res.data) {
      articles.value = res.data.records || []
      totalPages.value = res.data.pages || 0
    }
  } finally {
    loading.value = false
  }
}

function search() { pageNum.value = 1; fetchList() }

async function handleDelete(id: number) {
  if (!(await confirmDialog('确定删除这篇文章？删除后无法恢复。'))) return
  try {
    await del(`/article/${id}`)
    toast('删除成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('删除失败: ' + (e.message || '未知错误'), 'error')
  }
}

async function handleImport(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  try {
    await post('/article/import', formData)
    toast('导入成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('导入失败: ' + (e.message || '未知错误'), 'error')
  }
  input.value = ''
}

async function handleWordImport(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('file', file)
  try {
    await post('/article/import-word', formData)
    toast('Word 导入成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('Word 导入失败: ' + (e.message || '未知错误'), 'error')
  }
  input.value = ''
}

const selectedIds = ref<number[]>([])
const isAllSelected = computed(() => articles.value.length > 0 && selectedIds.value.length === articles.value.length)

function toggleAll() {
  if (isAllSelected.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = articles.value.map(a => a.id)
  }
}

async function handleBatchDelete() {
  if (!(await confirmDialog(`确定删除选中的 ${selectedIds.value.length} 篇文章？删除后无法恢复。`))) return
  try {
    await post('/article/batch-delete', selectedIds.value)
    selectedIds.value = []
    toast('批量删除成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('批量删除失败: ' + (e.message || '未知错误'), 'error')
  }
}

async function downloadMarkdown(ids: number[], filename: string) {
  const resp = await $fetch(`${apiBase}/article/batch-export`, {
    method: 'POST',
    body: ids,
    headers: token.value ? { Authorization: `Bearer ${token.value}` } : {},
    responseType: 'arrayBuffer'
  })
  const blob = new Blob([resp as any], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename; a.click()
  URL.revokeObjectURL(url)
}

async function handleBatchExport() {
  try {
    await downloadMarkdown(selectedIds.value, 'articles-export.md')
    toast('导出完成', 'success')
  } catch (e: any) {
    toast('批量导出失败: ' + (e.message || '未知错误'), 'error')
  }
}

async function handleExportOne(a: any) {
  try {
    const name = String(a.title || 'article').replace(/[\\/:*?"<>|]/g, '_')
    await downloadMarkdown([a.id], `${name}.md`)
    toast('导出完成', 'success')
  } catch (e: any) {
    toast('导出失败: ' + (e.message || '未知错误'), 'error')
  }
}

watch(pageNum, () => fetchList())
fetchList()
</script>
