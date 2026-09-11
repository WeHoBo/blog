<template>
  <nav v-if="items.length > 0" class="toc text-sm">
    <h4 class="font-semibold text-gray-900 dark:text-gray-100 mb-3 px-2">📑 目录</h4>
    <ul class="space-y-1" ref="tocList">
      <li v-for="item in items" :key="item.id" :class="indentClass(item.level)">
        <a :href="'#' + item.id" @click.prevent="scrollToHeading(item.id)"
          class="toc-item block py-1.5 px-3 leading-snug rounded-lg transition-all duration-300"
          :class="activeId === item.id ? 'toc-active' : 'text-gray-500 dark:text-gray-400 hover:text-primary-600 dark:hover:text-primary-400 hover:bg-gray-100 dark:hover:bg-gray-800'">
          <span class="toc-dot"></span>
          {{ item.text }}
        </a>
      </li>
    </ul>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ content: string }>()

/**
 * 缩进用显式映射，不能写成 `'pl-' + (level - 1) * 3`：
 * Tailwind 在构建期扫描源码字面量，运行时拼接出的 `pl-6` 不会被生成，导致 h3 完全不缩进。
 */
const INDENT_CLASS: Record<number, string> = { 1: 'pl-0', 2: 'pl-3', 3: 'pl-6', 4: 'pl-9' }
function indentClass(level: number) {
  return INDENT_CLASS[level] ?? 'pl-0'
}

const items = computed(() => {
  if (!props.content) return []
  const headings: { id: string; text: string; level: number }[] = []
  const lines = props.content.split('\n')
  for (const line of lines) {
    const match = line.match(/^(#{1,4})\s+(.+)/)
    if (match) {
      const level = match[1].length
      const text = match[2].trim()
      const id = text.toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fff\-]/g, '')
      headings.push({ id, text, level })
    }
  }
  return headings
})

const activeId = ref('')
const tocList = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let rafId = 0

function updateActive() {
  let current = items.value[0]?.id || ''
  for (const item of items.value) {
    const el = document.getElementById(item.id)
    if (el && el.getBoundingClientRect().top <= 120) {
      current = item.id
    }
  }
  activeId.value = current
}

function onScroll() {
  // rAF 节流：避免长文滚动时每个滚动事件都触发一次全量 getBoundingClientRect
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    rafId = 0
    updateActive()
  })
}

onMounted(() => {
  // IntersectionObserver 兜底
  observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) activeId.value = entry.target.id
    }
  }, { rootMargin: '-80px 0px -80% 0px' })
  items.value.forEach(h => {
    const el = document.getElementById(h.id)
    if (el) observer!.observe(el)
  })
  // 实时滚动跟随
  window.addEventListener('scroll', onScroll, { passive: true })
  updateActive()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (observer) observer.disconnect()
  if (rafId) cancelAnimationFrame(rafId)
})

// 目录跟随：高亮项超出可视区时自动平滑滚动
watch(activeId, () => {
  nextTick(() => {
    const el = tocList.value?.querySelector('.toc-active')
    if (el) el.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  })
})

function scrollToHeading(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<style scoped>
.toc-item {
  position: relative;
}
/* 圆角竖条：跟随主题色变量，换主题色时目录高亮同步变化 */
.toc-dot {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%) scaleY(0);
  width: 3px;
  height: 16px;
  border-radius: 9999px;
  background: var(--primary);
  transition: transform 0.3s ease;
}
.toc-active {
  color: var(--primary-dark) !important;
  font-weight: 600;
  background: hsla(var(--ph), var(--ps), var(--pl), 0.08);
}
.dark .toc-active {
  color: var(--primary) !important;
  background: hsla(var(--ph), var(--ps), var(--pl), 0.15);
}
.toc-active .toc-dot {
  transform: translateY(-50%) scaleY(1);
}
</style>
