<template>
  <div class="max-w-7xl mx-auto">
    <div class="flex gap-6 py-6">
      <!-- Left sidebar: category nav -->
      <aside class="hidden lg:block w-56 flex-shrink-0">
        <div class="sticky top-20 bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
          <div class="px-4 py-3 border-b dark:border-gray-700">
            <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">文章分类</h3>
          </div>
          <div class="py-1 max-h-[75vh] overflow-y-auto">
            <!-- 全部 -->
            <button @click="currentCategory = undefined; pageNum = 1"
              :class="!currentCategory
                ? 'bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 border-r-2 border-primary-600'
                : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'"
              class="w-full text-left px-4 py-2 text-sm font-medium transition flex items-center gap-2">
              <span class="flex-shrink-0 text-base leading-none">📂</span>
              <span class="flex-1 min-w-0">全部</span>
            </button>
            <!-- 全部分类（一级 + 二级 扁平列表） -->
            <button v-for="cat in categoryList" :key="cat.id"
              @click="currentCategory = cat.id; pageNum = 1"
              :class="catBtnClass(cat)">
              <span class="flex-shrink-0 text-base leading-none">{{ categoryIcon(cat.name) }}</span>
              <span class="flex-1 min-w-0 truncate">{{ cat.name }}</span>
              <span class="flex-shrink-0 text-xs px-1.5 py-0.5 rounded-full" :class="badgeColor(cat.name)">
                {{ cat.articleCount || 0 }}
              </span>
            </button>
          </div>
        </div>
      </aside>

      <!-- Center: article feed -->
      <main class="flex-1 min-w-0">
        <!-- Heading + view toggle -->
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 flex items-center gap-2">
            <span class="w-1 h-5 bg-primary-600 rounded-full"></span>
            全部文章
          </h2>
          <div class="flex bg-white dark:bg-gray-800 rounded-lg border dark:border-gray-700 p-1">
            <button @click="setViewMode('list')" title="列表视图"
              class="px-2.5 py-1 rounded-md text-sm transition"
              :class="viewMode === 'list' ? 'bg-primary-600 text-white' : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700'">📋</button>
            <button @click="setViewMode('grid')" title="网格视图"
              class="px-2.5 py-1 rounded-md text-sm transition"
              :class="viewMode === 'grid' ? 'bg-primary-600 text-white' : 'text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700'">🔲</button>
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
        <div v-else-if="articles.length === 0" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-20 text-center text-gray-400">
          <span class="text-4xl mb-4 block">📝</span>
          暂无文章
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
      </main>

      <!-- Right sidebar -->
      <aside class="hidden xl:block w-64 flex-shrink-0 space-y-4">
        <div class="sticky top-20">
          <!-- Author profile card -->
          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden mb-4">
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
          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
            <div class="px-4 py-3 border-b dark:border-gray-700">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">热门文章</h3>
            </div>
            <div class="p-3 space-y-1">
              <NuxtLink v-for="(a, i) in hotArticles" :key="a.id" :to="`/article/${a.id}`" class="flex items-start gap-2 py-2 px-1 rounded hover:bg-gray-50 dark:hover:bg-gray-700 transition text-sm">
                <span :class="i < 3 ? 'text-red-500' : 'text-gray-400'" class="font-bold text-xs w-5 flex-shrink-0">{{ i + 1 }}</span>
                <span class="text-gray-700 dark:text-gray-300 line-clamp-2 leading-snug hover:text-primary-600 dark:hover:text-primary-400 transition">{{ a.title }}</span>
              </NuxtLink>
            </div>
          </div>

          <!-- Tags -->
          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
            <div class="px-4 py-3 border-b dark:border-gray-700">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">热门标签</h3>
            </div>
            <div class="p-3 flex flex-wrap gap-2">
              <NuxtLink v-for="t in tags" :key="t.id" to="/" class="px-3 py-1 rounded-full text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 hover:bg-primary-100 dark:hover:bg-primary-900/30 hover:text-primary-600 dark:hover:text-primary-400 transition">{{ t.name }}</NuxtLink>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
const { get } = useApi()
const route = useRoute()
const authStore = useAuthStore()

const pageNum = ref(1)
const keyword = ref((route.query.keyword as string) || '')
const currentCategory = ref<number | undefined>(undefined)
const articles = ref<any[]>([])
const categoryTree = ref<any[]>([])
const categoryList = ref<any[]>([])
const tags = ref<any[]>([])
const hotArticles = ref<any[]>([])
const totalPages = ref(0)
const loading = ref(true)

const { viewMode, setViewMode, sortMode } = useLayout()

const ICONS: [string, string][] = [
  ['transformers', '🤖'],
  ['SpringCloud', '☁️'],
  ['PyTorch', '🔥'],
  ['Python', '🐍'],
  ['Docker', '🐳'],
  ['网络', '🌐'],
  ['操作系统', '💻'],
  ['科普', '📖']
]
function categoryIcon(name: string) {
  const hit = ICONS.find(([k]) => (name || '').includes(k))
  return hit ? hit[1] : '📂'
}

const BADGE_COLORS = [
  'bg-primary-100 dark:bg-primary-900/40 text-primary-700 dark:text-primary-300',
  'bg-green-100 dark:bg-green-900/40 text-green-700 dark:text-green-300',
  'bg-teal-100 dark:bg-teal-900/40 text-teal-700 dark:text-teal-300',
  'bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300',
  'bg-rose-100 dark:bg-rose-900/40 text-rose-700 dark:text-rose-300',
  'bg-cyan-100 dark:bg-cyan-900/40 text-cyan-700 dark:text-cyan-300',
  'bg-violet-100 dark:bg-violet-900/40 text-violet-700 dark:text-violet-300',
  'bg-pink-100 dark:bg-pink-900/40 text-pink-700 dark:text-pink-300'
]
function badgeColor(name: string) {
  let h = 0
  for (const ch of (name || '')) h = (h * 31 + ch.charCodeAt(0)) % 997
  return BADGE_COLORS[h % BADGE_COLORS.length]
}

function catBtnClass(cat: any) {
  const active = currentCategory.value === cat.id
  const base = 'w-full text-left px-4 py-2 text-sm transition flex items-center gap-2 ' + (cat.parentId ? 'pl-9 ' : '')
  return active
    ? base + 'bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 border-r-2 border-primary-600'
    : base + 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'
}

async function fetchArticles() {
  loading.value = true
  try {
    const params = new URLSearchParams({ pageNum: String(pageNum.value), pageSize: String(15) })
    if (currentCategory.value) params.set('categoryId', String(currentCategory.value))
    if (keyword.value) params.set('keyword', keyword.value)
    if (sortMode.value) params.set('sort', sortMode.value)
    const res = await get<any>(`/article/list?${params.toString()}`)
    if (res.code === 200 && res.data) {
      articles.value = res.data.records || []
      totalPages.value = res.data.pages || 0
    }
  } finally { loading.value = false }
}

async function fetchCategories() {
  const res = await get<any>('/category/list')
  if (res.code === 200) {
    categoryTree.value = res.data || []
    categoryList.value = res.data || []
  }
}

async function fetchTags() {
  const res = await get<any>('/article/tags')
  if (res.code === 200) tags.value = res.data || []
}

async function fetchHotArticles() {
  const res = await get<any>('/article/list?pageSize=8')
  if (res.code === 200 && res.data) {
    hotArticles.value = (res.data.records || []).sort((a: any, b: any) => b.viewCount - a.viewCount)
  }
}

watch([pageNum, currentCategory], () => { keyword.value = ''; fetchArticles() })
watch(sortMode, () => { pageNum.value = 1; fetchArticles() })
watch(() => route.query.keyword, (val) => {
  keyword.value = (val as string) || ''
  currentCategory.value = undefined
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
