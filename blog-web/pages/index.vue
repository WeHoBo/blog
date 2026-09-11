<template>
  <div class="max-w-7xl mx-auto">
    <!-- 轻量 Hero -->
    <div class="px-4 pt-6 pb-1 lg:px-0">
      <div class="rounded-2xl bg-white/60 dark:bg-gray-800/60 backdrop-blur border border-white/50 dark:border-gray-700/60 px-6 sm:px-10 py-8 relative overflow-hidden">
        <div class="absolute -right-10 -top-10 w-48 h-48 rounded-full opacity-15" style="background: radial-gradient(circle, hsl(var(--ph) var(--ps) 60%) 0%, transparent 70%)"></div>
        <div class="relative">
          <h1 class="text-xl sm:text-2xl font-extrabold text-gray-900 dark:text-gray-100">你好，我是好啵 👋</h1>
          <p class="text-sm text-gray-500 dark:text-gray-400 mt-1.5">记录我的计算机学习与技术实践</p>
          <div class="flex flex-wrap items-center gap-2 mt-3">
            <span v-for="t in ['Java', 'AI', 'Python', 'Linux', 'Docker']" :key="t" class="text-xs px-2.5 py-1 rounded-full theme-chip">{{ t }}</span>
          </div>
          <div class="flex gap-3 mt-4">
            <a href="#articles" class="px-4 py-1.5 rounded-full bg-primary-600 text-white text-xs font-medium hover:bg-primary-700 transition">开始阅读</a>
            <NuxtLink to="/about" class="px-4 py-1.5 rounded-full border border-gray-200 dark:border-gray-600 text-gray-600 dark:text-gray-300 text-xs font-medium hover:bg-gray-50 dark:hover:bg-gray-700 transition">关于我</NuxtLink>
          </div>
        </div>
      </div>
    </div>

    <div id="articles" class="flex gap-6 py-6">
      <!-- Left sidebar: category nav -->
      <aside class="hidden lg:block w-52 flex-shrink-0">
        <div class="sticky top-20 bg-white dark:bg-gray-800 rounded-xl shadow-sm overflow-hidden">
          <div class="px-4 py-3 border-b dark:border-gray-700 flex items-center justify-between">
            <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">文章分类</h3>
          </div>
          <div class="py-1 max-h-[75vh] overflow-y-auto">
            <!-- 全部 -->
            <button @click="currentCategory = undefined; pageNum = 1"
              :class="!currentCategory
                ? 'bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 border-l-2 border-primary-600'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'"
              class="w-full text-left px-4 py-2 text-sm font-medium transition flex items-center gap-2">
              <svg class="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h4l2 2h6a2 2 0 012 2v8a2 2 0 01-2 2H6a2 2 0 01-2-2z"/></svg>
              <span class="flex-1 min-w-0 truncate">全部</span>
            </button>

            <!-- 分类树（一级 + 折叠子级） -->
            <template v-for="cat in categoryTree" :key="cat.id">
              <div>
                <button @click="selectCategory(cat)"
                  :class="catBtnClass(cat)"
                  class="w-full text-left px-3 py-2 text-sm transition flex items-center gap-1.5 border-l-2 border-transparent">
                  <span v-if="cat.children && cat.children.length" @click.stop="toggleExpand(cat.id)"
                    class="w-4 h-4 flex items-center justify-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 flex-shrink-0">
                    <svg class="w-3 h-3 transition-transform duration-200" :class="isExpanded(cat.id) ? 'rotate-90' : ''" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7"/></svg>
                  </span>
                  <svg v-else class="w-3 h-3 text-transparent flex-shrink-0" viewBox="0 0 24 24"></svg>
                  <span v-html="catIcon(cat.name)" class="flex-shrink-0 w-4 h-4"></span>
                  <span class="flex-1 min-w-0 truncate font-medium">{{ cat.name }}</span>
                  <span class="text-xs opacity-50">{{ cat.articleCount || 0 }}</span>
                </button>

                <div v-if="cat.children && cat.children.length && isExpanded(cat.id)" class="pb-1">
                  <button v-for="child in cat.children" :key="child.id" @click="currentCategory = child.id; pageNum = 1"
                    :class="currentCategory === child.id
                      ? 'bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 border-l-2 border-primary-600'
                      : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'"
                    class="w-full text-left pl-9 pr-3 py-1.5 text-sm transition flex items-center gap-2">
                    <span class="w-1 h-1 rounded-full bg-gray-300 dark:bg-gray-600 flex-shrink-0"></span>
                    <span class="flex-1 min-w-0 truncate">{{ child.name }}</span>
                    <span class="text-xs opacity-40">{{ child.articleCount || 0 }}</span>
                  </button>
                </div>
              </div>
            </template>
          </div>
        </div>
      </aside>

      <!-- Center: article feed -->
      <main class="flex-1 min-w-0">
        <!-- 移动端分类入口 -->
        <div class="flex items-center gap-2 mb-3 lg:hidden">
          <button @click="mobileCatOpen = true"
            class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-full bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-600 text-sm text-gray-600 dark:text-gray-300 shadow-sm active:scale-95 transition">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h10"/></svg>
            分类
          </button>
          <span v-if="currentCategory" class="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 text-xs">
            {{ pageTitle }}
            <button @click="clearFilters" class="ml-0.5 hover:text-primary-800">✕</button>
          </span>
        </div>

        <!-- Heading + view toggle -->
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
            <span class="w-1 h-5 bg-primary-600 rounded-full"></span>
            {{ pageTitle }}
          </h2>
          <div class="flex bg-white dark:bg-gray-800 rounded-lg border dark:border-gray-700 p-1">
            <button @click="setViewMode('list')" title="列表视图"
              class="px-2.5 py-1 rounded-md text-sm transition"
              :class="viewMode === 'list' ? 'bg-primary-600 text-white' : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700'">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>
            </button>
            <button @click="setViewMode('grid')" title="网格视图"
              class="px-2.5 py-1 rounded-md text-sm transition"
              :class="viewMode === 'grid' ? 'bg-primary-600 text-white' : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700'">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"/></svg>
            </button>
          </div>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="space-y-3">
          <div v-for="i in 5" :key="i" class="bg-white dark:bg-gray-800 rounded-lg p-5 animate-pulse">
            <div class="flex gap-4">
              <div class="hidden sm:block w-32 h-20 bg-gray-200 dark:bg-gray-700 rounded-lg flex-shrink-0"></div>
              <div class="flex-1 space-y-2">
                <div class="h-5 bg-gray-200 dark:bg-gray-700 rounded w-3/4"></div>
                <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-full"></div>
                <div class="flex gap-4 mt-2">
                  <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-20"></div>
                  <div class="h-3 bg-gray-200 dark:bg-gray-700 rounded w-16"></div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Empty -->
        <div v-else-if="articles.length === 0" class="bg-white dark:bg-gray-800 rounded-xl shadow-sm p-16 text-center text-gray-400">
          <span class="text-4xl mb-4 block">🔍</span>
          <p class="text-gray-500 dark:text-gray-400 font-medium">{{ emptyText }}</p>
          <p class="text-xs mt-1 opacity-70">换个关键词或分类试试？</p>
          <button v-if="keyword || currentCategory || currentTag" @click="clearFilters"
            class="mt-4 px-4 py-1.5 rounded-full bg-primary-600 text-white text-xs hover:bg-primary-700 transition">
            清除筛选
          </button>
        </div>

        <!-- List view -->
        <div v-else-if="viewMode === 'list'" class="space-y-3">
          <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
        </div>

        <!-- Grid view -->
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <ArticleCard v-for="article in articles" :key="article.id" :article="article" layout="grid" />
        </div>

        <Pagination v-if="totalPages > 1" :current="pageNum" :total="totalPages" @change="pageNum = $event" />

        <!-- 右侧栏在 xl 以下会被隐藏：这里补一个可折叠的替代入口，避免窄屏完全看不到热门内容 -->
        <div class="xl:hidden mt-8">
          <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm overflow-hidden">
            <button @click="mobileAsideOpen = !mobileAsideOpen"
              class="w-full px-4 py-3 flex items-center justify-between text-left hover:bg-gray-50 dark:hover:bg-gray-700/50 transition">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">热门内容</h3>
              <svg class="w-4 h-4 text-gray-400 transition-transform duration-200" :class="mobileAsideOpen ? 'rotate-180' : ''" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7"/></svg>
            </button>
            <div v-show="mobileAsideOpen" class="px-4 pb-4">
              <!-- 热门文章 -->
              <div v-if="hotArticles.length" class="space-y-1 mb-3">
                <NuxtLink v-for="(a, i) in hotArticles" :key="a.id" :to="`/article/${a.id}`"
                  class="flex items-start gap-2 py-1.5 rounded hover:bg-gray-50 dark:hover:bg-gray-700 transition text-sm">
                  <span :class="i < 3 ? 'text-red-500' : 'text-gray-400'" class="font-bold text-xs w-5 flex-shrink-0">{{ String(i + 1).padStart(2, '0') }}</span>
                  <span class="flex-1 min-w-0 text-gray-700 dark:text-gray-300 line-clamp-2 leading-snug">{{ a.title }}</span>
                </NuxtLink>
              </div>
              <!-- 热门标签 -->
              <div v-if="hotTags.length" class="flex flex-wrap gap-1.5 pt-3 border-t dark:border-gray-700">
                <NuxtLink v-for="t in hotTags" :key="t.id" :to="`/?tagId=${t.id}`"
                  class="inline-flex items-baseline gap-1 px-2.5 py-1 rounded-full text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 hover:bg-primary-100 dark:hover:bg-primary-900/30 hover:text-primary-600 dark:hover:text-primary-400 transition">
                  {{ t.name }}
                  <span v-if="t.articleCount" class="text-[10px] opacity-50">{{ t.articleCount }}</span>
                </NuxtLink>
              </div>
            </div>
          </div>
        </div>
      </main>

      <!-- Right sidebar -->
      <aside class="hidden xl:block w-60 flex-shrink-0 space-y-4">
        <div class="sticky top-20">
          <!-- Author profile card -->
          <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm overflow-hidden mb-4">
            <div class="p-4 text-center">
              <template v-if="authStore.isLoggedIn && authStore.user">
                <img v-if="authStore.user.avatar" :src="authStore.user.avatar" :alt="authStore.user.nickname"
                  class="w-16 h-16 mx-auto rounded-full object-cover mb-3" />
                <div v-else class="w-16 h-16 mx-auto rounded-full bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center text-white font-bold text-xl mb-3">
                  {{ authStore.user.nickname?.[0] || authStore.user.username?.[0] || 'B' }}
                </div>
                <div class="font-bold text-gray-900 dark:text-gray-100 text-sm">{{ authStore.user.nickname || authStore.user.username }}</div>
                <p class="text-xs text-gray-500 mt-1 leading-relaxed">📝 记录技术成长<br>💻 分享编程心得</p>
                <NuxtLink to="/profile" class="inline-block mt-2 text-xs text-primary-600 dark:text-primary-400 hover:underline">个人主页 →</NuxtLink>
              </template>
              <template v-else>
                <div class="w-16 h-16 mx-auto rounded-full bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center text-white text-2xl mb-3">👤</div>
                <div class="font-bold text-gray-900 dark:text-gray-100 text-sm">未登录</div>
                <p class="text-xs text-gray-500 mt-1 leading-relaxed">登录后查看更多功能</p>
                <NuxtLink to="/login" class="inline-block mt-2 px-3 py-1 rounded-full bg-primary-600 text-white text-xs hover:bg-primary-700 transition">立即登录</NuxtLink>
              </template>
            </div>
          </div>

          <!-- Hot articles -->
          <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm overflow-hidden">
            <div class="px-4 py-3 border-b dark:border-gray-700">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">热门文章</h3>
            </div>
            <div class="p-3 space-y-1">
              <NuxtLink v-for="(a, i) in hotArticles" :key="a.id" :to="`/article/${a.id}`" class="flex items-start gap-2 py-2 px-1 rounded hover:bg-gray-50 dark:hover:bg-gray-700 transition text-sm">
                <span :class="i < 3 ? 'text-red-500' : 'text-gray-400'" class="font-bold text-xs w-5 flex-shrink-0">{{ String(i + 1).padStart(2, '0') }}</span>
                <span class="flex-1 min-w-0">
                  <span class="text-gray-700 dark:text-gray-300 line-clamp-2 leading-snug hover:text-primary-600 dark:hover:text-primary-400 transition">{{ a.title }}</span>
                  <span v-if="a.categoryName" class="block text-xs opacity-50 mt-0.5">{{ a.categoryName }}</span>
                </span>
              </NuxtLink>
            </div>
          </div>

          <!-- Tags -->
          <div class="bg-white dark:bg-gray-800 rounded-xl shadow-sm overflow-hidden">
            <div class="px-4 py-3 border-b dark:border-gray-700">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">热门标签</h3>
            </div>
            <div class="p-3 flex flex-wrap gap-1.5">
              <NuxtLink v-for="t in hotTags" :key="t.id" :to="`/?tagId=${t.id}`"
                class="inline-flex items-baseline gap-1 px-2.5 py-1 rounded-full text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 hover:bg-primary-100 dark:hover:bg-primary-900/30 hover:text-primary-600 dark:hover:text-primary-400 transition">
                {{ t.name }}
                <span v-if="t.articleCount" class="text-[10px] opacity-50">{{ t.articleCount }}</span>
              </NuxtLink>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 移动端分类抽屉（Teleport 放在根节点内，保证页面单根，避免过渡卡死） -->
    <Teleport to="body">
      <Transition name="drawer">
        <div v-if="mobileCatOpen" class="fixed inset-0 z-[70] lg:hidden" @click.self="mobileCatOpen = false">
          <div class="absolute inset-0 bg-black/30" @click="mobileCatOpen = false"></div>
          <div class="absolute bottom-0 inset-x-0 bg-white dark:bg-gray-900 rounded-t-2xl max-h-[75vh] overflow-y-auto pb-8">
            <div class="sticky top-0 bg-white/90 dark:bg-gray-900/90 backdrop-blur px-4 py-3 border-b dark:border-gray-700 flex items-center justify-between">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">选择分类</h3>
              <button @click="mobileCatOpen = false" class="w-8 h-8 rounded-full flex items-center justify-center text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800 transition">✕</button>
            </div>
            <div class="p-3">
              <button @click="currentCategory = undefined; pageNum = 1; mobileCatOpen = false"
                :class="!currentCategory ? 'bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400' : 'text-gray-600 dark:text-gray-400'"
                class="w-full text-left px-4 py-2.5 rounded-xl text-sm font-medium transition flex items-center gap-2">
                <span class="flex-1">全部文章</span>
              </button>
              <template v-for="cat in categoryTree" :key="cat.id">
                <button @click="selectCategory(cat); mobileCatOpen = false"
                  :class="currentCategory === cat.id ? 'bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400' : 'text-gray-600 dark:text-gray-400'"
                  class="w-full text-left px-4 py-2.5 rounded-xl text-sm transition flex items-center gap-2 mt-0.5">
                  <span v-html="catIcon(cat.name)" class="flex-shrink-0"></span>
                  <span class="flex-1">{{ cat.name }}</span>
                  <span class="text-xs opacity-50">{{ cat.articleCount || 0 }}</span>
                </button>
                <div v-if="cat.children && cat.children.length" class="ml-6 border-l border-gray-100 dark:border-gray-700 pl-2">
                  <button v-for="child in cat.children" :key="child.id" @click="currentCategory = child.id; pageNum = 1; mobileCatOpen = false"
                    :class="currentCategory === child.id ? 'text-primary-600 dark:text-primary-400' : 'text-gray-500 dark:text-gray-400'"
                    class="w-full text-left px-3 py-2 rounded-lg text-sm transition flex items-center gap-2">
                    <span class="flex-1">{{ child.name }}</span>
                    <span class="text-xs opacity-40">{{ child.articleCount || 0 }}</span>
                  </button>
                </div>
              </template>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
const { get } = useApi()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const pageNum = ref(1)
const keyword = ref((route.query.keyword as string) || '')
const currentCategory = ref<number | undefined>(undefined)
const currentTag = ref<number | undefined>(route.query.tagId ? Number(route.query.tagId) : undefined)
const articles = ref<any[]>([])
const categoryTree = ref<any[]>([])
const categoryList = ref<any[]>([])
const tags = ref<any[]>([])
const hotArticles = ref<any[]>([])
const totalPages = ref(0)
const loading = ref(true)
const mobileCatOpen = ref(false)

const { viewMode, setViewMode, sortMode } = useLayout()

const pageTitle = computed(() => {
  if (keyword.value) return `搜索：${keyword.value}`
  if (currentTag.value) {
    const t = tags.value.find((x: any) => x.id === currentTag.value)
    return t ? `标签：${t.name}` : '标签文章'
  }
  if (currentCategory.value) {
    const c = categoryList.value.find((x: any) => x.id === currentCategory.value)
    return c ? c.name : '分类文章'
  }
  return '全部文章'
})

const emptyText = computed(() => {
  if (keyword.value) return '没有找到相关文章'
  if (currentCategory.value || currentTag.value) return '该分类下暂无文章'
  return '暂无文章'
})

function clearFilters() {
  keyword.value = ''
  currentCategory.value = undefined
  currentTag.value = undefined
  pageNum.value = 1
  router.replace({ query: {} })
  fetchArticles()
}

// 分类图标（SVG，统一线性风格）
const CAT_ICONS: [string, string][] = [
  ['AI', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M12 3l1.8 5.5L19 10l-5.2 1.5L12 17l-1.8-5.5L5 10l5.2-1.5z"/><path stroke-linecap="round" stroke-width="1.8" d="M19 3v3M17.5 4.5h3"/></svg>'],
  ['大模型', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="5" y="5" width="14" height="14" rx="3" stroke-width="1.8"/><circle cx="9" cy="9" r="1.2" fill="currentColor" stroke="none"/><circle cx="15" cy="9" r="1.2" fill="currentColor" stroke="none"/><circle cx="9" cy="15" r="1.2" fill="currentColor" stroke="none"/><circle cx="15" cy="15" r="1.2" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.2" fill="currentColor" stroke="none"/></svg>'],
  ['Java', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M8 3.5s4.5 1 6.5 4.5c2.5-1 4-1.5 4-1.5s-2 6 .5 9.5c-2 .5-4.5 1-7 0 1-3 .5-8-4-12.5z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M8 3.5s-4 3 0 12.5c-1.5.5-3 0-3 0s1 4 6 4c0 0 .5-2-1-3 1.5 0 4.5.5 6.5-3"/></svg>'],
  ['Python', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linejoin="round" stroke-width="1.8" d="M12 3c-1.8 0-3 .4-3 1v1.5c1.8-1 5-1 6.5 0M15 3c1.8 0 3 .4 3 1v1.5c-1.8-1-5-1-6.5 0" fill="none" stroke-linecap="round"/><path stroke-width="1.8" fill="none" d="M9 5.5C6.8 6 5.5 7 5.5 9.5c0 2 .3 2.5 1.5 2.5M15 5.5c2.2.5 3.5 1.5 3.5 4 0 2-.3 2.5-1.5 2.5"/><path stroke-linejoin="round" stroke-width="1.8" d="M9 13.5c0 1-1 1-1.5 1H8c-1.5 0-2.5.5-2.5 2v2c0 1.5 1 2 2.5 2 1.5 0 3.5.5 5 0M12 21c1.8 0 3-.4 3-1v-1.5c-1.8 1-5 1-6.5 0" fill="none" stroke-linecap="round"/><path stroke-width="1.8" fill="none" d="M15 18.5c2.2-.5 3.5-1.5 3.5-4 0-2-.3-2.5-1.5-2.5M9 18.5C6.8 18 5.5 17 5.5 14.5"/></svg>'],
  ['Docker', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M3 13h1.5M6.5 13H8m-5-3h1.5M6.5 10H8m-2.5-3H7m5 6h9a5 5 0 01-5 5c-3.5 0-6-1.5-6-5z"/><path stroke-width="1.8" d="M4 13c0 3.5 2.5 5.5 6 5.5" fill="none" stroke-linecap="round"/></svg>'],
  ['Linux', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M4 17l6-6-6-6M12 19h8"/></svg>'],
  ['操作系统', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><rect x="2.5" y="4" width="19" height="12" rx="2" stroke-width="1.8"/><path stroke-linejoin="round" stroke-width="1.8" d="M8 20h8M12 16v4" fill="none" stroke-linecap="round"/><path stroke-width="1.8" d="M6 8h2M6 11h2" fill="none" stroke-linecap="round"/></svg>'],
  ['网络', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" stroke-width="1.8"/><path stroke-width="1.8" d="M3 12h18M12 3a15 15 0 010 18M12 3a15 15 0 000 18" fill="none"/></svg>'],
  ['数据库', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><ellipse cx="12" cy="5.5" rx="8" ry="2.8" stroke-width="1.8"/><path stroke-width="1.8" d="M4 5.5V18.5c0 1.5 3.6 2.8 8 2.8s8-1.3 8-2.8V5.5" fill="none"/><path stroke-width="1.8" d="M4 12c0 1.5 3.6 2.8 8 2.8s8-1.3 8-2.8" fill="none"/></svg>'],
  ['课程', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linejoin="round" stroke-width="1.8" d="M4 19.5A2.5 2.5 0 016.5 17H20V3H6.5A2.5 2.5 0 004 5.5z" fill="none" stroke-linecap="round"/><path stroke-width="1.8" d="M4 19.5A2.5 2.5 0 016.5 17H20" fill="none" stroke-linecap="round"/></svg>'],
  ['科普', '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linejoin="round" stroke-width="1.8" d="M9 18h6M10 22h4M12 2a7 7 0 00-4.5 12.4c.6.5 1.5 1.6 1.5 2.6h6c0-1 .9-2.1 1.5-2.6A7 7 0 0012 2z" fill="none" stroke-linecap="round"/></svg>']
]
function catIcon(name: string) {
  const hit = CAT_ICONS.find(([k]) => (name || '').includes(k))
  return hit ? hit[1] : '<svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M12 6v6m0 0l3-2m-3 2l-3-2"/><circle cx="12" cy="12" r="9" stroke-width="1.8"/></svg>'
}

const expandedCats = ref<Set<number>>(new Set())
function toggleExpand(id: number) {
  const s = new Set(expandedCats.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  expandedCats.value = s
}
function isExpanded(id: number) { return expandedCats.value.has(id) }

function catBtnClass(cat: any) {
  const active = currentCategory.value === cat.id
  const base = 'w-full text-left px-3 py-2 text-sm transition flex items-center gap-1.5 border-l-2 border-transparent'
  return active
    ? base + ' bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 border-primary-600'
    : base + ' text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
}

function selectCategory(cat: any) {
  currentCategory.value = cat.id
  pageNum.value = 1
  // 有子分类且未展开 → 自动展开
  if (cat.children && cat.children.length && !isExpanded(cat.id)) {
    toggleExpand(cat.id)
  }
}

// 扁平分类列表 → 树（父分类不存在的子分类提升为一级，保证不丢失）
function buildCategoryTree(list: any[]): any[] {
  const map = new Map<number, any>()
  list.forEach((c: any) => map.set(c.id, { ...c, children: [] }))
  const roots: any[] = []
  list.forEach((c: any) => {
    const node = map.get(c.id)
    const parent = c.parentId ? map.get(c.parentId) : null
    if (parent) parent.children.push(node)
    else roots.push(node)
  })
  return roots
}

const hotTags = computed(() =>
  [...tags.value].sort((a: any, b: any) => (b.articleCount || 0) - (a.articleCount || 0)).slice(0, 8)
)

async function fetchArticles() {
  loading.value = true
  try {
    const params = new URLSearchParams({ pageNum: String(pageNum.value), pageSize: String(15) })
    if (currentCategory.value) params.set('categoryId', String(currentCategory.value))
    if (currentTag.value) params.set('tagId', String(currentTag.value))
    if (keyword.value) params.set('keyword', keyword.value)
    if (sortMode.value) params.set('sort', sortMode.value)
    const res = await get<any>(`/article/list?${params.toString()}`)
    if (res.code === 200 && res.data) {
      articles.value = res.data.records || []
      totalPages.value = res.data.pages || 0
    }
  } catch { /* SSR/网络异常不阻塞页面 */ } finally { loading.value = false }
}

async function fetchCategories() {
  try {
    const res = await get<any>('/category/list')
    if (res.code === 200) {
      categoryList.value = res.data || []
      categoryTree.value = buildCategoryTree(res.data || [])
    }
  } catch { /* 容错 */ }
}

async function fetchTags() {
  try {
    const res = await get<any>('/article/tags')
    if (res.code === 200) tags.value = res.data || []
  } catch { /* 容错 */ }
}

async function fetchHotArticles() {
  try {
    const res = await get<any>('/article/list?pageSize=5')
    if (res.code === 200 && res.data) {
      hotArticles.value = (res.data.records || []).sort((a: any, b: any) => b.viewCount - a.viewCount).slice(0, 5)
    }
  } catch { /* 容错 */ }
}

watch([pageNum, currentCategory, currentTag], () => { keyword.value = ''; fetchArticles() })
watch(sortMode, () => { pageNum.value = 1; fetchArticles() })
watch(() => route.query.keyword, (val) => {
  keyword.value = (val as string) || ''
  currentCategory.value = undefined
  currentTag.value = undefined
  pageNum.value = 1
  fetchArticles()
})
watch(() => route.query.tagId, (val) => {
  currentTag.value = val ? Number(val) : undefined
  currentCategory.value = undefined
  keyword.value = ''
  pageNum.value = 1
  fetchArticles()
})

async function init() {
  await fetchCategories()
  await Promise.all([fetchTags(), fetchHotArticles()])
  fetchArticles()
}
init()

useSeoMeta({ title: '好啵博客', description: '一个程序员的个人技术博客' })
</script>
