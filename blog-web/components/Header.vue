<template>
  <header
    class="fixed top-0 inset-x-0 z-50 transition-all duration-300 border-b backdrop-blur-xl bg-white/80 dark:bg-gray-900/80 dark:border-gray-800 rounded-b-2xl shadow-sm"
    :class="hidden ? '-translate-y-full' : 'translate-y-0'"
  >
    <div class="max-w-7xl mx-auto px-4 h-16 flex items-center justify-between gap-3">
      <!-- Logo -->
      <NuxtLink to="/" class="flex items-center gap-2 flex-shrink-0 mr-2 group" style="perspective:600px">
        <div class="relative w-9 h-9 transition-transform duration-500" style="transform-style:preserve-3d" :class="{'rotate-y-180': logoFlipped}" @mouseenter="logoFlipped = true" @mouseleave="logoFlipped = false">
          <div class="absolute inset-0 flex items-center justify-center rounded-lg bg-primary-600 text-white font-bold text-sm" style="backface-visibility:hidden">B</div>
          <div class="absolute inset-0 flex items-center justify-center rounded-lg bg-primary-600 text-white font-bold text-sm" style="backface-visibility:hidden;transform:rotateY(180deg)">&lt;/&gt;</div>
        </div>
        <span class="font-extrabold text-gray-900 dark:text-white tracking-tight text-lg sm:text-xl">好啵博客</span>
      </NuxtLink>

      <!-- Desktop Nav -->
      <nav class="hidden lg:flex items-center gap-1 bg-white/40 dark:bg-white/5 rounded-full px-1 py-1 backdrop-blur-sm">
        <NuxtLink to="/" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-primary-100/70 dark:hover:bg-primary-500/20 transition">首页</NuxtLink>
        <NuxtLink to="/about" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-primary-100/70 dark:hover:bg-primary-500/20 transition">关于</NuxtLink>
        <NuxtLink to="/archive" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-primary-100/70 dark:hover:bg-primary-500/20 transition">归档</NuxtLink>
        <NuxtLink to="/categories" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-primary-100/70 dark:hover:bg-primary-500/20 transition">分类</NuxtLink>
        <NuxtLink to="/tags" class="px-3 py-1.5 rounded-full text-sm text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-primary-100/70 dark:hover:bg-primary-500/20 transition">标签</NuxtLink>
        <button @click="settingsOpen = true" class="px-2 py-1.5 rounded-full text-sm text-gray-500 hover:bg-primary-100/70 dark:hover:bg-primary-500/20 transition" title="主题设置">
          <svg class="w-4 h-4 inline-block" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 2a9 9 0 000 18h2.5a2 2 0 002-2.5A2 2 0 0118 15a2 2 0 012-1.5A2 2 0 0118.5 11H17a5 5 0 01-5-5V4.5A1.5 1.5 0 0012 2z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 15a2 2 0 104 0 2 2 0 00-4 0zM18 6a2 2 0 11-4 0 2 2 0 014 0z"/></svg>
        </button>
      </nav>

      <!-- Search -->
      <div class="flex-1 max-w-md hidden sm:block mx-2">
        <div class="relative">
          <input ref="searchInput" v-model="searchText" @keyup.enter="doSearch" placeholder="搜索文章、标签、分类 (Ctrl+K)" class="w-full pl-9 pr-10 py-1.5 rounded-full border-2 border-primary-100 bg-white/70 dark:bg-gray-800/70 dark:border-gray-700 dark:text-gray-100 text-sm focus:border-primary-400 dark:focus:border-primary-500 focus:outline-none transition backdrop-blur-sm" />
          <span class="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-4.35-4.35M17 10.5a6.5 6.5 0 11-13 0 6.5 6.5 0 0113 0z"/></svg>
          </span>
          <span class="absolute right-3 top-1/2 -translate-y-1/2 text-[10px] px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-700 text-gray-400 border dark:border-gray-600 hidden sm:block">Ctrl K</span>
        </div>
      </div>

      <!-- Right actions -->
      <div class="flex items-center gap-2 flex-shrink-0">
        <template v-if="authStore.isLoggedIn">
          <NuxtLink v-if="authStore.isAdmin" to="/admin/articles/create" class="hidden sm:inline-flex items-center gap-1 px-4 py-1.5 rounded-full bg-primary-600 text-white text-xs sm:text-sm font-medium hover:bg-primary-700 hover:shadow-lg hover:-translate-y-0.5 transition-all duration-200">✏️ 写文章</NuxtLink>
          <!-- User dropdown -->
          <div class="relative">
            <button @click="userOpen = !userOpen" class="hidden sm:flex items-center gap-1.5 pl-1 pr-2 py-1 rounded-full hover:bg-gray-100 dark:hover:bg-gray-700 transition">
              <img v-if="authStore.user?.avatar" :src="authStore.user.avatar" alt="avatar" class="w-6 h-6 rounded-full object-cover" />
              <span v-else class="w-6 h-6 rounded-full bg-primary-600 text-white flex items-center justify-center text-xs font-bold">{{ authStore.user?.nickname?.[0] || 'U' }}</span>
              <span class="text-sm text-gray-500 dark:text-gray-400 max-w-[6rem] truncate">{{ authStore.user?.nickname }}</span>
              <svg class="w-3 h-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
            </button>
            <div v-if="userOpen" class="absolute right-0 mt-2 w-44 bg-white dark:bg-gray-800 rounded-xl shadow-lg border border-gray-100 dark:border-gray-700 py-1.5 z-50">
              <NuxtLink to="/profile" @click="userOpen = false" class="block px-4 py-2 text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">👤 个人主页</NuxtLink>
              <NuxtLink v-if="authStore.isAdmin" to="/admin/articles" @click="userOpen = false" class="block px-4 py-2 text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">📋 管理后台</NuxtLink>
              <button @click="userOpen = false; settingsOpen = true" class="w-full text-left px-4 py-2 text-sm text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 transition">⚙️ 设置</button>
              <div class="border-t border-gray-100 dark:border-gray-700 my-1"></div>
              <button @click="handleLogout" class="w-full text-left px-4 py-2 text-sm text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 transition">🚪 退出登录</button>
            </div>
          </div>
          <button @click="authStore.logout" class="sm:hidden text-sm text-gray-400 hover:text-red-500 transition">退出</button>
        </template>
        <template v-else>
          <NuxtLink to="/login" class="px-4 py-1.5 rounded-full bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 transition">登录</NuxtLink>
        </template>
        <!-- Settings gear (mobile) -->
        <button @click="settingsOpen = true" class="lg:hidden p-1.5 rounded-md text-gray-500 hover:bg-primary-100/70 dark:hover:bg-primary-500/20 transition" title="主题设置">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 2a9 9 0 000 18h2.5a2 2 0 002-2.5A2 2 0 0118 15a2 2 0 012-1.5A2 2 0 0118.5 11H17a5 5 0 01-5-5V4.5A1.5 1.5 0 0012 2z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 15a2 2 0 104 0 2 2 0 00-4 0zM18 6a2 2 0 11-4 0 2 2 0 014 0z"/></svg>
        </button>
        <!-- Hamburger -->
        <button @click="mobileOpen = !mobileOpen" class="lg:hidden p-1.5 rounded-md text-gray-500 hover:bg-primary-100/70 dark:hover:bg-primary-500/20 transition text-xl">
          {{ mobileOpen ? '✕' : '☰' }}
        </button>
      </div>
    </div>

    <!-- Mobile menu -->
    <div v-if="mobileOpen" class="lg:hidden border-t dark:border-gray-700 py-3 space-y-1 pb-4 px-4">
      <NuxtLink to="/" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-primary-100/70 dark:hover:bg-primary-500/20">首页</NuxtLink>
      <NuxtLink v-if="authStore.isAdmin" to="/admin/articles" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-primary-100/70 dark:hover:bg-primary-500/20">📋 管理后台</NuxtLink>
      <NuxtLink to="/about" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-primary-100/70 dark:hover:bg-primary-500/20">关于</NuxtLink>
      <NuxtLink to="/archive" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-primary-100/70 dark:hover:bg-primary-500/20">归档</NuxtLink>
      <NuxtLink to="/categories" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-primary-100/70 dark:hover:bg-primary-500/20">分类</NuxtLink>
      <NuxtLink to="/tags" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-primary-100/70 dark:hover:bg-primary-500/20">标签</NuxtLink>
      <NuxtLink v-if="authStore.isLoggedIn" to="/profile" @click="mobileOpen = false" class="block px-3 py-2 rounded-md text-sm text-gray-600 dark:text-gray-300 hover:bg-primary-100/70 dark:hover:bg-primary-500/20">👤 个人主页</NuxtLink>
      <div class="px-3 py-2">
        <input v-model="searchText" @keyup.enter="doSearch" placeholder="搜索文章" class="w-full px-3 py-1.5 rounded-full border text-sm dark:bg-gray-800 dark:border-gray-600 focus:outline-none" />
      </div>
    </div>

    <!-- Settings Modal -->
    <Teleport to="body">
      <Transition name="modal">
        <div v-if="settingsOpen" class="fixed inset-0 z-[60] flex items-center justify-center p-4" @click.self="settingsOpen = false">
          <div class="absolute inset-0 bg-black/20" @click="settingsOpen = false"></div>
          <div class="relative w-full max-w-sm rounded-2xl shadow-2xl border border-primary-100 dark:border-gray-700 bg-white/95 dark:bg-gray-900/95 backdrop-blur-xl p-6">
            <div class="flex items-center justify-between mb-5">
              <h3 class="text-lg font-bold text-gray-900 dark:text-white">⚙️ 设置</h3>
              <button @click="settingsOpen = false" class="w-8 h-8 rounded-full flex items-center justify-center text-gray-400 hover:bg-primary-100/70 dark:hover:bg-primary-500/20 hover:text-gray-600 dark:hover:text-gray-200 transition text-xl">✕</button>
            </div>

            <div class="space-y-5">
              <!-- 主题颜色 -->
              <div>
                <div class="flex items-center justify-between mb-1.5">
                  <span class="text-sm font-medium text-gray-700 dark:text-gray-200">🎨 主题颜色</span>
                  <span class="text-xs text-gray-400">{{ accentHue }}°</span>
                </div>
                <input type="range" min="0" max="360" :value="accentHue" @input="setAccentHue" class="w-full h-3 accent-primary-600 cursor-pointer" />
              </div>

              <!-- 模块背景颜色 -->
              <div>
                <div class="flex items-center justify-between mb-1.5">
                  <span class="text-sm font-medium text-gray-700 dark:text-gray-200">☕ 模块背景颜色</span>
                  <span class="text-xs text-gray-400">{{ bgWarmth }}</span>
                </div>
                <input type="range" min="0" max="100" :value="bgWarmth" @input="setBgWarmth" class="w-full h-3 accent-amber-600 cursor-pointer" />
              </div>

              <!-- 夜间模式 -->
              <div class="flex items-center justify-between py-2">
                <span class="text-sm font-medium text-gray-700 dark:text-gray-200">🌙 夜间模式</span>
                <button @click="toggleColor" class="relative w-12 h-6 rounded-full transition-colors duration-200" :class="colorMode.preference === 'dark' ? 'bg-primary-600' : 'bg-gray-300'" role="switch" :aria-checked="colorMode.preference === 'dark'">
                  <span class="absolute top-0.5 left-0.5 w-5 h-5 bg-white rounded-full shadow transition-transform duration-200" :class="colorMode.preference === 'dark' ? 'translate-x-6' : ''"></span>
                </button>
              </div>

              <!-- 文章布局 -->
              <div class="flex items-center justify-between py-2">
                <span class="text-sm font-medium text-gray-700 dark:text-gray-200">📄 文章布局</span>
                <div class="flex items-center gap-1 bg-white/60 dark:bg-white/5 rounded-full p-1 border border-primary-100 dark:border-gray-700">
                  <button @click="setLayout('list')" class="px-3 py-1 rounded-full text-xs transition" :class="layout === 'list' ? 'bg-primary-600 text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700'">列表</button>
                  <button @click="setLayout('grid')" class="px-3 py-1 rounded-full text-xs transition" :class="layout === 'grid' ? 'bg-primary-600 text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700'">网格</button>
                </div>
              </div>

              <!-- 文章排序 -->
              <div class="flex items-center justify-between py-2">
                <span class="text-sm font-medium text-gray-700 dark:text-gray-200">🔀 文章排序</span>
                <div class="flex items-center gap-1 bg-white/60 dark:bg-white/5 rounded-full p-1 border border-primary-100 dark:border-gray-700">
                  <button @click="setSort('time')" class="px-3 py-1 rounded-full text-xs transition" :class="sort === 'time' ? 'bg-primary-600 text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700'">发布时间</button>
                  <button @click="setSort('title')" class="px-3 py-1 rounded-full text-xs transition" :class="sort === 'title' ? 'bg-primary-600 text-white' : 'text-gray-500 dark:text-gray-400 hover:text-gray-700'">标题首字母</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </header>
</template>

<script setup lang="ts">
const authStore = useAuthStore()
const colorMode = useColorMode()
const router = useRouter()
const searchText = ref('')
const searchInput = ref<HTMLInputElement | null>(null)
const mobileOpen = ref(false)
const logoFlipped = ref(false)
const settingsOpen = ref(false)
const userOpen = ref(false)

const hidden = ref(false)
let lastScrollY = 0
let scrollRafId = 0

function handleLogout() {
  userOpen.value = false
  authStore.logout()
  router.push('/')
}

// Ctrl+K / Cmd+K 聚焦搜索框（独立命名，便于卸载时移除）
function handleHotkey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    searchInput.value?.focus()
  }
}

function handleScroll() {
  // rAF 节流：滚动事件频率极高，直接写响应式数据会导致持续的整页重渲染
  if (scrollRafId) return
  scrollRafId = requestAnimationFrame(() => {
    scrollRafId = 0
    const y = window.scrollY
    if (y > lastScrollY && y > 60) {
      hidden.value = true
    } else if (y < lastScrollY) {
      hidden.value = false
    }
    lastScrollY = y
  })
}

onMounted(() => {
  lastScrollY = window.scrollY
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('keydown', handleHotkey)
  const saved = localStorage.getItem('accent-hue')
  if (saved) { accentHue.value = Number(saved); applyHue(Number(saved)) }
  const w = localStorage.getItem('bg-warmth')
  if (w) { bgWarmth.value = Number(w); applyWarmth(Number(w)) }
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('keydown', handleHotkey)
  if (scrollRafId) cancelAnimationFrame(scrollRafId)
})

function toggleColor() {
  colorMode.preference = colorMode.preference === 'dark' ? 'light' : 'dark'
}

const accentHue = ref(217)
function setAccentHue(e: Event) {
  const val = Number((e.target as HTMLInputElement).value)
  accentHue.value = val
  applyHue(val)
}

function applyHue(h: number) {
  if (!process.client) return
  document.documentElement.style.setProperty('--ph', String(h))
  localStorage.setItem('accent-hue', String(h))
}

const bgWarmth = ref(0)
function setBgWarmth(e: Event) {
  const val = Number((e.target as HTMLInputElement).value)
  bgWarmth.value = val
  applyWarmth(val)
}
function applyWarmth(w: number) {
  if (!process.client) return
  localStorage.setItem('bg-warmth', String(w))
  const id = 'warmth-style'
  let el = document.getElementById(id)
  if (!el) { el = document.createElement('style'); el.id = id; document.head.appendChild(el) }
  const white = `hsl(40,${20 + w}%,${97 - w * 0.1}%)`
  const gray50 = `hsl(40,${15 + w}%,${95 - w * 0.08}%)`
  const border = `hsl(35,${10 + w}%,${90 - w * 0.15}%)`
  el.textContent = w > 0 ? `
    .bg-white, [class*="bg-white"] { background-color:${white} !important }
    .bg-gray-50 { background-color:${gray50} !important }
    .border-gray-200, .border-gray-100, .border-gray-300 { border-color:${border} !important }
    .dark .bg-white, .dark [class*="bg-white/"] { background-color:${white} !important }
  ` : ''
}

const { viewMode: layout, setViewMode: setLayout, sortMode: sort, setSortMode: setSort } = useLayout()

function doSearch() {
  mobileOpen.value = false
  userOpen.value = false
  if (searchText.value.trim()) {
    router.push(`/?keyword=${encodeURIComponent(searchText.value.trim())}`)
  }
}
</script>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-enter-active .relative,
.modal-leave-active .relative {
  transition: transform 0.2s ease;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
.modal-enter-from .relative,
.modal-leave-to .relative {
  transform: scale(0.95);
}
</style>
