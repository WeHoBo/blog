/**
 * 图表配色与主题（**不 import echarts**）。
 *
 * 页面需要这里的颜色常量来拼 option，如果它们和 echarts 注册代码放在同一个文件，
 * 页面同步 import 就会把 100KB 的 echarts 拖进路由主 chunk，
 * 「按需加载」等于白做。所以颜色与注册分成两个文件。
 */

/**
 * 主序列色（硬约束）。
 *
 * 项目视觉是「暖纸 + 墨色 + 单一陶土强调色」。ECharts 默认调色板是彩虹色，
 * 不显式指定的话这页会成为全站唯一一处彩色区域，风格直接断裂。
 */
export const SERIES_COLORS = [
  '#8a5a44',
  '#a8745c',
  '#c48f74',
  '#d9ab92',
  '#e8c7b4',
  '#5f5e5a',
  '#888780',
  '#b4b2a9'
]

/** 同一色相的明度序列：按用户分色时用它，不要引入第二个色相 */
export const MONO_COLORS = ['#8a5a44', '#a8745c', '#c48f74', '#d9ab92', '#e8c7b4']

/** 图表内文字与网格线：跟随明暗模式，否则浅色主题下白字看不见 */
export function chartTheme(dark: boolean) {
  return {
    text: dark ? '#d8d3ca' : '#4a423a',
    textDim: dark ? '#9a938a' : '#888780',
    line: dark ? 'rgba(216, 211, 202, 0.14)' : 'rgba(74, 66, 58, 0.12)',
    tooltipBg: dark ? '#2a2622' : '#fffdf9',
    tooltipBorder: dark ? '#4a423a' : 'rgba(74, 59, 42, 0.16)'
  }
}
