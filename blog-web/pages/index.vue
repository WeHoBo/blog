<template>
  <div class="max-w-7xl mx-auto">
    <div class="flex gap-6 py-6">
      <!-- Left sidebar: category nav -->
      <aside class="hidden lg:block w-44 flex-shrink-0">
        <div class="sticky top-20 bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
          <div class="px-4 py-3 border-b dark:border-gray-700 flex items-center justify-between">
            <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">文章分类</h3>
            <button @click="expandAll = !expandAll" class="text-xs text-gray-400 hover:text-blue-500 transition">{{ expandAll ? '收起' : '展开' }}</button>
          </div>
          <div class="py-1">
            <button
              @click="currentCategory = undefined; pageNum = 1"
              :class="!currentCategory ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 border-r-2 border-blue-600' : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'"
              class="w-full text-left px-4 py-2 text-sm transition">
              全部文章
            </button>
            <template v-for="cat in categoryTree" :key="cat.id">
              <button
                @click="currentCategory = cat.id; pageNum = 1"
                :class="currentCategory === cat.id ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 border-r-2 border-blue-600' : 'text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'"
                class="w-full text-left px-4 py-2 text-sm transition">
                <span class="font-medium">{{ cat.name }}</span>
                <span class="text-xs text-gray-400 ml-1">({{ cat.articleCount || 0 }})</span>
              </button>
              <button
                v-if="(expandAll || currentCategory === cat.id || isChildSelected(cat)) && cat.children && cat.children.length > 0"
                v-for="child in cat.children"
                :key="child.id"
                @click="currentCategory = child.id; pageNum = 1"
                :class="currentCategory === child.id ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 border-r-2 border-blue-600' : 'text-gray-500 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700'"
                class="w-full text-left pl-8 py-1.5 text-sm transition">
                {{ child.name }}
                <span class="text-xs text-gray-400 ml-1">({{ child.articleCount || 0 }})</span>
              </button>
            </template>
          </div>
        </div>
      </aside>

      <!-- Center: article feed -->
      <main class="flex-1 min-w-0">
        <!-- Loading -->
        <div v-if="loading" class="flex justify-center py-20">
          <div class="animate-spin h-8 w-8 border-2 border-blue-600 border-t-transparent rounded-full"></div>
        </div>

        <!-- Empty -->
        <div v-else-if="articles.length === 0" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-20 text-center text-gray-400">
          <span class="text-4xl mb-4 block">📝</span>
          暂无文章
        </div>

        <!-- Article list -->
        <div v-else class="space-y-0">
          <div v-for="article in articles" :key="article.id" class="bg-white dark:bg-gray-800 border-b dark:border-gray-700 first:rounded-t-lg last:rounded-b-lg hover:bg-gray-50 dark:hover:bg-gray-750 transition p-5">
            <div class="flex gap-4">
              <!-- Cover -->
              <NuxtLink v-if="article.cover" :to="`/article/${article.id}`" class="hidden sm:block w-32 h-20 flex-shrink-0 rounded-lg overflow-hidden">
                <img :src="article.cover" class="w-full h-full object-cover" />
              </NuxtLink>

              <!-- Info -->
              <div class="flex-1 min-w-0">
                <NuxtLink :to="`/article/${article.id}`" class="block group">
                  <h2 class="text-base font-bold text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition line-clamp-1 mb-2">
                    <span v-if="article.isTop" class="inline-block text-xs bg-red-500 text-white px-1.5 py-0.5 rounded mr-1.5 align-middle">置顶</span>
                    {{ article.title }}
                  </h2>
                  <p v-if="article.summary" class="text-sm text-gray-500 dark:text-gray-400 line-clamp-2 mb-3 leading-relaxed">
                    {{ article.summary }}
                  </p>
                </NuxtLink>
                <div class="flex items-center gap-4 text-xs text-gray-400 flex-wrap">
                  <NuxtLink :to="`/?keyword=${encodeURIComponent(article.title)}`" class="text-xs text-blue-500 hover:underline mr-2">{{ article.createTime?.substring(0, 10) }}</NuxtLink>
                  <span>👁 {{ article.viewCount }} 阅读</span>
                  <span>💬 {{ article.commentCount || 0 }} 评论</span>
                  <span>❤ {{ article.likeCount || 0 }} 点赞</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <Pagination v-if="totalPages > 1" :current="pageNum" :total="totalPages" @change="pageNum = $event" />
      </main>

      <!-- Right sidebar -->
      <aside class="hidden xl:block w-64 flex-shrink-0 space-y-4">
        <div class="sticky top-20">
          <!-- Hot articles -->
          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
            <div class="px-4 py-3 border-b dark:border-gray-700">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">热门文章</h3>
            </div>
            <div class="p-3 space-y-1">
              <NuxtLink v-for="(a, i) in hotArticles" :key="a.id" :to="`/article/${a.id}`" class="flex items-start gap-2 py-2 px-1 rounded hover:bg-gray-50 dark:hover:bg-gray-700 transition text-sm">
                <span :class="i < 3 ? 'text-red-500' : 'text-gray-400'" class="font-bold text-xs w-5 flex-shrink-0">{{ i + 1 }}</span>
                <span class="text-gray-700 dark:text-gray-300 line-clamp-2 leading-snug hover:text-blue-600 dark:hover:text-blue-400 transition">{{ a.title }}</span>
              </NuxtLink>
            </div>
          </div>

          <!-- Tags -->
          <div class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
            <div class="px-4 py-3 border-b dark:border-gray-700">
              <h3 class="text-sm font-bold text-gray-900 dark:text-gray-100">热门标签</h3>
            </div>
            <div class="p-3 flex flex-wrap gap-2">
              <NuxtLink v-for="t in tags" :key="t.id" to="/" class="px-3 py-1 rounded-full text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-400 hover:bg-blue-100 dark:hover:bg-blue-900/30 hover:text-blue-600 dark:hover:text-blue-400 transition">{{ t.name }}</NuxtLink>
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

const pageNum = ref(1)
const keyword = ref((route.query.keyword as string) || '')
const currentCategory = ref<number | undefined>(undefined)
const articles = ref<any[]>([])
const categoryTree = ref<any[]>([])
const tags = ref<any[]>([])
const hotArticles = ref<any[]>([])
const totalPages = ref(0)
const loading = ref(true)
const expandAll = ref(false)

function isChildSelected(cat: any) {
  return cat.children && cat.children.some((c: any) => c.id === currentCategory.value)
}

async function fetchArticles() {
  loading.value = true
  try {
    const params = new URLSearchParams({ pageNum: String(pageNum.value), pageSize: String(15) })
    if (currentCategory.value) params.set('categoryId', String(currentCategory.value))
    if (keyword.value) params.set('keyword', keyword.value)
    const res = await get<any>(`/article/list?${params.toString()}`)
    if (res.code === 200 && res.data) {
      articles.value = res.data.records || []
      totalPages.value = res.data.pages || 0
    }
  } finally { loading.value = false }
}

async function fetchCategories() {
  const res = await get<any>('/category/tree')
  if (res.code === 200) categoryTree.value = res.data || []
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

watch([pageNum, currentCategory], () => fetchArticles())
fetchArticles()
fetchCategories()
fetchTags()
fetchHotArticles()

useSeoMeta({ title: '好啵博客', description: '一个程序员的个人技术博客' })
</script>
