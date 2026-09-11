<template>
  <div class="min-h-screen bg-gray-50 dark:bg-gray-900 flex flex-col relative">
    <div v-if="bgImage" class="fixed inset-0 z-0 bg-cover bg-center bg-no-repeat opacity-15 dark:opacity-10" :style="{ backgroundImage: `linear-gradient(rgba(255,255,255,0.5), rgba(255,255,255,0.5)), url(${bgImage})` }"></div>
    <StarryBackground v-else />
    <canvas ref="trailCanvas" class="fixed inset-0 pointer-events-none z-40 opacity-40"></canvas>
    <FestivalEffect />
    <!-- 鼠标跟随光斑：直接用 DOM 样式驱动，不经过响应式数据 -->
    <div ref="glowEl" class="fixed pointer-events-none z-0 opacity-20 dark:opacity-10" style="width:600px;height:600px;border-radius:50%;background:radial-gradient(circle, rgba(59,130,246,0.15) 0%, transparent 70%);transform:translate(-50%,-50%);transition:opacity 0.3s;opacity:0"></div>
    <!-- Reading progress bar -->
    <div class="fixed top-0 left-0 z-50 h-0.5 bg-gradient-to-r from-primary-500 to-primary-500 transition-all" :style="{ width: progress + '%' }"></div>
    <div class="relative z-10 flex flex-col flex-1">
      <Header />
      <main class="flex-1 px-4 pt-16 lg:pt-20">
        <slot />
      </main>
      <Footer />
    </div>
    <ClientOnly>
      <AiFloat />
      <AppToast />
      <AppConfirm />
    </ClientOnly>
    <button @click="scrollToTop" v-show="showTopBtn" class="fixed bottom-24 right-6 z-[60] w-10 h-10 rounded-full bg-primary-600 text-white shadow-lg hover:bg-primary-700 transition flex items-center justify-center text-lg" title="返回顶部">↑</button>
  </div>
</template>

<script setup lang="ts">
const progress = ref(0)
const showTopBtn = ref(false)
const bgImage = ref('')

const trailCanvas = ref<HTMLCanvasElement | null>(null)
const glowEl = ref<HTMLElement | null>(null)

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

function onMouseMove(e: MouseEvent) {
  // 直接写 DOM 样式，不写响应式数据，避免鼠标移动导致全站重渲染
  if (glowEl.value) {
    glowEl.value.style.left = `${e.clientX}px`
    glowEl.value.style.top = `${e.clientY}px`
    glowEl.value.style.opacity = '1'
  }
}

onMounted(async () => {
  try {
    const { get } = useApi()
    const res = await get<any>('/site-config/public')
    if (res.code === 200 && res.data) bgImage.value = res.data.bgImage || ''
  } catch {}
  if (process.client) {
    const canvas = trailCanvas.value
    if (canvas) {
      const ctx = canvas.getContext('2d')
      if (ctx) {
        canvas.width = window.innerWidth; canvas.height = window.innerHeight
        const dots: { x: number; y: number; life: number }[] = []
        window.addEventListener('mousemove', (e) => {
          dots.push({ x: e.clientX, y: e.clientY, life: 1 })
          if (dots.length > 30) dots.shift()
        })
        function animate() {
          ctx!.clearRect(0, 0, canvas!.width, canvas!.height)
          for (let i = 0; i < dots.length; i++) {
            const d = dots[i]; d.life -= 0.02
            if (d.life <= 0) { dots.splice(i, 1); i--; continue }
            ctx!.fillStyle = `hsla(${(Date.now()/10 + i*20) % 360}, 70%, 65%, ${d.life * 0.3})`
            ctx!.beginPath(); ctx!.arc(d.x, d.y, 3 + d.life * 2, 0, Math.PI*2); ctx!.fill()
          }
          requestAnimationFrame(animate)
        }
        animate()
      }
    }
  }
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('mousemove', onMouseMove, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('mousemove', onMouseMove)
  if (rafId) cancelAnimationFrame(rafId)
})

function scrollToTop() { window.scrollTo({ top: 0, behavior: 'smooth' }) }
</script>
