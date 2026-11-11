<template>
  <div
    class="min-h-screen bg-gray-50 dark:bg-gray-900 flex flex-col relative"
    :class="{ 'has-wallpaper': hasWallpaper }"
  >
    <!-- 壁纸层：模式由设置面板控制（横幅/全屏/覆盖/隐藏），无图时自动回落到纸面 -->
    <WallpaperLayer />
    <!-- 阅读进度条：用主题色，但只占 2px，不抢视觉 -->
    <div class="fixed top-0 left-0 z-50 h-0.5 bg-primary-600 transition-all" :style="{ width: progress + '%' }"></div>
    <div class="editorial relative z-10 flex flex-col flex-1">
      <Header />
      <main id="site-main" class="flex-1 px-4">
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
const { mode, image, setImage } = useWallpaper()

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
setImage(siteCfg.value?.bgImage || '')

/** 有图且没被「隐藏壁纸」关掉，才算真正启用壁纸 */
const hasWallpaper = computed(() => !!image.value && mode.value !== 'none')

/**
 * 把「有没有壁纸」写进 <html>，交给 CSS 决定正文上边距（见 main.css 的 #site-main 规则）。
 *
 * 为什么不用 computed 拼 class：上边距必须和 html[data-wallpaper] 在同一次绘制里生效，
 * 否则会先按 280px 画一帧、hydration 后再塌回去。htmlAttrs 是服务端就能渲染的，
 * 客户端又跟着 image 变化自动更新，正好满足这个要求。
 */
useHead({
  htmlAttrs: {
    'data-has-wallpaper': () => (hasWallpaper.value ? '1' : '0')
  }
})

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  if (rafId) cancelAnimationFrame(rafId)
})

function scrollToTop() { window.scrollTo({ top: 0, behavior: 'smooth' }) }
</script>
