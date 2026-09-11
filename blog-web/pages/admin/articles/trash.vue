<template>
  <div>
    <div class="flex justify-between items-center mb-6 gap-3 flex-wrap">
      <h1 class="text-2xl font-bold">回收站</h1>
      <NuxtLink to="/admin/articles" class="text-sm text-gray-500 hover:text-primary-500">← 返回文章管理</NuxtLink>
    </div>

    <div v-if="selectedIds.length > 0" class="bg-primary-50 dark:bg-primary-900/20 rounded-lg px-4 py-3 mb-4 flex items-center gap-4 text-sm flex-wrap">
      <span class="text-primary-600 dark:text-primary-400 font-medium">已选 {{ selectedIds.length }} 篇</span>
      <button @click="handleBatchRestore" class="px-3 py-1.5 bg-green-500 text-white rounded-lg hover:bg-green-600 transition text-xs">批量恢复</button>
      <button @click="handleBatchForceDelete" class="px-3 py-1.5 bg-red-500 text-white rounded-lg hover:bg-red-600 transition text-xs">彻底删除</button>
      <button @click="selectedIds = []" class="text-gray-400 hover:text-gray-600 transition text-xs">取消选择</button>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-4 mb-4 flex gap-3 flex-wrap">
      <input v-model="keyword" placeholder="搜索标题" class="px-3 py-1.5 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" @keyup.enter="search" />
      <button @click="search" class="px-3 py-1.5 border rounded text-sm hover:border-primary-500 hover:text-primary-500">搜索</button>
    </div>

    <div v-if="loading" class="text-center py-10 text-gray-400">加载中...</div>
    <div v-else-if="articles.length === 0" class="text-center py-16 text-gray-400">
      <p class="mb-2 text-lg">回收站是空的</p>
      <p class="text-sm">删除的文章会先到这里，30 天内可恢复</p>
    </div>
    <div v-else class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[640px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <th class="px-4 py-3 w-10">
                <input type="checkbox" :checked="isAllSelected" @change="toggleAll" class="rounded" aria-label="全选" />
              </th>
              <th class="px-4 py-3 text-left">标题</th>
              <th class="px-4 py-3 text-left w-20">字数</th>
              <th class="px-4 py-3 text-left w-40">删除时间</th>
              <th class="px-4 py-3 text-left w-44">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in articles" :key="a.id" class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <td class="px-4 py-3">
                <input type="checkbox" :value="a.id" v-model="selectedIds" class="rounded" :aria-label="`选择文章 ${a.title}`" />
              </td>
              <td class="px-4 py-3">{{ a.title }}</td>
              <td class="px-4 py-3 text-gray-400">{{ a.wordCount || 0 }}</td>
              <td class="px-4 py-3 text-gray-400 whitespace-nowrap">{{ a.updateTime?.replace('T', ' ').substring(0, 16) }}</td>
              <td class="px-4 py-3 whitespace-nowrap">
                <button @click="handleRestore(a.id)" class="text-green-500 hover:underline text-xs mr-3">恢复</button>
                <button @click="handleForceDelete(a.id)" class="text-red-500 hover:underline text-xs">彻底删除</button>
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

const { get, post, del } = useApi()
const { toast, confirmDialog } = useFeedback()

const pageNum = ref(1)
const keyword = ref('')
const articles = ref<any[]>([])
const totalPages = ref(0)
const loading = ref(false)
const selectedIds = ref<number[]>([])

const isAllSelected = computed(() => articles.value.length > 0 && selectedIds.value.length === articles.value.length)

function toggleAll() {
  if (isAllSelected.value) selectedIds.value = []
  else selectedIds.value = articles.value.map(a => a.id)
}

async function fetchList() {
  loading.value = true
  try {
    const params: any = { pageNum: pageNum.value, pageSize: 10 }
    if (keyword.value) params.keyword = keyword.value
    const qs = new URLSearchParams(params).toString()
    const res = await get<any>(`/article/trash/list?${qs}`)
    if (res.code === 200 && res.data) {
      articles.value = res.data.records || []
      totalPages.value = res.data.pages || 0
    }
  } finally {
    loading.value = false
  }
}

function search() { pageNum.value = 1; fetchList() }

async function handleRestore(id: number) {
  try {
    await post(`/article/${id}/restore`)
    toast('已恢复', 'success')
    fetchList()
  } catch (e: any) {
    toast('恢复失败: ' + (e.message || '未知错误'), 'error')
  }
}

async function handleForceDelete(id: number) {
  if (!(await confirmDialog('彻底删除后无法恢复，确定？'))) return
  try {
    await del(`/article/${id}/force`)
    toast('已彻底删除', 'success')
    fetchList()
  } catch (e: any) {
    toast('删除失败: ' + (e.message || '未知错误'), 'error')
  }
}

async function handleBatchRestore() {
  try {
    await post('/article/batch-restore', selectedIds.value)
    selectedIds.value = []
    toast('批量恢复成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('恢复失败: ' + (e.message || '未知错误'), 'error')
  }
}

async function handleBatchForceDelete() {
  if (!(await confirmDialog(`确定彻底删除选中的 ${selectedIds.value.length} 篇？不可恢复。`))) return
  try {
    await post('/article/batch-force-delete', selectedIds.value)
    selectedIds.value = []
    toast('批量删除成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('删除失败: ' + (e.message || '未知错误'), 'error')
  }
}

watch(pageNum, () => fetchList())
fetchList()
</script>
