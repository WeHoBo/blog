/**
 * 发布热力图的纯函数集合。
 *
 * 抽成纯函数的原因：日历的边界最容易出错（月初是周几、闰月、跨年、时区），
 * 而这些都能在 node 里直接断言，不需要起一个 Nuxt 或浏览器。
 * 组件只负责把 `buildMonthGrid()` 的结果画出来。
 */

export interface ContributionCell {
  /** YYYY-MM-DD */
  date: string
  /** 当月第几天 */
  day: number
  /** 当天发布的文章数 */
  count: number
  /** 0–4，用来选深浅色 */
  level: number
  isToday: boolean
  /** 未来日期（当月里还没到的日子），渲染时淡出 */
  isFuture: boolean
}

const WEEK_LABELS = ['一', '二', '三', '四', '五', '六', '日'] as const
export const WEEK = WEEK_LABELS

/** 本地时区的 YYYY-MM-DD（不能用 toISOString：那是 UTC，晚上 8 点后会跳到第二天） */
export function dateKey(d: Date = new Date()): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/** 本地时区的 YYYY-MM */
export function monthKeyOf(d: Date = new Date()): string {
  return dateKey(d).slice(0, 7)
}

/** 月份加减，跨年自动进位（'2026-01' + -1 → '2025-12'） */
export function shiftMonth(key: string, delta: number): string {
  const [y, m] = key.split('-').map(Number)
  if (!y || !m) return key
  // 用 0 号的下标算法避开闰年/月末：Date 会自动归一化
  const d = new Date(y, m - 1 + delta, 1)
  return monthKeyOf(d)
}

export function monthLabel(key: string): string {
  const [y, m] = key.split('-').map(Number)
  if (!y || !m) return key
  return `${y} 年 ${m} 月`
}

/** 0 篇 → 0，1 篇 → 1，2–3 → 2，4–6 → 3，7+ → 4 */
export function levelOf(count: number): number {
  if (!count || count <= 0) return 0
  if (count === 1) return 1
  if (count <= 3) return 2
  if (count <= 6) return 3
  return 4
}

/**
 * 生成一个月历网格（周一为第一列）。
 *
 * 返回数组里 `null` 表示月初之前 / 月末之后的占位格子，
 * 长度补齐到 7 的整数倍，保证最后一行不会缺列。
 */
export function buildMonthGrid(
  monthKey: string,
  days: Record<string, number> = {},
  today: string = dateKey()
): (ContributionCell | null)[] {
  const [y, m] = monthKey.split('-').map(Number)
  if (!y || !m) return []

  const first = new Date(y, m - 1, 1)
  const totalDays = new Date(y, m, 0).getDate()
  // getDay(): 0=周日 → 换成「周一=0」的下标
  const lead = (first.getDay() + 6) % 7

  const cells: (ContributionCell | null)[] = []
  for (let i = 0; i < lead; i++) cells.push(null)

  for (let day = 1; day <= totalDays; day++) {
    const date = `${monthKey}-${String(day).padStart(2, '0')}`
    const count = Number(days[date] || 0)
    cells.push({
      date,
      day,
      count,
      level: levelOf(count),
      isToday: date === today,
      isFuture: date > today
    })
  }

  while (cells.length % 7 !== 0) cells.push(null)
  return cells
}

/** 当月发布总数（后端也返回 total，这里是本地兜底：days 里有值就能算出来） */
export function sumCounts(days: Record<string, number> = {}): number {
  return Object.values(days).reduce((a, b) => a + (Number(b) || 0), 0)
}
