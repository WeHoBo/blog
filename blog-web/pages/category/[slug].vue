<template>
  <div class="max-w-4xl mx-auto py-8">
    <nav class="text-xs text-gray-400 mb-3 flex items-center gap-1.5">
      <NuxtLink to="/" class="hover:text-primary-600 transition">首页</NuxtLink>
      <span class="opacity-50">/</span>
      <NuxtLink to="/categories" class="hover:text-primary-600 transition">分类</NuxtLink>
      <span class="opacity-50">/</span>
      <span class="text-gray-500 dark:text-gray-400 truncate max-w-[12rem]">{{ category?.name || '分类' }}</span>
    </nav>

    <header class="mb-6">
      <h1 class="text-2xl font-extrabold text-gray-900 dark:text-gray-100 flex items-center gap-2">
        <span class="w-1 h-6 bg-primary-600 rounded-full"></span>
        {{ category?.name || '分类文章' }}
      </h1>
      <p class="text-sm text-gray-500 dark:text-gray-400 mt-2">
        该分类下共 <strong class="text-gray-700 dark:text-gray-200">{{ total }}</strong> 篇文章
      </p>
    </header>

    <div v-if="pending" class="text-center py-16 text-gray-400">加载中...</div>

    <div v-else-if="!articles.length" class="bg-white dark:bg-gray-800 rounded-xl shadow-sm p-16 text-center text-gray-400">
      <span class="text-4xl block mb-3">🗂️</span>
      <p class="text-sm">该分类下暂无文章</p>
      <NuxtLink to="/categories" class="inline-block mt-4 text-sm text-primary-600 dark:text-primary-400 hover:underline">← 返回分类</NuxtLink>
    </div>

    <template v-else>
      <div class="space-y-3">
        <ArticleCard v-for="a in articles" :key="a.id" :article="a" />
      </div>
      <Pagination v-if="totalPages > 1" :current="pageNum" :total="totalPages" @change="goPage" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { matchTaxonomy, taxonomyCanonical, type TaxonomyItem } from '~/utils/taxonomy'

const route = useRoute()
const router = useRouter()
const { get } = useApi()

const pageNum = computed(() => Math.max(1, Number(route.query.page) || 1))

const { data, pending } = await useAsyncData(
  'category-detail',
  async () => {
    const catRes = await get<any>('/category/list').catch(() => null)
    const all: TaxonomyItem[] = catRes?.code === 200 ? (catRes.data || []) : []
    const matched = matchTaxonomy(all, route.params.slug)
    if (!matched) {
      return { category: null, catsOk: catRes?.code === 200, articles: [] as any[], total: 0, totalPages: 0 }
    }
    // 后端 categoryId 查询会自动带上子分类，所以一级分类页能一次列出全部子孙文章
    const listRes = await get<any>(
      `/article/list?categoryId=${matched.id}&pageNum=${pageNum.value}&pageSize=15`
    ).catch(() => null)
    const d = listRes?.code === 200 ? listRes.data : null
    return {
      category: matched,
      catsOk: true,
      articles: (d?.records || []) as any[],
      total: Number(d?.total || 0),
      totalPages: Number(d?.pages || 0)
    }
  },
  { watch: [() => route.params.slug, () => route.query.page] }
)

const category = computed<TaxonomyItem | null>(() => data.value?.category || null)
const articles = computed<any[]>(() => data.value?.articles || [])
const total = computed(() => data.value?.total || 0)
const totalPages = computed(() => data.value?.totalPages || 0)

if (data.value?.catsOk && !data.value?.category) {
  throw createError({ statusCode: 404, statusMessage: '分类不存在', fatal: true })
}

function goPage(p: number) {
  router.push({ query: { ...route.query, page: String(p) } })
}

const canonicalUrl = computed(() => taxonomyCanonical('/category', category.value))

useSeoMeta({
  title: () => (category.value ? `${category.value.name} - 分类 - 好啵博客` : '分类文章 - 好啵博客'),
  description: () =>
    category.value
      ? `好啵博客「${category.value.name}」分类下的技术文章，共 ${category.value.articleCount || total.value} 篇。`
      : '按分类浏览好啵博客的技术文章。',
  ogTitle: () => (category.value ? `${category.value.name} - 分类 - 好啵博客` : '分类文章 - 好啵博客'),
  ogDescription: () => (category.value ? `「${category.value.name}」分类下的技术文章。` : '按分类浏览好啵博客的技术文章。'),
  ogUrl: () => canonicalUrl.value
})
useHead(() => ({ link: [{ rel: 'canonical', href: canonicalUrl.value }] }))
</script>
