/**
 * AI 用量页的图表 option 构造（纯函数）。
 *
 * 抽出来的理由不只是"好读"：这里没有任何 vue / DOM 依赖，
 * 所以可以在 Node 里用 echarts 的 SSR 模式把 option 真正渲染一遍，
 * 验证「按需注册是否完整、option 结构是否合法」—— 否则只能靠肉眼看浏览器。
 */
import { SERIES_COLORS, chartTheme } from './chartTheme'

export type TrendMetric = 'cost' | 'requests'

export interface TrendSeries {
  key: string
  requests?: number[]
  tokens?: number[]
  cost?: number[]
}

export interface TrendData {
  days: number
  groupBy: string
  dates: string[]
  series: TrendSeries[]
}

export interface TreemapUser {
  id: number
  name: string
  cost: number
  requests: number
  ratio: number
  children: { name: string; cost: number; requests: number }[]
}

export interface ByUserData {
  days: number
  total: number
  children: TreemapUser[]
}

const num = (v: any) => (Number.isFinite(Number(v)) ? Number(v) : 0)

/** 折线是否没数据（全 0 也算没数据，否则会画一条贴地直线，看着像"有量"） */
export function isTrendEmpty(data: TrendData | null, metric: TrendMetric): boolean {
  const series = data?.series || []
  if (!series.length) return true
  return series.every(s => (s[metric] || []).every(v => !num(v)))
}

export function buildTrendOption(
  data: TrendData | null,
  opts: { dark: boolean; metric: TrendMetric }
) {
  const th = chartTheme(opts.dark)
  const isCost = opts.metric === 'cost'
  const dates = (data?.dates || []).map(d => String(d).slice(5))
  const series = (data?.series || []).map(s => ({
    name: s.key,
    type: 'line' as const,
    smooth: true,
    showSymbol: false,
    symbolSize: 6,
    lineStyle: { width: 2 },
    // 悬停时其余线自动变淡，正好是设计里要的「点某条线可高亮」
    emphasis: { focus: 'series' as const },
    data: (s[opts.metric] || []).map(num)
  }))

  return {
    color: SERIES_COLORS,
    // ECharts 6 里 grid.containLabel 已废弃且不注册 LegacyGridContainLabel 时**不生效**
    // （y 轴的 ¥ 标签会被挤到画布外）。官方等价写法就是下面这两行。
    grid: {
      left: 4,
      right: 12,
      top: 12,
      bottom: series.length > 4 ? 40 : 16,
      outerBoundsMode: 'same' as const,
      outerBoundsContain: 'axisLabel' as const
    },
    tooltip: {
      trigger: 'axis' as const,
      backgroundColor: th.tooltipBg,
      borderColor: th.tooltipBorder,
      textStyle: { color: th.text, fontSize: 12 },
      valueFormatter: (v: any) => (isCost ? '¥' + num(v).toFixed(4) : num(v) + ' 次')
    },
    legend: series.length > 1
      ? { bottom: 0, type: 'scroll' as const, itemWidth: 12, itemHeight: 8, textStyle: { color: th.textDim, fontSize: 11 } }
      : { show: false },
    xAxis: {
      type: 'category' as const,
      boundaryGap: false,
      data: dates,
      axisLine: { lineStyle: { color: th.line } },
      axisTick: { show: false },
      axisLabel: { color: th.textDim, fontSize: 11 }
    },
    yAxis: {
      type: 'value' as const,
      splitLine: { lineStyle: { color: th.line } },
      axisLabel: {
        color: th.textDim,
        fontSize: 11,
        formatter: (v: number) => (isCost ? '¥' + v : String(v))
      }
    },
    series
  }
}

/**
 * 树状图的面积用金额还是次数。
 *
 * 单价默认是 0（很多模型先不填），此时金额恒为 0，treemap 会画成一片空白 ——
 * 看起来像"没数据"，实际是"没配价格"。这种情况自动改用次数当面积，并在页面上说明。
 */
export function resolveAreaKey(data: ByUserData | null): TrendMetric {
  return num(data?.total) > 0 ? 'cost' : 'requests'
}

export function isTreemapEmpty(data: ByUserData | null, area: TrendMetric): boolean {
  const kids = data?.children || []
  if (!kids.length) return true
  return kids.every(u => !num(u[area]))
}

export function buildTreemapOption(
  data: ByUserData | null,
  opts: { dark: boolean; area: TrendMetric }
) {
  const th = chartTheme(opts.dark)
  const isCost = opts.area === 'cost'
  const fmt = (v: any) => (isCost ? '¥' + num(v).toFixed(4) : num(v) + ' 次')

  const nodes = (data?.children || []).map(u => ({
    name: u.name,
    value: num(u[opts.area]),
    ratio: u.ratio,
    children: (u.children || []).map(c => ({
      name: c.name,
      value: num(c[opts.area])
    }))
  }))

  return {
    color: SERIES_COLORS,
    tooltip: {
      backgroundColor: th.tooltipBg,
      borderColor: th.tooltipBorder,
      textStyle: { color: th.text, fontSize: 12 },
      formatter: (p: any) => {
        const pct = p?.data && typeof p.data.ratio === 'number' ? `（占 ${p.data.ratio}%）` : ''
        return `${p?.name ?? ''}${pct}<br/>${fmt(p?.value)}`
      }
    },
    series: [{
      type: 'treemap' as const,
      roam: false,
      nodeClick: false as const,
      breadcrumb: { show: false },
      top: 2,
      left: 2,
      right: 2,
      bottom: 2,
      label: {
        show: true,
        color: '#fffdf9',
        fontSize: 11,
        overflow: 'truncate' as const,
        // label 只作用于**叶子**节点（父节点的名字由 upperLabel 画在顶部条上）。
        // 反过来写会导致第二层的模型名一个都不显示。
        formatter: (p: any) => (p?.data && p.data.children ? '' : String(p?.name ?? ''))
      },
      upperLabel: {
        show: true,
        height: 20,
        color: '#fffdf9',
        fontSize: 11,
        formatter: (p: any) => String(p?.name ?? '')
      },
      itemStyle: {
        borderColor: opts.dark ? '#1e1b18' : '#faf7f2',
        borderWidth: 2,
        gapWidth: 2
      },
      levels: [
        { itemStyle: { borderWidth: 0, gapWidth: 2 } },
        {
          // 第二层用同色相明度变化，不引入第二个色相
          colorSaturation: [0.28, 0.5],
          itemStyle: { borderWidth: 1, gapWidth: 1, borderColorSaturation: 0.45 }
        }
      ],
      data: nodes
    }]
  }
}
