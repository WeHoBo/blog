<template>
  <nav v-if="items.length > 0" class="toc text-sm">
    <!-- hideTitle：手机端折叠面板的 summary 已经写了「本文目录 · N 节」，
         里面再出现一遍标题就是同一句话说两次 -->
    <div v-if="!hideTitle" class="flex items-center justify-between gap-2 mb-3 pl-2 pr-1">
      <h4 class="flex items-center gap-1.5 font-semibold text-gray-900 dark:text-gray-100">
        <!-- 📑 换成线条图标：emoji 在 Windows 上是彩色方块，和旁边墨色标题不是一套视觉语言 -->
        <svg class="w-4 h-4 text-gray-400" fill="none" stroke="currentColor" stroke-width="1.6"
          stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4 5.5h6l1.6 2H20a1 1 0 0 1 1 1V18a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1Z" />
          <path d="M3 10.5h18" />
        </svg>
        目录
      </h4>
      <!-- 收起按钮：和标题同一行，不额外占一行高度 -->
      <button v-if="collapsible" type="button" @click="$emit('collapse')"
        class="p-1 rounded-md text-gray-400 hover:text-gray-900 dark:hover:text-gray-100 hover:bg-gray-100 dark:hover:bg-gray-700 transition"
        title="收起目录" aria-label="收起目录">
        <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
          <rect x="3" y="4" width="18" height="16" rx="2.5" />
          <path d="M9 4v16" stroke-linecap="round" />
          <path d="M14.5 9.5L12 12l2.5 2.5" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </div>
    <ul class="space-y-1" ref="tocList">
      <li v-for="item in items" :key="item.id" :class="indentClass(item.depth)">
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
import { extractHeadings } from '~/utils/markdownCore'

const props = defineProps<{ content: string; collapsible?: boolean; hideTitle?: boolean; expanded?: boolean }>()
defineEmits<{ (e: 'collapse'): void }>()

/**
 * 缩进用显式映射，不能写成 `'pl-' + depth * 3`：
 * Tailwind 在构建期扫描源码字面量，运行时拼接出的 `pl-6` 不会被生成，导致 h3 完全不缩进。
 * 下标是「归一化后的层级」：文章从 ## 写起时首个标题就是 0 级，不会整份目录多缩进一格。
 */
const INDENT_CLASS = ['pl-0', 'pl-3', 'pl-6', 'pl-9']
function indentClass(depth: number) {
  return INDENT_CLASS[depth] ?? 'pl-9'
}

/**
 * 标题与 id 全部来自 utils/markdownCore 的 extractHeadings —— 与正文渲染共用同一套规则。
 * 之前的实现是用正则扫原始 Markdown：
 *   - 代码块里的 `# 注释` 会被当成标题；
 *   - 同名小节生成同样的 id，两条目录都跳到第一个；
 *   - 层级按绝对 `#` 数量算，从 ## 写起时整体缩进错位。
 */
const items = computed(() => extractHeadings(props.content))

const activeId = ref('')
const tocList = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let media: MediaQueryList | null = null
let elements: HTMLElement[] = []

function updateActive() {
  let low = 0
  let high = elements.length
  while (low < high) {
    const mid = Math.floor((low + high) / 2)
    if (elements[mid].getBoundingClientRect().top <= 121) low = mid + 1
    else high = mid
  }
  activeId.value = elements[Math.max(0, low - 1)]?.id || ''
}

function subscribe() {
  observer?.disconnect()
  window.removeEventListener('scrollend', updateActive)
  if (!media?.matches || (props.hideTitle && !props.expanded)) return
  elements = items.value.map(item => document.getElementById(item.id)).filter((el): el is HTMLElement => !!el)
  if (!elements.length) return
  observer = new IntersectionObserver(updateActive, { rootMargin: '-120px 0px 0px 0px' })
  elements.forEach(el => observer!.observe(el))
  window.addEventListener('scrollend', updateActive, { passive: true })
  updateActive()
}

onMounted(() => {
  media = window.matchMedia(props.hideTitle ? '(max-width: 1023px)' : '(min-width: 1024px)')
  media.addEventListener('change', subscribe)
  subscribe()
})
watch([items, () => props.expanded], () => { if (media) subscribe() }, { flush: 'post' })

onBeforeUnmount(() => {
  observer?.disconnect()
  media?.removeEventListener('change', subscribe)
  window.removeEventListener('scrollend', updateActive)
})

function motion(): ScrollBehavior {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'
}

watch(activeId, () => {
  if (!props.collapsible || !media?.matches) return
  const scroller = tocList.value?.closest<HTMLElement>('[data-toc-scroll]')
  const active = tocList.value?.querySelector('.toc-active')
  if (!scroller || !active) return
  const bounds = scroller.getBoundingClientRect()
  const rect = active.getBoundingClientRect()
  const delta = rect.top < bounds.top ? rect.top - bounds.top : Math.max(0, rect.bottom - bounds.bottom)
  if (delta) scroller.scrollBy({ top: delta, behavior: motion() })
}, { flush: 'post' })

function scrollToHeading(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: motion(), block: 'start' })
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
