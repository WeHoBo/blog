<template>
  <div class="max-w-4xl mx-auto px-4 py-8 lg:px-0">
    <div class="flex items-center justify-between gap-3 flex-wrap mb-5">
      <div>
        <h1 class="text-xl font-bold text-gray-900 dark:text-gray-100">我的文章</h1>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          提交后由站长审核，通过后才会出现在站内。
        </p>
      </div>
      <NuxtLink to="/author/create"
        class="px-4 py-2 rounded-lg bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-sm font-medium hover:opacity-85 transition">
        写新文章
      </NuxtLink>
    </div>

    <!-- 状态筛选 -->
    <div class="flex gap-1.5 mb-4 flex-wrap">
      <button v-for="t in tabs" :key="String(t.value)" @click="status = t.value; fetchList()"
        class="px-3 py-1.5 rounded-full text-xs border transition"
        :class="status === t.value
          ? 'border-primary-300 bg-primary-50 text-primary-700 dark:bg-primary-900/20 dark:text-primary-300'
          : 'border-gray-200 dark:border-gray-700 text-gray-500 dark:text-gray-400 hover:border-gray-300'">
        {{ t.label }}
      </button>
    </div>

    <div v-if="loading" class="py-16 text-center text-sm text-gray-400">加载中…</div>

    <div v-else-if="!list.length" class="py-16 text-center">
      <p class="text-sm text-gray-400">这里还没有文章</p>
      <NuxtLink to="/author/create" class="mt-2 inline-block text-sm text-primary-600 dark:text-primary-400 hover:underline">
        写第一篇 →
      </NuxtLink>
    </div>

    <ul v-else class="space-y-2">
      <li v-for="a in list" :key="a.id"
        class="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-3">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <span class="text-[11px] px-1.5 py-0.5 rounded" :class="statusMeta(a.status).cls">
                {{ statusMeta(a.status).label }}
              </span>
              <h2 class="text-sm font-medium text-gray-900 dark:text-gray-100 truncate">{{ a.title }}</h2>
            </div>
            <p class="mt-1 text-[11px] text-gray-400">
              更新于 {{ (a.updateTime || a.createTime || '').substring(0, 16).replace('T', ' ') }}
              <template v-if="a.wordCount"> · {{ a.wordCount }} 字</template>
              <template v-if="a.status === 1 && a.viewCount !== undefined"> · {{ a.viewCount }} 次阅读</template>
            </p>
            <!-- 驳回理由：不显示的话作者只会反复重交同一篇 -->
            <p v-if="a.status === 3 && a.rejectReason"
              class="mt-1.5 text-[11px] text-red-500 dark:text-red-400 break-words">
              驳回原因：{{ a.rejectReason }}
            </p>
          </div>
          <div class="shrink-0 flex items-center gap-3 text-xs">
            <NuxtLink :to="`/author/${a.id}`" class="text-primary-600 dark:text-primary-400 hover:underline">编辑</NuxtLink>
            <a v-if="a.status === 1 && a.slug" :href="`/post/${a.slug}`" target="_blank"
              class="text-gray-500 hover:underline">查看</a>
            <button v-if="a.status === 0 || a.status === 3" @click="remove(a)"
              class="text-red-500 hover:underline">删除</button>
          </div>
        </div>
      </li>
    </ul>

    <p v-if="total > pageSize" class="mt-4 text-center text-xs text-gray-400">
      共 {{ total }} 篇 · 第 {{ pageNum }} 页
      <button class="ml-2 text-primary-600 dark:text-primary-400 hover:underline disabled:opacity-40"
        :disabled="pageNum <= 1" @click="pageNum--; fetchList()">上一页</button>
      <button class="ml-2 text-primary-600 dark:text-primary-400 hover:underline disabled:opacity-40"
        :disabled="pageNum * pageSize >= total" @click="pageNum++; fetchList()">下一页</button>
    </p>
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'author', layout: 'default' })
useHead({ title: '我的文章', meta: [{ name: 'robots', content: 'noindex' }] })

const { get, del } = useApi()
const { toast, confirmDialog } = useFeedback()

const loading = ref(true)
const list = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
/** null = 全部 */
const status = ref<number | null>(null)

const tabs = [
  { label: '全部', value: null as number | null },
  { label: '草稿', value: 0 },
  { label: '待审核', value: 2 },
  { label: '已驳回', value: 3 },
  { label: '已发布', value: 1 }
]

/** 四种状态的视觉：只有「已发布」用主题色，其余都是中性/警示色 */
function statusMeta(s: number) {
  if (s === 1) return { label: '已发布', cls: 'bg-primary-50 text-primary-700 dark:bg-primary-900/25 dark:text-primary-300' }
  if (s === 2) return { label: '待审核', cls: 'bg-amber-50 text-amber-700 dark:bg-amber-900/25 dark:text-amber-300' }
  if (s === 3) return { label: '已驳回', cls: 'bg-red-50 text-red-600 dark:bg-red-900/25 dark:text-red-300' }
  return { label: '草稿', cls: 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-300' }
}

async function fetchList() {
  loading.value = true
  try {
    const qs = new URLSearchParams({ pageNum: String(pageNum.value), pageSize: String(pageSize) })
    if (status.value !== null) qs.set('status', String(status.value))
    const res = await get<any>(`/author/articles?${qs}`)
    if (res?.code === 200) {
      list.value = res.data?.records || []
      total.value = res.data?.total || 0
    } else {
      list.value = []
      toast(res?.message || '加载失败', 'error')
    }
  } catch (e: any) {
    list.value = []
    toast(e?.message || '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function remove(a: any) {
  if (!(await confirmDialog('删除后不可恢复，确定删除这篇草稿？', '删除文章', 'danger'))) return
  try {
    const res = await del<any>(`/author/articles/${a.id}`)
    if (res?.code !== 200) {
      toast(res?.message || '删除失败', 'error')
      return
    }
    toast('已删除', 'success')
    fetchList()
  } catch (e: any) {
    toast(e?.message || '删除失败', 'error')
  }
}

// 只在客户端拉：接口需要登录态，SSR 时 cookie 里的 token 虽然有了，
// 但页面本身是 noindex 的私有页，服务端渲染没有意义，还会多一次无用请求
onMounted(fetchList)
</script>
