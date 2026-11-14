<template>
  <div class="space-y-6">
    <!-- 标题 + 范围 -->
    <div class="flex items-center justify-between gap-3 flex-wrap">
      <div>
        <h1 class="text-2xl font-bold">AI 用量分析</h1>
        <p class="text-xs text-gray-400 mt-1">数据来自每日汇总表，不含明细，所以用得再多这张页也不会变慢。</p>
      </div>
      <div class="flex items-center gap-2">
        <div class="flex rounded-md border border-gray-200 dark:border-gray-600 overflow-hidden text-sm">
          <button v-for="r in RANGES" :key="r.value"
                  class="px-3 py-1.5 transition"
                  :class="days === r.value ? 'bg-ink text-paper' : 'text-gray-500 dark:text-gray-400 hover:text-primary-600'"
                  @click="setDays(r.value)">
            {{ r.label }}
          </button>
        </div>
        <!-- 模型页是超管专属（增删改模型 = 改全站在用哪个模型），普通管理员这里不显示入口 -->
        <NuxtLink v-if="isSuper" to="/admin/ai/models" class="px-3 py-1.5 text-sm rounded-md border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:border-primary-400 hover:text-primary-600 transition">
          模型设置
        </NuxtLink>
      </div>
    </div>

    <!-- ① 数字卡 -->
    <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
        <div class="text-xs text-gray-400">今日金额</div>
        <div class="mt-2 text-2xl font-bold tabular-nums">¥{{ money(summary?.todayCost) }}</div>
        <div class="text-xs text-gray-400 mt-1">{{ summary?.todayRequests || 0 }} 次请求</div>
      </div>
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
        <div class="text-xs text-gray-400">今日次数</div>
        <div class="mt-2 text-2xl font-bold tabular-nums">{{ summary?.todayRequests || 0 }}</div>
        <div class="text-xs text-gray-400 mt-1">{{ tokens(summary?.todayTokens) }} token</div>
      </div>
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
        <div class="text-xs text-gray-400">今日 token</div>
        <div class="mt-2 text-2xl font-bold tabular-nums">{{ tokens(summary?.todayTokens) }}</div>
        <div class="text-xs text-gray-400 mt-1">输入 + 输出合计</div>
      </div>
      <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
        <div class="text-xs text-gray-400">近 {{ days }} 天累计</div>
        <div class="mt-2 text-2xl font-bold tabular-nums">¥{{ money(summary?.totalCost) }}</div>
        <div class="text-xs text-gray-400 mt-1">{{ summary?.totalRequests || 0 }} 次 · {{ tokens(summary?.totalTokens) }} token</div>
      </div>
    </div>

    <!-- ② 趋势 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
      <div class="flex items-center justify-between gap-2 flex-wrap mb-2">
        <h2 class="font-medium">每日趋势</h2>
        <div class="flex items-center gap-3 text-xs">
          <div class="flex rounded border border-gray-200 dark:border-gray-600 overflow-hidden">
            <button v-for="g in GROUPS" :key="g.value" class="px-2.5 py-1 transition"
                    :class="groupBy === g.value ? 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200' : 'text-gray-400'"
                    @click="setGroupBy(g.value)">
              {{ g.label }}
            </button>
          </div>
          <div class="flex rounded border border-gray-200 dark:border-gray-600 overflow-hidden">
            <button v-for="m in METRICS" :key="m.value" class="px-2.5 py-1 transition"
                    :class="metric === m.value ? 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-200' : 'text-gray-400'"
                    @click="metric = m.value">
              {{ m.label }}
            </button>
          </div>
        </div>
      </div>
      <AiEcharts :option="trendOption" :empty="trendEmpty" empty-text="这段时间还没有调用记录" height="300px" />
      <p class="text-[11px] text-gray-400 mt-1">鼠标悬停某条线会把其余线压淡；点图例可单独隐藏。</p>
    </div>

    <!-- ③ 树状图 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
      <div class="flex items-center justify-between gap-2 flex-wrap mb-2">
        <h2 class="font-medium">消耗构成</h2>
        <span class="text-xs text-gray-400">
          面积 = {{ areaKey === 'cost' ? '金额' : '调用次数' }} · 共 {{ byUser?.children?.length || 0 }} 位用户
        </span>
      </div>
      <p v-if="areaKey === 'requests' && !treemapEmpty" class="text-[11px] text-primary-700 dark:text-primary-300 mb-2">
        这些模型的单价还没配置（填 0），金额恒为 0 画不出面积，所以改按调用次数显示。
      </p>
      <AiEcharts :option="treemapOption" :empty="treemapEmpty" empty-text="这段时间还没有消耗" height="340px" />
      <p class="text-[11px] text-gray-400 mt-1">外层方块是用户，内层是该用户在各模型上的分布；面积最大的那块就是花得最多的用户。</p>
    </div>

    <!-- ④ Top 用户 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper overflow-hidden">
      <div class="flex items-center justify-between gap-2 px-4 py-3 border-b dark:border-gray-700">
        <h2 class="font-medium">Top 用户</h2>
        <button class="text-xs px-2.5 py-1 rounded border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 hover:border-primary-400 hover:text-primary-600 transition disabled:opacity-40"
                :disabled="!topUsers.length" @click="exportCsv">
          导出 CSV
        </button>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full min-w-[720px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <th class="px-4 py-2.5 text-left">用户</th>
              <th class="px-4 py-2.5 text-right">次数</th>
              <th class="px-4 py-2.5 text-right">token</th>
              <th class="px-4 py-2.5 text-right">金额</th>
              <th class="px-4 py-2.5 text-left w-40">占比</th>
              <th class="px-4 py-2.5 text-left">最后活跃</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!topUsers.length">
              <td colspan="6" class="px-4 py-8 text-center text-gray-400 text-sm">这段时间还没有调用记录</td>
            </tr>
            <template v-else>
            <tr v-for="u in topUsers" :key="u.userId" class="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50">
              <td class="px-4 py-2.5 font-medium">{{ u.name }}</td>
              <td class="px-4 py-2.5 text-right tabular-nums">{{ u.requests }}</td>
              <td class="px-4 py-2.5 text-right tabular-nums text-gray-500 dark:text-gray-400">{{ tokens(u.tokens) }}</td>
              <td class="px-4 py-2.5 text-right tabular-nums">¥{{ money(u.cost) }}</td>
              <td class="px-4 py-2.5">
                <div class="flex items-center gap-2">
                  <div class="flex-1 h-1.5 rounded-full bg-gray-100 dark:bg-gray-700 overflow-hidden">
                    <div class="h-full rounded-full bg-primary-500" :style="{ width: barWidth(u.ratio) }"></div>
                  </div>
                  <span class="text-xs text-gray-400 tabular-nums w-10 text-right">{{ u.ratio }}%</span>
                </div>
              </td>
              <td class="px-4 py-2.5 text-xs text-gray-400 whitespace-nowrap">{{ u.lastActive || '-' }}</td>
            </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ⑤ 限流命中 -->
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper overflow-hidden">
      <div class="flex items-center justify-between gap-2 px-4 py-3 border-b dark:border-gray-700">
        <h2 class="font-medium">今日触发限流的用户</h2>
        <span class="text-xs text-gray-400">只保留最近两天</span>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full min-w-[560px] text-sm">
          <thead class="bg-gray-50 dark:bg-gray-700 text-gray-600 dark:text-gray-300">
            <tr>
              <th class="px-4 py-2.5 text-left">用户</th>
              <th class="px-4 py-2.5 text-left">最后触发层</th>
              <th class="px-4 py-2.5 text-right">次数</th>
              <th class="px-4 py-2.5 text-left">最后触发时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!limitHits.length">
              <td colspan="4" class="px-4 py-8 text-center text-gray-400 text-sm">
                今天还没有人被拦。用量变少就是真的没人问，不是被限流挡住了。
              </td>
            </tr>
            <template v-else>
            <tr v-for="h in limitHits" :key="h.userId" class="border-t dark:border-gray-700">
              <td class="px-4 py-2.5">{{ h.name }}</td>
              <td class="px-4 py-2.5 text-gray-500 dark:text-gray-400">{{ h.scopeLabel || h.scope }}</td>
              <td class="px-4 py-2.5 text-right tabular-nums">{{ h.count }}</td>
              <td class="px-4 py-2.5 text-xs text-gray-400 whitespace-nowrap">{{ h.lastAt || '-' }}</td>
            </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>

    <!-- ⑥ 限流阈值：读只读，改只给超管（后端 PUT /admin/ai/setting 也是 hasRole('super')） -->
    <div class="bg-white dark:bg-gray-800 rounded-lg shadow-paper p-4">
      <div class="flex items-center justify-between gap-2 flex-wrap mb-3">
        <h2 class="font-medium">限流与预算</h2>
        <span class="text-xs text-gray-400">填 0 表示该层不限制 · 管理员不受限</span>
      </div>

      <p v-if="!isSuper" class="text-sm text-gray-500 dark:text-gray-400">
        限流与预算只有超级管理员能改。当前值：
        每分钟 {{ limits.minuteLimit || '不限' }} ·
        每小时 {{ limits.hourlyLimit || '不限' }} ·
        每天 {{ limits.dailyLimit || '不限' }} 次 ·
        每天 {{ limits.dailyTokenLimit || '不限' }} token ·
        全站日预算 {{ limits.globalDailyBudget ? '¥' + limits.globalDailyBudget : '不限' }}
      </p>

      <template v-else>
      <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <div>
          <label for="lim-minute" class="text-xs text-gray-500 dark:text-gray-400">每用户每分钟</label>
          <input id="lim-minute" v-model.number="limits.minuteLimit" type="number" min="0"
                 class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
        </div>
        <div>
          <label for="lim-hour" class="text-xs text-gray-500 dark:text-gray-400">每用户每小时</label>
          <input id="lim-hour" v-model.number="limits.hourlyLimit" type="number" min="0"
                 class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
        </div>
        <div>
          <label for="lim-day" class="text-xs text-gray-500 dark:text-gray-400">每用户每天</label>
          <input id="lim-day" v-model.number="limits.dailyLimit" type="number" min="0"
                 class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
        </div>
        <div>
          <label for="lim-token" class="text-xs text-gray-500 dark:text-gray-400">每用户每天 token 上限</label>
          <input id="lim-token" v-model.number="limits.dailyTokenLimit" type="number" min="0"
                 class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
        </div>
      </div>
      <div class="flex items-end justify-between gap-3 flex-wrap mt-3">
        <div class="w-full sm:w-56">
          <label for="lim-budget" class="text-xs text-gray-500 dark:text-gray-400">全站日预算（元）</label>
          <input id="lim-budget" v-model.number="limits.globalDailyBudget" type="number" min="0" step="0.01"
                 class="w-full mt-1 px-3 py-2 border rounded text-sm dark:bg-gray-700 dark:border-gray-600" />
          <p class="text-[11px] text-gray-400 mt-1">达到上限后全站停止服务，次日 0 点恢复（管理员仍可用）</p>
        </div>
        <button class="px-4 py-2 rounded text-sm bg-ink text-paper hover:opacity-90 disabled:opacity-50 transition"
                :disabled="savingLimits" @click="saveLimits">
          {{ savingLimits ? '保存中…' : '保存阈值' }}
        </button>
      </div>
      </template>

      <!-- 预算闸门状态（P4）。没设预算时整块不显示 —— 0 表示不限，画个空进度条只会误导 -->
      <div v-if="budget && !budget.unlimited" class="mt-3">
        <div class="flex items-center justify-between text-xs mb-1">
          <span class="text-gray-500 dark:text-gray-400">
            今日已用 <b class="text-gray-700 dark:text-gray-200">¥{{ Number(budget.spent).toFixed(2) }}</b>
            / ¥{{ Number(budget.budget).toFixed(2) }}
          </span>
          <span v-if="budget.exceeded" class="text-red-600 dark:text-red-400 font-medium">
            已熔断，全站暂停服务
          </span>
          <span v-else class="text-gray-400">剩余 ¥{{ (Number(budget.budget) - Number(budget.spent)).toFixed(2) }}</span>
        </div>
        <div class="h-1.5 rounded-full bg-gray-100 dark:bg-gray-700 overflow-hidden">
          <div class="h-full rounded-full transition-all"
               :class="budget.exceeded ? 'bg-red-500' : 'bg-primary-500'"
               :style="{ width: Math.min(100, Math.max(0, Number(budget.ratio) * 100)) + '%' }"></div>
        </div>
        <p class="text-[11px] text-gray-400 mt-1">
          判定有最多 60 秒缓存延迟（避免每次提问都做一次聚合查询），所以可能出现"刚超一点还能问一句"。
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  buildTrendOption,
  buildTreemapOption,
  isTrendEmpty,
  isTreemapEmpty,
  resolveAreaKey
} from '~/utils/aiChartOptions'

definePageMeta({ middleware: 'admin', layout: 'admin' })

/** 限流/预算的修改权只在超管手里；普通管理员这页仍然能看全部用量报表 */
const authStore = useAuthStore()
const isSuper = computed(() => authStore.isSuper)

const RANGES = [
  { label: '7 天', value: 7 },
  { label: '30 天', value: 30 },
  { label: '90 天', value: 90 }
]
const GROUPS = [
  { label: '按模型', value: 'model' },
  { label: '按厂商', value: 'provider' }
]
const METRICS = [
  { label: '金额', value: 'cost' },
  { label: '次数', value: 'requests' }
]

const { get, put } = useApi()
const { toast } = useFeedback()
const colorMode = useColorMode()
const dark = computed(() => colorMode.value === 'dark')

const days = ref(7)
const groupBy = ref<'model' | 'provider'>('model')
const metric = ref<'cost' | 'requests'>('cost')

const summary = ref<any>(null)
const trend = ref<any>(null)
const byUser = ref<any>(null)
const topUsers = ref<any[]>([])
const limitHits = ref<any[]>([])
/** 预算闸门状态：{spent, budget, ratio, exceeded, unlimited} */
const budget = ref<any>(null)

// ---------------- 数据 ----------------

async function loadAll() {
  try {
    const [s, t, b, u, h, bd] = await Promise.all([
      get<any>(`/admin/ai/usage/summary?days=${days.value}`),
      get<any>(`/admin/ai/usage/trend?days=${days.value}&groupBy=${groupBy.value}`),
      get<any>(`/admin/ai/usage/by-user?days=${days.value}`),
      get<any>(`/admin/ai/usage/top-users?days=${days.value}&limit=10`),
      get<any>('/admin/ai/usage/limit-hits?limit=20'),
      get<any>('/admin/ai/usage/budget')
    ])
    if (s.code === 200) summary.value = s.data
    if (t.code === 200) trend.value = t.data
    if (b.code === 200) byUser.value = b.data
    if (u.code === 200) topUsers.value = u.data || []
    if (h.code === 200) limitHits.value = h.data || []
    if (bd.code === 200) budget.value = bd.data
  } catch (e: any) {
    toast('加载失败：' + (e.message || '未知错误'), 'error')
  }
}

onMounted(() => {
  loadAll()
  loadLimits()
})

watch([days, groupBy], loadAll)

function setDays(v: number) { days.value = v }
function setGroupBy(v: 'model' | 'provider') { groupBy.value = v }

// ---------------- 展示辅助 ----------------

/** 金额很小，两位小数会全是 0.00，所以保留四位 */
function money(v: any) {
  return Number(v || 0).toFixed(4)
}

function tokens(v: any) {
  const n = Number(v || 0)
  if (n >= 1000000) return (n / 1000000).toFixed(2) + 'M'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

function barWidth(ratio: any) {
  const n = Math.max(0, Math.min(100, Number(ratio || 0)))
  return n + '%'
}

// ---------------- 折线 ----------------

const trendEmpty = computed(() => isTrendEmpty(trend.value, metric.value))

const trendOption = computed(() =>
  buildTrendOption(trend.value, { dark: dark.value, metric: metric.value })
)

// ---------------- 树状图 ----------------

const areaKey = computed(() => resolveAreaKey(byUser.value))

const treemapEmpty = computed(() => isTreemapEmpty(byUser.value, areaKey.value))

const treemapOption = computed(() =>
  buildTreemapOption(byUser.value, { dark: dark.value, area: areaKey.value })
)

// ---------------- 限流阈值 ----------------

const limits = reactive({
  minuteLimit: 5,
  hourlyLimit: 30,
  dailyLimit: 100,
  dailyTokenLimit: 0,
  globalDailyBudget: 0
})
const savingLimits = ref(false)

async function loadLimits() {
  try {
    const res = await get<any>('/admin/ai/setting')
    if (res.code !== 200 || !res.data) return
    const s = res.data
    limits.minuteLimit = Number(s.minuteLimit ?? 5)
    limits.hourlyLimit = Number(s.hourlyLimit ?? 30)
    limits.dailyLimit = Number(s.dailyLimit ?? 100)
    limits.dailyTokenLimit = Number(s.dailyTokenLimit ?? 0)
    limits.globalDailyBudget = Number(s.globalDailyBudget ?? 0)
  } catch {
    // 读不到就保留默认值，不打断整页
  }
}

async function saveLimits() {
  if (savingLimits.value) return
  savingLimits.value = true
  try {
    const res = await put<any>('/admin/ai/setting', {
      minuteLimit: Math.max(0, Number(limits.minuteLimit) || 0),
      hourlyLimit: Math.max(0, Number(limits.hourlyLimit) || 0),
      dailyLimit: Math.max(0, Number(limits.dailyLimit) || 0),
      dailyTokenLimit: Math.max(0, Number(limits.dailyTokenLimit) || 0),
      globalDailyBudget: Math.max(0, Number(limits.globalDailyBudget) || 0)
    })
    if (res.code !== 200) { toast(res.message || '保存失败', 'error'); return }
    toast('阈值已保存，5 秒内生效', 'success')
    loadLimits()
    // 预算金额读的是 5 秒缓存的 setting，立刻重取还是旧值 ——
    // 稍等再刷，避免管理员以为"改了没生效"
    setTimeout(() => { loadBudget() }, 5500)
  } catch (e: any) {
    toast('保存失败：' + (e.message || '未知错误'), 'error')
  } finally {
    savingLimits.value = false
  }
}

async function loadBudget() {
  try {
    const res = await get<any>('/admin/ai/usage/budget')
    if (res.code === 200) budget.value = res.data
  } catch {
    // 预算状态是辅助信息，读不到不该打断整页
  }
}

// ---------------- 导出 ----------------

function exportCsv() {
  if (!topUsers.value.length) return
  const rows: string[][] = [['用户', '次数', 'token', '金额(元)', '占比(%)', '最后活跃']]
  for (const u of topUsers.value) {
    rows.push([
      String(u.name ?? ''),
      String(u.requests ?? 0),
      String(u.tokens ?? 0),
      Number(u.cost || 0).toFixed(4),
      String(u.ratio ?? 0),
      String(u.lastActive ?? '')
    ])
  }
  const csv = rows
    .map(r => r.map(c => `"${c.replace(/"/g, '""')}"`).join(','))
    .join('\r\n')

  // BOM：不加的话 Excel 打开中文表头是乱码
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `ai-usage-${days.value}d-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

useHead({ title: 'AI 用量 - 后台' })
</script>
