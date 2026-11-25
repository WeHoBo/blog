<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex flex-col relative"
    :class="{ 'edition-shell edition-admin': reference, 'edition-side-shell': rail }">
    <WallpaperLayer v-if="reference && visible && mode !== 'banner'" />
    <div v-if="!reference && bgImage" class="fixed inset-0 z-0 bg-cover bg-center bg-no-repeat opacity-30 dark:opacity-20" :style="{ backgroundImage: `url(${bgImage})` }"></div>
    <StarryBackground v-else-if="!reference" />
    <!-- Reading progress bar -->
    <div class="fixed top-0 left-0 z-50 h-0.5 bg-gradient-to-r from-primary-500 to-primary-500 transition-all" :style="{ width: progress + '%' }"></div>
    <!-- 顶栏也 sticky：后台页面（文章列表、日志）都很长，滚到中部时
         「现在是谁登录的 / 退出」不该消失。侧栏的 top 值要和这里的 56px 对齐。 -->
    <Header v-if="reference" admin />
    <header v-else class="sticky top-0 z-30 bg-white/80 dark:bg-gray-800/80 backdrop-blur-md shadow-sm border-b dark:border-gray-700">
      <div class="container mx-auto px-4 max-w-7xl flex items-center gap-2.5 h-14">
        <NuxtLink to="/" class="flex items-center gap-2 shrink-0" aria-label="回到站点首页">
          <img src="/logo.svg" alt="" width="28" height="28" class="w-7 h-7 rounded-lg object-contain p-0.5 ring-1 ring-gray-300/70 dark:ring-gray-600"
            style="background: var(--page-bg, #F7F2E8)" />
          <span class="font-bold text-gray-900 dark:text-gray-100">博观</span>
        </NuxtLink>
        <span class="text-[11px] px-2 py-0.5 rounded-full bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-400 shrink-0">后台</span>

        <div class="flex-1"></div>

        <div class="flex items-center gap-3 text-sm shrink-0">
          <span class="text-gray-500 hidden sm:inline">{{ authStore.user?.nickname }}</span>
          <button @click="authStore.logout" class="text-gray-400 hover:text-red-500 transition">退出</button>
        </div>
      </div>

      <!-- 小屏（< lg）没有侧栏，导航退回到「胶囊 + 折行」的老形态。
           仍然不能用 overflow-x-auto + 隐藏滚动条：栏目变多之后最后一个会被
           推出可视区，用户根本不知道还有这一项 —— 宁可多占一行也不能让入口消失。
           断点用 lg 而不是 md：768–1023px 这一段放侧栏的话，
           正文只剩 792px，宽表格（920px）会被压出横向滚动条。 -->
      <nav class="lg:hidden container mx-auto px-4 pb-2 max-w-7xl flex flex-wrap items-center gap-0.5">
        <NuxtLink v-for="m in menus" :key="m.to" :to="m.to"
          class="px-2.5 py-1.5 rounded-full text-sm whitespace-nowrap transition"
          :class="isActive(m.to)
            ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900 font-medium'
            : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10'">{{ m.label }}</NuxtLink>
      </nav>
    </header>

    <div class="admin-workspace relative z-10 flex-1 container mx-auto px-4 py-6 max-w-7xl">
      <details v-if="reference" ref="mobileAdminNav" class="edition-admin-mobile lg:hidden">
        <summary>后台导航 <span>{{ menus.find(m => m.to === activeTo)?.label }}</span></summary>
        <nav aria-label="后台导航" class="flex flex-wrap gap-2">
          <NuxtLink v-for="m in menus" :key="m.to" :to="m.to" :aria-current="isActive(m.to) ? 'page' : undefined">{{ m.label }}</NuxtLink>
        </nav>
      </details>
      <div class="flex items-start gap-6">

        <!-- 左侧栏（lg 及以上）。12 个入口竖排，比横着排一长条更容易扫；
             分组标签只是视觉分隔，不参与权限判断。
             sticky 的 top 与顶栏 56px + 16px 间距对齐，滚到底也还在；
             自身限高可滚动：条目再多也够得着。 -->
        <aside class="admin-rail hidden lg:block w-44 shrink-0 sticky top-[4.5rem]">
          <nav class="rounded-xl bg-white/70 dark:bg-white/5 backdrop-blur border border-gray-200 dark:border-gray-700 p-1.5 max-h-[calc(100vh-5.5rem)] overflow-y-auto">
            <NuxtLink v-if="rail" to="/" class="edition-wordmark"><img src="/logo.svg" alt="" width="34" height="34" /><strong>{{ siteConfig?.siteName || '博观' }}</strong></NuxtLink>
            <template v-for="(g, gi) in menuGroups" :key="g.label">
              <p class="px-2.5 pb-1.5 text-[11px] tracking-wider text-gray-400 dark:text-gray-500 select-none"
                 :class="gi === 0 ? 'pt-1.5' : 'pt-3 mt-2 border-t border-gray-200/80 dark:border-gray-700'">{{ g.label }}</p>
              <NuxtLink v-for="m in g.items" :key="m.to" :to="m.to"
                class="block px-2.5 py-1.5 rounded-lg text-sm transition" :aria-current="isActive(m.to) ? 'page' : undefined"
                :class="isActive(m.to)
                  ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900 font-medium'
                  : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10'">{{ m.label }}</NuxtLink>
            </template>
          </nav>
        </aside>

        <!-- min-w-0 不能省：里面的表格有 min-w-[920px]，不加的话 flex 子项会被内容撑破，
             侧栏和正文一起横向溢出整个页面。 -->
        <main class="flex-1 min-w-0">
          <slot />
        </main>
      </div>
    </div>

    <button @click="scrollToTop" v-show="showTopBtn" class="fixed bottom-6 right-6 z-50 w-10 h-10 rounded-full bg-primary-600 text-white shadow-lg hover:bg-primary-700 transition flex items-center justify-center" title="返回顶部" aria-label="返回顶部">
      <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="M12 19V5"/><path d="m5 12 7-7 7 7"/></svg>
    </button>
    <AppToast />
    <AppConfirm />
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin' })
const authStore = useAuthStore()
const route = useRoute()
const { current, reference, rail } = useReferenceShell()
const { visible, mode, setImage } = useWallpaper()
const mobileAdminNav = ref<HTMLDetailsElement | null>(null)
watch(() => route.fullPath, () => { if (mobileAdminNav.value) mobileAdminNav.value.open = false })
useHead({ htmlAttrs: { 'data-skin': () => reference.value ? current.value.id : undefined } })

interface AdminMenu {
  to: string
  label: string
  /** 侧栏分组标题。只是视觉分隔，不参与权限判断 */
  group: '内容' | '权限' | '系统'
  /** 仅超管可见（后端对应接口也是 hasRole('super')） */
  superOnly?: boolean
}

/**
 * 后台导航项集中在这里，模板里只做循环。
 * 之前是 11 个写死的 `<NuxtLink>`，加一个后台页面要改两处（这里 + 页面本身），
 * 还全都长得一样、看不出「我现在在哪」—— 这也是改成「当前页墨色实心块」的原因。
 *
 * `group` 只影响侧栏里的分组标题，删掉某一组时自动隐藏（`menuGroups` 里 filter 掉了空组）。
 * `superOnly: true` = 只有超级管理员能看到 / 能进（后端对应接口也是 hasRole('super')）。
 * 这里只是把入口藏起来，别指望它当权限用。
 */
const allMenus: AdminMenu[] = [
  { to: '/admin/articles', label: '文章', group: '内容' },
  { to: '/admin/articles/trash', label: '回收站', group: '内容' },
  { to: '/admin/categories', label: '分类', group: '内容' },
  { to: '/admin/tags', label: '标签', group: '内容' },
  { to: '/admin/comments', label: '评论', group: '内容' },
  { to: '/admin/links', label: '友链', group: '内容' },
  { to: '/admin/users', label: '用户', group: '权限', superOnly: true },
  { to: '/admin/apply', label: '申请设置', group: '权限', superOnly: true },
  { to: '/admin/logs', label: '日志', group: '系统' },
  { to: '/admin/ai/models', label: 'AI 模型', group: '系统', superOnly: true },
  { to: '/admin/ai/usage', label: 'AI 用量', group: '系统' },
  { to: '/admin/settings', label: '设置', group: '系统' }
]

const GROUP_ORDER: AdminMenu['group'][] = ['内容', '权限', '系统']

/**
 * `mounted` 这道开关是为了不触发水合不一致：
 * SSR 时读不到 localStorage，`isSuper` 恒为 false；而客户端 store 一创建就已经拿到用户，
 * 于是首帧就成了「服务端 9 项、客户端 11 项」。挂载后再放开，两边首帧保持一致。
 */
const mounted = ref(false)
const menus = computed(() => allMenus.filter(m => !m.superOnly || (mounted.value && authStore.isSuper)))

/** 按 GROUP_ORDER 分组；空组直接丢掉，免得侧栏出现一个只有标题没有条目的「权限」 */
const menuGroups = computed(() =>
  GROUP_ORDER.map(label => ({
    label,
    items: menus.value.filter(m => m.group === label)
  })).filter(g => g.items.length > 0)
)

/**
 * 高亮判定：只看**最长**的那条匹配。
 * 不能只用 startsWith —— 「/admin/articles」会把「/admin/articles/trash」也点亮，
 * 于是回收站里永远有两个亮着的项。取最长匹配就自然解决了。
 */
const activeTo = computed(() => {
  const path = route.path.replace(/\/+$/, '')
  let best = ''
  for (const m of menus.value) {
    if ((path === m.to || path.startsWith(m.to + '/')) && m.to.length > best.length) {
      best = m.to
    }
  }
  return best
})
const isActive = (to: string) => activeTo.value === to

const progress = ref(0)
const showTopBtn = ref(false)
const { data: siteConfig } = await useSiteConfig()
const bgImage = computed(() => siteConfig.value?.bgImage || '')
watch([reference, bgImage], ([active, image]) => { if (active) setImage(image) }, { immediate: true })

let rafId = 0

function onScroll() {
  // rAF 节流，避免高频滚动事件直接触发重渲染
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    rafId = 0
    const scrollTop = window.scrollY
    const docHeight = document.documentElement.scrollHeight - window.innerHeight
    progress.value = docHeight > 0 ? Math.round((scrollTop / docHeight) * 100) : 0
    showTopBtn.value = scrollTop > 500
  })
}

onMounted(() => {
  mounted.value = true
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (rafId) cancelAnimationFrame(rafId)
})

function scrollToTop() { window.scrollTo({ top: 0, behavior: 'smooth' }) }
</script>
