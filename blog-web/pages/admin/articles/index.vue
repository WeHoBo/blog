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
        <option :value="2">待审核</option>
        <option :value="1">已发布</option>
        <option :value="0">草稿</option>
        <option :value="3">已驳回</option>
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
                <span v-if="a.isFeatured" class="text-primary-600 dark:text-primary-400 text-xs mr-1">[精品]</span>
                {{ a.title }}
              </td>
              <td class="px-4 py-3">
                <span :class="statusCls(a.status)" class="text-xs">{{ statusLabel(a.status) }}</span>
                <!-- 待审的文章只显示标题和状态，正文要点「编辑」才看得到 -->
                <p v-if="a.status === 3 && a.rejectReason" class="mt-1 text-[11px] text-red-400 break-words max-w-[220px]">
                  {{ a.rejectReason }}
                </p>
              </td>
              <td class="px-4 py-3 text-gray-400">{{ a.viewCount }}</td>
              <td class="px-4 py-3 text-gray-400 whitespace-nowrap">{{ a.createTime?.substring(0, 10) }}</td>
              <td class="px-4 py-3 whitespace-nowrap">
                <!-- 待审：只有超管能操作，和后台接口的 hasRole('super') 对齐 -->
                <template v-if="a.status === 2 && isSuper">
                  <button @click="approve(a)" class="text-primary-600 dark:text-primary-400 hover:underline text-xs mr-3 font-medium">通过</button>
                  <button @click="reject(a)" class="text-amber-600 hover:underline text-xs mr-3">驳回</button>
                </template>
                <NuxtLink :to="`/admin/articles/${a.id}`" class="text-primary-500 hover:underline text-xs mr-3">编辑</NuxtLink>
                <NuxtLink :to="`/admin/articles/revisions/${a.id}`" class="text-gray-500 hover:underline text-xs mr-3">历史</NuxtLink>
                <!-- 精品：只有超管能设，且只是卡片上的徽标，不影响首页布局 -->
                <button v-if="isSuper && a.status === 1" @click="toggleFeatured(a)"
                  class="text-xs mr-3 hover:underline"
                  :class="a.isFeatured ? 'text-gray-500' : 'text-primary-600 dark:text-primary-400'">
                  {{ a.isFeatured ? '取消精品' : '设为精品' }}
                </button>
                <button @click="handleExportOne(a)" class="text-green-500 hover:underline text-xs mr-3">导出</button>
                <button @click="handleDelete(a.id)" class="text-red-500 hover:underline text-xs">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Pagination v-if="totalPages > 1" :current="pageNum" :total="totalPages" @change="pageNum = $event" />

    <!-- 驳回理由：必须填，作者才知道该改什么。
         用页面内的弹窗而不是 window.prompt —— 后者样式丑、移动端体验差，还可能被浏览器拦。 -->
    <div v-if="rejectTarget" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 px-4"
      @click.self="rejectTarget = null">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-lg p-6 w-full max-w-md">
        <h2 class="text-base font-bold mb-1">驳回《{{ rejectTarget.title }}》</h2>
        <p class="text-xs text-gray-500 mb-3">作者会看到这段话，说清楚要改什么，能少来回几次。</p>
        <textarea v-model="rejectReason" rows="4" aria-label="驳回理由"
          placeholder="例如：第二段的示意图是外站的，麻烦换成本站图床的链接"
          class="w-full px-3 py-2 border rounded dark:bg-gray-700 dark:border-gray-600 text-sm" />
        <div class="flex justify-end gap-2 mt-4">
          <button @click="rejectTarget = null" class="px-4 py-2 border rounded text-sm dark:border-gray-600">取消</button>
          <button @click="doReject" :disabled="rejecting"
            class="px-4 py-2 bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 rounded text-sm disabled:opacity-50 transition">
            {{ rejecting ? '提交中…' : '确认驳回' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const { get, del, post, put } = useApi()
const { toast, confirmDialog } = useFeedback()
const config = useRuntimeConfig()
const apiBase = config.public.apiBase
const token = useCookie('token')
/** 精品操作只对超管开放（mounted 后置 true，避免 SSR 水合不一致） */
const isSuper = useIsSuper()
const purgeCache = usePurgeCache()
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

/**
 * 设为 / 取消精品。
 * ⚠️ 必须自判 `res.code`：后端业务异常是 HTTP 200 + body.code=400，`useApi` 不会抛错，
 * 只看 try/catch 会把失败当成功（这个坑在本项目踩过多次）。
 */
async function toggleFeatured(a: any) {
  const next = a.isFeatured ? 0 : 1
  try {
    const res = await put<any>(`/article/${a.id}/featured`, { featured: next })
    if (res?.code !== 200) {
      toast('设置失败: ' + (res?.message || `code ${res?.code}`), 'error')
      return
    }
    a.isFeatured = next
    toast(next === 1 ? '已设为精品' : '已取消精品', 'success')
    // 首页有 20s 缓存，不清就得等一轮才看得见
    try { await purgeCache() } catch { /* 清缓存失败不影响设置结果 */ }
  } catch (e: any) {
    toast('设置失败: ' + (e.message || '未知错误'), 'error')
  }
}

async function handleDelete(id: number) {
  if (!(await confirmDialog('确定删除这篇文章？将移入回收站，可在回收站恢复。', '删除文章', 'danger'))) return
  try {
    await del(`/article/${id}`)
    toast('删除成功', 'success')
    fetchList()
  } catch (e: any) {
    toast('删除失败: ' + (e.message || '未知错误'), 'error')
  }
}

/* ---------------- 投稿审核 ---------------- */

const rejectTarget = ref<any>(null)
const rejectReason = ref('')
const rejecting = ref(false)
const approving = ref(false)

/** 0 草稿 / 1 已发布 / 2 待审 / 3 已驳回 —— 与后端 ArticleService.STATUS_* 对应 */
function statusLabel(s: number) {
  if (s === 1) return '已发布'
  if (s === 2) return '待审核'
  if (s === 3) return '已驳回'
  return '草稿'
}

function statusCls(s: number) {
  if (s === 1) return 'text-green-500'
  if (s === 2) return 'text-primary-600 dark:text-primary-400 font-medium'
  if (s === 3) return 'text-red-500'
  return 'text-yellow-500'
}

async function approve(a: any) {
  if (approving.value) return
  approving.value = true
  try {
    // 同 toggleFeatured：业务失败是 HTTP 200 + code=400，必须自己判 code
    const res = await post<any>(`/article/${a.id}/approve`, {})
    if (res?.code !== 200) {
      toast('通过失败: ' + (res?.message || `code ${res?.code}`), 'error')
      return
    }
    toast('已发布', 'success')
    try { await purgeCache() } catch { /* 清缓存失败不影响发布结果 */ }
    fetchList()
  } catch (e: any) {
    toast('通过失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    approving.value = false
  }
}

function reject(a: any) {
  rejectTarget.value = a
  rejectReason.value = ''
}

async function doReject() {
  if (!rejectTarget.value || rejecting.value) return
  if (!rejectReason.value.trim()) {
    toast('请填写驳回理由', 'error')
    return
  }
  rejecting.value = true
  try {
    const res = await post<any>(`/article/${rejectTarget.value.id}/reject`, {
      reason: rejectReason.value.trim()
    })
    if (res?.code !== 200) {
      toast('驳回失败: ' + (res?.message || `code ${res?.code}`), 'error')
      return
    }
    toast('已驳回', 'success')
    rejectTarget.value = null
    fetchList()
  } catch (e: any) {
    toast('驳回失败: ' + (e.message || '未知错误'), 'error')
  } finally {
    rejecting.value = false
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
  if (!(await confirmDialog(`确定删除选中的 ${selectedIds.value.length} 篇文章？将移入回收站，可在回收站恢复。`, '批量删除', 'danger'))) return
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
