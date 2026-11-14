<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex flex-col relative">
    <div v-if="bgImage" class="fixed inset-0 z-0 bg-cover bg-center bg-no-repeat opacity-30 dark:opacity-20" :style="{ backgroundImage: `url(${bgImage})` }"></div>
    <StarryBackground v-else />
    <!-- Reading progress bar -->
    <div class="fixed top-0 left-0 z-50 h-0.5 bg-gradient-to-r from-primary-500 to-primary-500 transition-all" :style="{ width: progress + '%' }"></div>
    <header class="relative z-10 bg-white/80 dark:bg-gray-800/80 backdrop-blur-md shadow-sm border-b dark:border-gray-700">
      <div class="container mx-auto px-4 max-w-5xl flex items-center gap-3 h-auto min-h-14 py-2 flex-wrap">
        <NuxtLink to="/" class="flex items-center gap-2 shrink-0" aria-label="回到站点首页">
          <img src="/logo.svg" alt="" width="28" height="28" class="w-7 h-7 rounded-lg object-contain p-0.5 ring-1 ring-gray-300/70 dark:ring-gray-600"
            style="background: var(--page-bg, #F7F2E8)" />
          <span class="font-bold text-gray-900 dark:text-gray-100">博观</span>
        </NuxtLink>
        <span class="text-[11px] px-2 py-0.5 rounded-full bg-gray-100 dark:bg-white/10 text-gray-500 dark:text-gray-400 shrink-0">后台</span>

        <!-- 导航与前台顶栏同一套「胶囊」语言：一个椭圆包住所有栏目，
             当前页用墨色实心块标出。窄屏横向滚动，避免 11 项被裁掉。 -->
        <nav class="flex items-center gap-0.5 bg-white/50 dark:bg-white/5 rounded-full px-1 py-1 border border-gray-200 dark:border-gray-700 flex-1 min-w-0 overflow-x-auto [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <NuxtLink v-for="m in menus" :key="m.to" :to="m.to"
            class="px-3 py-1.5 rounded-full text-sm whitespace-nowrap transition"
            :class="isActive(m.to)
              ? 'bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900 font-medium'
              : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-white/10'">{{ m.label }}</NuxtLink>
        </nav>

        <div class="flex items-center gap-3 text-sm shrink-0">
          <span class="text-gray-500 hidden sm:inline">{{ authStore.user?.nickname }}</span>
          <button @click="authStore.logout" class="text-gray-400 hover:text-red-500 transition">退出</button>
        </div>
      </div>
    </header>
    <main class="relative z-10 flex-1 container mx-auto px-4 py-6 max-w-5xl">
      <slot />
    </main>
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

/**
 * 后台导航项集中在这里，模板里只做循环。
 * 之前是 11 个写死的 `<NuxtLink>`，加一个后台页面要改两处（这里 + 页面本身），
 * 还全都长得一样、看不出「我现在在哪」—— 这也是改成胶囊样式的原因。
 *
 * `superOnly: true` = 只有超级管理员能看到 / 能进（后端对应接口也是 hasRole('super')）。
 * 这里只是把入口藏起来，别指望它当权限用。
 */
const allMenus = [
  { to: '/admin/articles', label: '文章' },
  { to: '/admin/articles/trash', label: '回收站' },
  { to: '/admin/categories', label: '分类' },
  { to: '/admin/tags', label: '标签' },
  { to: '/admin/comments', label: '评论' },
  { to: '/admin/links', label: '友链' },
  { to: '/admin/users', label: '用户', superOnly: true },
  { to: '/admin/logs', label: '日志' },
  { to: '/admin/ai/models', label: 'AI 模型', superOnly: true },
  { to: '/admin/ai/usage', label: 'AI 用量' },
  { to: '/admin/settings', label: '设置' }
]

/**
 * `mounted` 这道开关是为了不触发水合不一致：
 * SSR 时读不到 localStorage，`isSuper` 恒为 false；而客户端 store 一创建就已经拿到用户，
 * 于是首帧就成了「服务端 9 项、客户端 11 项」。挂载后再放开，两边首帧保持一致。
 */
const mounted = ref(false)
const menus = computed(() => allMenus.filter(m => !m.superOnly || (mounted.value && authStore.isSuper)))

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
const bgImage = ref('')

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

onMounted(async () => {
  mounted.value = true
  try {
    const { get } = useApi()
    const res = await get<any>('/site-config/public')
    if (res.code === 200 && res.data) bgImage.value = res.data.bgImage || ''
  } catch {}
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (rafId) cancelAnimationFrame(rafId)
})

function scrollToTop() { window.scrollTo({ top: 0, behavior: 'smooth' }) }
</script>
