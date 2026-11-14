<template>
  <div class="relative w-full" :style="{ height }">
    <div ref="el" class="w-full h-full" :class="{ 'opacity-0': !ready }"></div>
    <div v-if="empty" class="absolute inset-0 flex items-center justify-center text-sm text-gray-400">
      {{ emptyText }}
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * ECharts 宿主组件（仅后台使用）。
 *
 * 三个必须做对的地方：
 * 1. **只在客户端初始化**：ECharts 需要真实 DOM 尺寸，SSR 阶段 init 会拿到 0×0，
 *    而且 echarts 本体不该被服务端加载。
 * 2. **ResizeObserver 而不是 window.resize**：后台侧边抽屉展开、表格行变化都会
 *    改变容器宽度但不触发 window resize，只监听 window 会导致图表被拉扁。
 * 3. **option 用 notMerge 覆盖**：切「按模型/按厂商」时序列数量会变，
 *    增量合并会把上一组多余的线留在图上。
 */
const props = withDefaults(defineProps<{
  option: Record<string, any>
  height?: string
  empty?: boolean
  emptyText?: string
}>(), {
  height: '320px',
  empty: false,
  emptyText: '暂无数据'
})

const el = ref<HTMLElement | null>(null)
const ready = ref(false)

let chart: any = null
let observer: ResizeObserver | null = null
let disposed = false

async function ensure() {
  if (chart || disposed || !el.value) return chart
  const mod = await import('~/utils/echarts')
  if (disposed || !el.value) return null
  chart = mod.default.init(el.value, undefined, { renderer: 'canvas' })
  ready.value = true
  observer = new ResizeObserver(() => chart && chart.resize())
  observer.observe(el.value)
  return chart
}

async function render() {
  if (props.empty) return
  const c = await ensure()
  if (c) c.setOption(props.option, true)
}

onMounted(render)

watch(() => props.option, render, { deep: true })

// 从「暂无数据」切到有数据时，图表此前根本没初始化，必须重新走一遍 ensure
watch(() => props.empty, (isEmpty) => { if (!isEmpty) render() })

onBeforeUnmount(() => {
  disposed = true
  if (observer) { observer.disconnect(); observer = null }
  if (chart) { chart.dispose(); chart = null }
})
</script>
