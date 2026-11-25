<template>
  <div class="min-h-screen flex flex-col relative" :class="{ 'edition-shell': reference, 'edition-side-shell': rail }">
    <WallpaperLayer v-if="visible && (mode !== 'banner' || (homeBanner && currentSkin.layout !== 'platform'))" />
    <!-- 阅读进度条：用主题色，但只占 2px，不抢视觉 -->
    <div class="fixed top-0 left-0 z-50 h-0.5 bg-primary-600 transition-all" :style="{ width: progress + '%' }"></div>
    <div class="editorial relative z-10 flex flex-col flex-1">
      <Header />
      <main id="site-main" class="flex-1 px-4" :class="{ 'home-banner': homeBanner && !reference }">
        <slot />
      </main>
      <Footer />
    </div>
    <ClientOnly>
      <AiFloat />
      <AppToast />
      <AppConfirm />
    </ClientOnly>
    <button @click="scrollToTop" v-show="showTopBtn" class="fixed bottom-24 right-6 z-[60] w-10 h-10 rounded-full bg-gray-900 text-white dark:bg-gray-100 dark:text-gray-900 shadow-paper hover:opacity-90 transition flex items-center justify-center" title="返回顶部" aria-label="返回顶部">
      <svg class="w-5 h-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24"><path d="m5 12 7-7 7 7"/><path d="M12 19V5"/></svg>
    </button>
  </div>
</template>

<script setup lang="ts">
// 站点配置统一走 useSiteConfig（内部用 useAsyncData，与首页共用同一次请求）
const { source, visible, mode, setImage } = useWallpaper()
const route = useRoute()
const homeBanner = computed(() => route.path === '/' && route.query.view !== 'articles' && !route.query.keyword && !route.query.tagId && !route.query.categoryId)

/* ---------------------------------------------------------------
   皮肤（风格）
   ---------------------------------------------------------------
   `data-skin` 走 useHead 写在 <html> 上，**服务端就能渲染**：
   皮肤的纸面底色是纯 CSS（见 main.css），背景图和首页版式都按这个属性走，
   这样 SSR 吐出来的第一帧就是用户选的那套，不会「先暖纸再变薄荷」。

   为什么皮肤不在这里顺手改主题色相：
   `--ph` 是 useThemeColor 用 inline style 写到 <html> 上的，SSR 阶段先按
   nuxt.config 内联脚本从 localStorage 恢复 —— 那才是用户最后一次的选择。
   在这里再写一遍会把它覆盖掉（刷新页面就回不去用户手动调过的色）。
   色相由用户切换风格那一刻同步（Header 的 chooseSkin），这是唯一入口。
   --------------------------------------------------------------- */
const { current: currentSkin, reference, rail } = useReferenceShell()

const progress = ref(0)
const showTopBtn = ref(false)

let rafId = 0

function onScroll() {
  // requestAnimationFrame 节流：滚动期间每帧最多更新一次，
  // 避免高频事件直接触发整页重渲染（layout 包裹全站所有页面）
  if (rafId) return
  rafId = requestAnimationFrame(() => {
    rafId = 0
    const scrollTop = window.scrollY
    const docHeight = document.documentElement.scrollHeight - window.innerHeight
    progress.value = docHeight > 0 ? Math.round((scrollTop / docHeight) * 100) : 0
    showTopBtn.value = scrollTop > 500
  })
}

// 站点配置在 SSR 阶段取好：壁纸属于首屏视觉，晚到会闪一下白。
// 走 composables/useSiteConfig 而不是自己写 useAsyncData：首页横幅也要用同一份数据，
// 固定 key 让两处共用一次请求（详情见那个文件的注释）。
const { data: siteCfg } = await useSiteConfig()
watch(() => siteCfg.value?.bgImage, url => setImage(url || ''), { immediate: true })

// 服务端标记是否存在背景。首绘几何由 head 中提前恢复的 data-wallpaper 决定。
useHead({
  htmlAttrs: {
    'data-has-wallpaper': () => (source.value === 'none' ? '0' : '1'),
    // 皮肤 id 同时是 CSS 钩子（纸面底色、卡片圆角）与首页版式的开关
    'data-skin': () => currentSkin.value.id
  }
})

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (rafId) cancelAnimationFrame(rafId)
})

function scrollToTop() { window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' }) }
</script>
