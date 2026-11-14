<template>
  <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm overflow-hidden">
    <!-- 头部：标题 + 月份切换（只允许往回看，未来月份没有意义） -->
    <div class="px-3 py-2.5 border-b dark:border-gray-700 flex items-center justify-between gap-2">
      <h3 class="text-xs font-bold text-gray-900 dark:text-gray-100">发布热力</h3>
      <div class="flex items-center gap-0.5">
        <button @click="step(-1)" class="w-6 h-6 rounded-md text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-white/10 transition flex items-center justify-center" title="上个月" aria-label="上个月">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="m15 18-6-6 6-6"/></svg>
        </button>
        <span class="text-[11px] text-gray-500 dark:text-gray-400 tabular-nums whitespace-nowrap">{{ label }}</span>
        <button @click="step(1)" :disabled="atCurrent"
          class="w-6 h-6 rounded-md text-gray-400 enabled:hover:text-gray-700 dark:enabled:hover:text-gray-200 enabled:hover:bg-gray-100 dark:enabled:hover:bg-white/10 disabled:opacity-30 transition flex items-center justify-center" title="下个月" aria-label="下个月">
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="m9 18 6-6-6-6"/></svg>
        </button>
      </div>
    </div>

    <div class="px-3 pt-2 pb-3">
      <div class="grid grid-cols-7 gap-1 mb-1">
        <span v-for="w in WEEK" :key="w" class="text-[10px] text-gray-300 dark:text-gray-600 text-center leading-none">{{ w }}</span>
      </div>
      <div class="grid grid-cols-7 gap-1">
        <template v-for="(c, i) in cells" :key="i">
          <span v-if="!c" class="aspect-square"></span>
          <span v-else
            class="aspect-square rounded-[4px] flex items-center justify-center text-[10px] tabular-nums leading-none transition-colors"
            :class="cellClass(c)" :title="`${c.date} · ${c.count ? c.count + ' 篇' : '未发布'}`">
            {{ c.day }}
          </span>
        </template>
      </div>

      <div class="mt-2 flex items-center justify-between text-[10px] text-gray-400">
        <span>本月 {{ total }} 篇</span>
        <span class="flex items-center gap-1">
          少
          <i v-for="l in 5" :key="l" class="w-2 h-2 rounded-[2px]" :class="LEVEL_CLASS[l - 1]"></i>
          多
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  buildMonthGrid, dateKey, monthKeyOf, monthLabel, shiftMonth, sumCounts, WEEK
} from '~/utils/contribution'

const { get } = useApi()

const today = dateKey()
const month = ref(monthKeyOf())
const atCurrent = computed(() => month.value >= monthKeyOf())

/**
 * 每月的数据按月份 key 缓存（useAsyncData 同一个 key 只请求一次）。
 * 往回翻几个月再翻回来不会重复打接口，也不会重复渲染。
 */
const { data } = useAsyncData(
  () => `contribution:${month.value}`,
  async () => {
    try {
      const res = await get<any>(`/article/contribution?month=${month.value}`)
      if (res?.code === 200 && res.data) {
        return {
          days: (res.data.days || {}) as Record<string, number>,
          total: Number(res.data.total || 0)
        }
      }
    } catch { /* 接口挂了不该让首页报错 */ }
    return { days: {} as Record<string, number>, total: 0 }
  },
  { watch: [month], default: () => ({ days: {}, total: 0 }) }
)

const days = computed(() => data.value?.days || {})
const total = computed(() => {
  const fromServer = Number(data.value?.total || 0)
  return fromServer || sumCounts(days.value)
})
const label = computed(() => monthLabel(month.value))
const cells = computed(() => buildMonthGrid(month.value, days.value, today))

/** 0–4 五档：都是同一支主题色，只调透明度级别，避免热力图变成第二个强调色 */
const LEVEL_CLASS = [
  'bg-gray-100 dark:bg-white/10 text-gray-400 dark:text-gray-500',
  'bg-primary-100 dark:bg-primary-900/40 text-primary-700 dark:text-primary-300',
  'bg-primary-300 dark:bg-primary-700/70 text-primary-900 dark:text-primary-100',
  'bg-primary-500 text-white dark:text-white',
  'bg-primary-700 text-white dark:text-white'
]

function cellClass(c: { level: number; isToday: boolean; isFuture: boolean }) {
  const base = LEVEL_CLASS[c.level]
  // 今天：加一圈描边把「几号」点出来；未来日期整体淡出，避免看起来像「那天没写」
  const todayRing = c.isToday ? ' ring-1 ring-primary-600 dark:ring-primary-300 ring-offset-1 ring-offset-white dark:ring-offset-gray-800' : ''
  const future = c.isFuture ? ' opacity-40' : ''
  return base + todayRing + future
}

function step(delta: number) {
  const next = shiftMonth(month.value, delta)
  if (next > monthKeyOf()) return
  month.value = next
}
</script>
