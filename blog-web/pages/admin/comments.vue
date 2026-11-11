<template>
  <div>
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold">评论管理</h1>
      <div class="flex gap-2">
        <button
          @click="switchTab('comments')"
          :class="tab === 'comments' ? 'bg-primary-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300'"
          class="px-4 py-2 rounded-lg text-sm transition"
        >全部评论</button>
        <button
          @click="switchTab('reports')"
          :class="tab === 'reports' ? 'bg-primary-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300'"
          class="px-4 py-2 rounded-lg text-sm transition"
        >
          举报
          <span v-if="pendingReports > 0" class="ml-1 px-1.5 py-0.5 rounded-full bg-red-500 text-white text-xs">{{ pendingReports }}</span>
        </button>
      </div>
    </div>

    <!-- 评论列表 -->
    <div v-if="tab === 'comments'">
      <div class="flex flex-wrap gap-3 mb-4">
        <input
          v-model="keyword"
          @keyup.enter="search"
          placeholder="搜索评论内容"
          aria-label="搜索评论内容"
          class="px-3 py-2 border rounded-lg dark:bg-gray-700 dark:border-gray-600 text-sm w-56"
        />
        <label class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-300">
          <input v-model="onlyReported" type="checkbox" @change="search" />
          只看被举报
        </label>
        <button @click="search" class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm hover:bg-primary-700 transition">搜索</button>
      </div>

      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full min-w-[860px] text-sm">
            <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
              <tr>
                <th class="px-4 py-3 text-left">评论内容</th>
                <th class="px-4 py-3 text-left w-48">文章</th>
                <th class="px-4 py-3 text-left w-32">评论者</th>
                <th class="px-4 py-3 text-left w-36">时间</th>
                <th class="px-4 py-3 text-left w-20">举报</th>
                <th class="px-4 py-3 text-left w-20">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!loading && comments.length === 0">
                <td colspan="6" class="px-4 py-10 text-center text-gray-400">没有符合条件的评论</td>
              </tr>
              <tr
                v-for="c in comments"
                :key="c.id"
                class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50 align-top"
              >
                <td class="px-4 py-3">
                  <p class="text-gray-700 dark:text-gray-200 whitespace-pre-wrap break-words max-w-xl">{{ c.content }}</p>
                  <span v-if="c.status !== 1" class="text-xs text-amber-500">（状态 {{ c.status }}，前台不展示）</span>
                </td>
                <td class="px-4 py-3 text-gray-500 dark:text-gray-400">
                  <NuxtLink :to="`/article/${c.articleId}`" target="_blank" class="hover:text-primary-600 line-clamp-2">
                    {{ c.articleTitle }}
                  </NuxtLink>
                </td>
                <td class="px-4 py-3 text-gray-500 dark:text-gray-400">{{ c.nickname || c.username || '—' }}</td>
                <td class="px-4 py-3 text-gray-400 text-xs">{{ fmt(c.createTime) }}</td>
                <td class="px-4 py-3">
                  <span v-if="c.reportCount > 0" class="text-xs">
                    <span :class="c.pendingReportCount > 0 ? 'text-red-500 font-medium' : 'text-gray-400'">
                      {{ c.reportCount }} 次
                    </span>
                    <span v-if="c.pendingReportCount > 0" class="text-red-400">（{{ c.pendingReportCount }} 待处理）</span>
                  </span>
                  <span v-else class="text-xs text-gray-300">—</span>
                </td>
                <td class="px-4 py-3">
                  <button @click="removeComment(c)" class="text-red-500 hover:underline text-xs">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pagination :current="pageNum" :total="totalPages" @change="p => { pageNum = p; fetchComments() }" />
      </div>
    </div>

    <!-- 举报列表 -->
    <div v-else>
      <div class="flex gap-2 mb-4">
        <button
          v-for="opt in reportStatusOptions"
          :key="String(opt.value)"
          @click="reportStatus = opt.value; reportPage = 1; fetchReports()"
          :class="reportStatus === opt.value ? 'bg-primary-600 text-white' : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300'"
          class="px-3 py-1.5 rounded-lg text-xs transition"
        >{{ opt.label }}</button>
      </div>

      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full min-w-[860px] text-sm">
            <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
              <tr>
                <th class="px-4 py-3 text-left">被举报评论</th>
                <th class="px-4 py-3 text-left w-44">文章</th>
                <th class="px-4 py-3 text-left w-28">举报人</th>
                <th class="px-4 py-3 text-left w-24">原因</th>
                <th class="px-4 py-3 text-left w-36">时间</th>
                <th class="px-4 py-3 text-left w-32">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!loading && reports.length === 0">
                <td colspan="6" class="px-4 py-10 text-center text-gray-400">没有举报记录</td>
              </tr>
              <tr
                v-for="r in reports"
                :key="r.id"
                class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50 align-top"
              >
                <td class="px-4 py-3">
                  <p v-if="r.commentContent" class="text-gray-700 dark:text-gray-200 whitespace-pre-wrap break-words max-w-xl">{{ r.commentContent }}</p>
                  <p v-else class="text-gray-400 text-xs">（评论已被删除）</p>
                </td>
                <td class="px-4 py-3 text-gray-500 dark:text-gray-400">{{ r.articleTitle }}</td>
                <td class="px-4 py-3 text-gray-500 dark:text-gray-400">{{ r.reporterNickname || '—' }}</td>
                <td class="px-4 py-3">
                  <span class="px-2 py-0.5 rounded bg-amber-50 dark:bg-amber-900/30 text-amber-600 dark:text-amber-400 text-xs">{{ r.reason }}</span>
                </td>
                <td class="px-4 py-3 text-gray-400 text-xs">{{ fmt(r.createTime) }}</td>
                <td class="px-4 py-3 whitespace-nowrap">
                  <template v-if="r.status === 0">
                    <button @click="handleReport(r, 'delete')" class="text-red-500 hover:underline text-xs mr-3">删除评论</button>
                    <button @click="handleReport(r, 'ignore')" class="text-gray-500 hover:underline text-xs">忽略</button>
                  </template>
                  <span v-else class="text-xs text-gray-400">{{ r.status === 1 ? '已忽略' : '评论已删除' }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <Pagination :current="reportPage" :total="reportPages" @change="p => { reportPage = p; fetchReports() }" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

/**
 * 评论管理。
 *
 * 两个视角：
 * - 全部评论：日常巡检用，支持按内容搜索、只看被举报；
 * - 举报：按状态过滤，处理动作只有「删除评论」和「忽略」两种，不给自己留模糊地带。
 *
 * ⚠️ 后端业务失败是 HTTP 200 + body.code != 200（见 GlobalExceptionHandler），
 * useApi 不会抛错，所以这里每处都显式判 code。
 */
const { get, del, put } = useApi()
const { toast, confirmDialog } = useFeedback()

const tab = ref<'comments' | 'reports'>('comments')
const loading = ref(false)

const pageNum = ref(1)
const totalPages = ref(0)
const keyword = ref('')
const onlyReported = ref(false)
const comments = ref<any[]>([])

const reportPage = ref(1)
const reportPages = ref(0)
const reportStatus = ref<number | undefined>(0)
const reports = ref<any[]>([])
const pendingReports = ref(0)

const reportStatusOptions = [
  { label: '待处理', value: 0 },
  { label: '已忽略', value: 1 },
  { label: '评论已删除', value: 2 },
  { label: '全部', value: undefined }
]

function fmt(v: string) {
  return (v || '').replace('T', ' ').substring(0, 16)
}

async function fetchComments() {
  loading.value = true
  try {
    const params: any = { pageNum: pageNum.value, pageSize: 10 }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (onlyReported.value) params.reported = true
    const res = await get<any>(`/comment/admin/list?${new URLSearchParams(params).toString()}`)
    if (res.code !== 200) {
      toast(res.message || '加载失败', 'error')
      return
    }
    comments.value = res.data?.records || []
    totalPages.value = res.data?.pages || 0
  } catch (e: any) {
    toast('加载失败：' + (e.message || '未知错误'), 'error')
  } finally {
    loading.value = false
  }
}

async function fetchReports() {
  loading.value = true
  try {
    const params: any = { pageNum: reportPage.value, pageSize: 10 }
    if (reportStatus.value !== undefined) params.status = reportStatus.value
    const res = await get<any>(`/comment/admin/reports?${new URLSearchParams(params).toString()}`)
    if (res.code !== 200) {
      toast(res.message || '加载失败', 'error')
      return
    }
    reports.value = res.data?.records || []
    reportPages.value = res.data?.pages || 0
  } catch (e: any) {
    toast('加载失败：' + (e.message || '未知错误'), 'error')
  } finally {
    loading.value = false
  }
}

/** 顶部那个红点：只数「待处理」的举报，不受当前筛选影响 */
async function fetchPendingCount() {
  try {
    const res = await get<any>('/comment/admin/reports?pageNum=1&pageSize=1&status=0')
    if (res.code === 200) pendingReports.value = res.data?.total || 0
  } catch {
    // 红点失败不影响主流程，静默
  }
}

function search() {
  pageNum.value = 1
  fetchComments()
}

function switchTab(t: 'comments' | 'reports') {
  tab.value = t
  if (t === 'comments') fetchComments()
  else fetchReports()
}

async function removeComment(c: any) {
  if (!(await confirmDialog('删除后不可恢复，确定删除这条评论？', '删除评论', true))) return
  await doDelete(c.id)
}

async function doDelete(id: number) {
  try {
    const res = await del<any>(`/comment/admin/${id}`)
    if (res.code !== 200) {
      toast(res.message || '删除失败', 'error')
      return
    }
    toast('已删除', 'success')
    await Promise.all([fetchComments(), fetchPendingCount()])
    if (tab.value === 'reports') await fetchReports()
  } catch (e: any) {
    toast('删除失败：' + (e.message || '未知错误'), 'error')
  }
}

async function handleReport(r: any, action: 'delete' | 'ignore') {
  if (action === 'delete') {
    if (!(await confirmDialog('删除这条评论？该评论下所有举报会一并标记为已处理。', '删除评论', true))) return
  }
  try {
    const res = await put<any>(`/comment/admin/reports/${r.id}`, { action })
    if (res.code !== 200) {
      toast(res.message || '操作失败', 'error')
      return
    }
    toast(action === 'delete' ? '评论已删除' : '已忽略', 'success')
    await Promise.all([fetchReports(), fetchPendingCount()])
  } catch (e: any) {
    toast('操作失败：' + (e.message || '未知错误'), 'error')
  }
}

onMounted(() => {
  fetchComments()
  fetchPendingCount()
})
</script>
