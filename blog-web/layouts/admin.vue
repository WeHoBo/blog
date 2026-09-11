<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex flex-col relative">
    <div v-if="bgImage" class="fixed inset-0 z-0 bg-cover bg-center bg-no-repeat opacity-30 dark:opacity-20" :style="{ backgroundImage: `url(${bgImage})` }"></div>
    <StarryBackground v-else />
    <FestivalEffect />
    <!-- Reading progress bar -->
    <div class="fixed top-0 left-0 z-50 h-0.5 bg-gradient-to-r from-primary-500 to-primary-500 transition-all" :style="{ width: progress + '%' }"></div>
    <header class="relative z-10 bg-white/80 dark:bg-gray-800/80 backdrop-blur-md shadow-sm border-b dark:border-gray-700">
      <div class="container mx-auto px-4 max-w-5xl flex items-center gap-3 h-auto min-h-14 py-2 flex-wrap">
        <NuxtLink to="/" class="font-bold text-primary-600 dark:text-primary-400 shrink-0">好啵博客</NuxtLink>
        <!-- 窄屏横向滚动，避免 7 个导航项溢出被裁掉 -->
        <nav class="flex gap-4 text-sm overflow-x-auto whitespace-nowrap flex-1 min-w-0 py-1 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden">
          <NuxtLink to="/admin/articles" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition shrink-0">文章</NuxtLink>
          <NuxtLink to="/admin/articles/trash" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition shrink-0">回收站</NuxtLink>
          <NuxtLink to="/admin/categories" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition shrink-0">分类</NuxtLink>
          <NuxtLink to="/admin/tags" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition shrink-0">标签</NuxtLink>
          <NuxtLink to="/admin/links" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition shrink-0">友链</NuxtLink>
          <NuxtLink to="/admin/users" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition shrink-0">用户</NuxtLink>
          <NuxtLink to="/admin/logs" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition shrink-0">日志</NuxtLink>
          <NuxtLink to="/admin/settings" class="text-gray-600 dark:text-gray-300 hover:text-primary-600 transition shrink-0">设置</NuxtLink>
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
    <button @click="scrollToTop" v-show="showTopBtn" class="fixed bottom-6 right-6 z-50 w-10 h-10 rounded-full bg-primary-600 text-white shadow-lg hover:bg-primary-700 transition flex items-center justify-center text-lg" title="返回顶部" aria-label="返回顶部">↑</button>
    <AppToast />
    <AppConfirm />
  </div>
</template>

<script setup lang="ts">
definePageMeta({ middleware: 'admin' })
const authStore = useAuthStore()

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
