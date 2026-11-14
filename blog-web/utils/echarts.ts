/**
 * ECharts 按需注册（只给后台用量页用）。
 *
 * 为什么不用 `import * as echarts from 'echarts'`：
 * 全量包 gzip 300KB+，而这里只画折线 + 树状图 + 少量柱状。
 * 本文件只被 `components/AiEcharts.vue` **动态 import**，不会进前台首屏。
 *
 * ⚠️ 配色常量放在 utils/chartTheme.ts —— 页面要用颜色拼 option，
 * 如果颜色也从这里导出，页面同步 import 就会把 echarts 拖进主 chunk。
 */
import * as echarts from 'echarts/core'
import { BarChart, LineChart, TreemapChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  LineChart,
  TreemapChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  CanvasRenderer
])

export default echarts
