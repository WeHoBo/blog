<template>
  <div>
    <div class="flex items-center justify-between gap-4 mb-6 flex-wrap">
      <h1 class="text-2xl font-bold">操作日志</h1>
      <!-- 筛选用胶囊按钮，与后台其他页导航保持一致；动作按钮一律墨色 -->
      <div class="flex items-center gap-2">
        <button v-for="opt in FILTERS" :key="opt.value" type="button" @click="status = opt.value"
          class="px-3 py-1.5 rounded-full border text-xs transition"
          :class="status === opt.value
            ? 'bg-gray-900 text-white border-gray-900 dark:bg-gray-100 dark:text-gray-900 dark:border-gray-100'
            : 'border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-white/5'">
          {{ opt.label }}
        </button>
      </div>
    </div>

    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full min-w-[880px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <th class="px-4 py-3 text-left w-36">时间</th>
              <th class="px-4 py-3 text-left w-24">用户</th>
              <th class="px-4 py-3 text-left">操作</th>
              <th class="px-4 py-3 text-left w-20">结果</th>
              <th class="px-4 py-3 text-left w-24">耗时</th>
              <th class="px-4 py-3 text-left w-36">IP</th>
              <th class="px-4 py-3 text-left w-40">请求 ID</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id"
              class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <td class="px-4 py-3 text-gray-400 text-xs whitespace-nowrap">{{ fmtTime(log.createTime) }}</td>
              <td class="px-4 py-3 text-gray-600 dark:text-gray-300 text-xs font-medium">{{ log.username }}</td>
              <td class="px-4 py-3 text-sm">{{ log.action }}</td>

              <!-- 结果：这是扩容后最该看的一列。失败 = 有人试了但被拒 -->
              <td class="px-4 py-3">
                <span class="inline-block px-1.5 py-0.5 rounded text-[11px] font-medium"
                  :class="log.status === 0
                    ? 'bg-red-50 text-red-600 dark:bg-red-500/15 dark:text-red-400'
                    : 'bg-primary-50 text-primary-700 dark:bg-primary-500/15 dark:text-primary-300'">
                  {{ log.status === 0 ? '失败' : '成功' }}
                </span>
              </td>

              <!-- 耗时：超过 1 秒标黄，别看它是"审计"字段，它也是慢接口的第一手证据 -->
              <td class="px-4 py-3 text-xs whitespace-nowrap"
                :class="log.costMs > 1000 ? 'text-amber-600 dark:text-amber-400' : 'text-gray-400'">
                {{ log.costMs != null ? log.costMs + 'ms' : '—' }}
              </td>

              <td class="px-4 py-3 text-xs text-gray-400 whitespace-nowrap">{{ log.ip || '—' }}</td>

              <!-- 请求 ID：点一下复制，拿去 grep 文件日志就能看到那次请求的全过程 -->
              <td class="px-4 py-3">
                <button v-if="log.requestId" type="button" @click="copyRid(log.requestId)"
                  :title="`点击复制：${log.requestId}`"
                  class="font-mono text-[11px] text-gray-400 hover:text-primary-600 dark:hover:text-primary-400 transition truncate max-w-[140px]">
                  {{ log.requestId.slice(0, 8) }}…
                </button>
                <span v-else class="text-xs text-gray-300 dark:text-gray-600">—</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div v-if="error" role="alert" class="text-center py-8 text-sm">
      操作日志读取失败。
      <button type="button" @click="refresh()" class="text-primary-600 underline">重新加载</button>
    </div>
    <div v-else-if="!pending && logs.length === 0" class="text-center py-8 text-sm text-gray-400">
      {{ status === 'fail' ? '没有失败记录' : '暂无日志' }}
    </div>

    <Pagination :current="page" :total="totalPages" @change="page = $event" />
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin', layout: 'admin' })

const { get } = useApi()
const { toast } = useFeedback()

const FILTERS = [
  { label: '全部', value: 'all' },
  { label: '只看失败', value: 'fail' },
  { label: '只成功', value: 'ok' }
] as const

const PAGE_SIZE = 50
const page = ref(1)
const status = ref<'all' | 'fail' | 'ok'>('all')

/**
 * key 必须跟着筛选走。
 *
 * <p>如果 key 固定、把结果塞进一个普通 ref，那么在 SSR 之后的客户端水合阶段
 * handler 不会重跑，ref 是空的 —— 表现就是「明明有日志却显示暂无日志，
 * 点一下筛选才冒出来」。让 data 由 useAsyncData 自己持有才是对的。
 */
const { data, pending, error, refresh } = await useAsyncData(
  () => `admin-logs-${status.value}-${page.value}`,
  async () => {
    const params = new URLSearchParams({ pageNum: String(page.value), pageSize: String(PAGE_SIZE) })
    if (status.value === 'fail') params.set('status', '0')
    if (status.value === 'ok') params.set('status', '1')
    const res = await get<any>(`/admin/log/list?${params.toString()}`)
    if (res.code !== 200 || !res.data) throw new Error(res.message || '读取失败')
    return res.data
  },
  { watch: [page, status] }
)

const logs = computed<any[]>(() => data.value?.records || [])
const totalPages = computed(() => Math.max(1, Math.ceil((data.value?.total || 0) / PAGE_SIZE)))

// 切筛选时回到第一页，否则会停在"第 3 页但只有 1 页"的空列表上
watch(status, () => { page.value = 1 })

function fmtTime(v?: string) {
  return v ? v.replace('T', ' ').substring(0, 19) : '—'
}

async function copyRid(rid: string) {
  try {
    await navigator.clipboard.writeText(rid)
    toast('请求 ID 已复制，可直接去日志里 grep', 'success')
  } catch {
    toast('复制失败，请手动选择', 'error')
  }
}
</script>
