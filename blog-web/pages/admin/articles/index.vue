<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold">文章管理</h1>
      <div class="flex gap-2">
        <label class="px-4 py-2 border-2 border-dashed rounded-lg text-sm cursor-pointer hover:border-blue-500 hover:text-blue-500 transition">
          <input type="file" accept=".md" class="hidden" @change="handleImport" ref="fileInput" />
          导入 .md
        </label>
        <NuxtLink to="/admin/articles/create" class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm">写文章</NuxtLink>
      </div>
    </div>

    <!-- Batch actions -->
    <div v-if="selectedIds.length > 0" class="bg-blue-50 dark:bg-blue-900/20 rounded-lg px-4 py-3 mb-4 flex items-center gap-4 text-sm">
      <span class="text-blue-600 dark:text-blue-400 font-medium">已选 {{ selectedIds.length }} 篇</span>
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
      <table class="w-full text-sm">
        <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
          <tr>
            <th class="px-4 py-3 w-10">
              <input type="checkbox" :checked="isAllSelected" @change="toggleAll" class="rounded" />
            </th>
            <th class="px-4 py-3 text-left">标题</th>
            <th class="px-4 py-3 text-left w-24">状态</th>
            <th class="px-4 py-3 text-left w-20">阅读</th>
            <th class="px-4 py-3 text-left w-36">时间</th>
            <th class="px-4 py-3 text-left w-36">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in articles" :key="a.id" class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-750">
            <td class="px-4 py-3">
              <input type="checkbox" :value="a.id" v-model="selectedIds" class="rounded" />
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
            <td class="px-4 py-3 text-gray-400">{{ a.createTime?.substring(0, 10) }}</td>
            <td class="px-4 py-3">
              <NuxtLink :to="`/admin/articles/${a.id}`" class="text-blue-500 hover:underline text-xs mr-3">编辑</NuxtLink>
              <a :href="`${apiBase}/article/${a.id}/export`" class="text-green-500 hover:underline text-xs mr-3">导出</a>
              <button @click="handleDelete(a.id)" class="text-red-500 hover:underline text-xs">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <Pagination v-if="totalPages > 1" :current="pageNum" :total="totalPages" @change="pageNum = $event" />
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const { get, del, post } = useApi()
const config = useRuntimeConfig()
const apiBase = config.public.apiBase
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
  if (!confirm('确定删除？')) return
  try {
    await del(`/article/${id}`)
    fetchList()
  } catch (e: any) {
    alert('删除失败: ' + (e.message || '未知错误'))
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
    fetchList()
  } catch (e: any) {
    alert('导入失败: ' + (e.message || '未知错误'))
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
  if (!confirm(`确定删除选中的 ${selectedIds.value.length} 篇文章？`)) return
  try {
    await post('/article/batch-delete', selectedIds.value)
    selectedIds.value = []
    fetchList()
  } catch (e: any) {
    alert('批量删除失败: ' + (e.message || '未知错误'))
  }
}

async function handleBatchExport() {
  try {
    const token = authStore?.token || ''
    const resp = await $fetch(`${apiBase}/article/batch-export`, {
      method: 'POST',
      body: selectedIds.value,
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    const blob = new Blob([resp as any], { type: 'text/markdown' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = 'articles-export.md'; a.click()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    alert('批量导出失败: ' + (e.message || '未知错误'))
  }
}

watch(pageNum, () => fetchList())
fetchList()
</script>
