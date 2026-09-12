<template>
  <div class="max-w-4xl mx-auto py-8">
    <nav class="text-xs text-gray-400 mb-3 flex items-center gap-1.5">
      <NuxtLink to="/" class="hover:text-primary-600 transition">首页</NuxtLink>
      <span class="opacity-50">/</span>
      <NuxtLink to="/tags" class="hover:text-primary-600 transition">标签云</NuxtLink>
      <span class="opacity-50">/</span>
      <span class="text-gray-500 dark:text-gray-400 truncate max-w-[12rem]">{{ tag?.name || '标签' }}</span>
    </nav>

    <header class="mb-6">
      <h1 class="text-2xl font-extrabold text-gray-900 dark:text-gray-100 flex items-center gap-2">
        <span class="text-primary-500">#</span>{{ tag?.name || '标签文章' }}
      </h1>
      <p class="text-sm text-gray-500 dark:text-gray-400 mt-2">
        该标签下共 <strong class="text-gray-700 dark:text-gray-200">{{ total }}</strong> 篇文章
      </p>
    </header>

    <div v-if="pending" class="text-center py-16 text-gray-400">加载中...</div>

    <div v-else-if="!articles.length" class="bg-white dark:bg-gray-800 rounded-xl shadow-sm p-16 text-center text-gray-400">
      <span class="text-4xl block mb-3">🗂️</span>
      <p class="text-sm">该标签下暂无文章</p>
      <NuxtLink to="/tags" class="inline-block mt-4 text-sm text-primary-600 dark:text-primary-400 hover:underline">← 返回标签云</NuxtLink>
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
  'tag-detail',
  async () => {
    const tagRes = await get<any>('/article/tags').catch(() => null)
    const all: TaxonomyItem[] = tagRes?.code === 200 ? (tagRes.data || []) : []
    const matched = matchTaxonomy(all, route.params.slug)
    if (!matched) {
      // tagRes 为 null 说明是请求失败，不能误判成 404
      return { tag: null, tagsOk: tagRes?.code === 200, articles: [] as any[], total: 0, totalPages: 0 }
    }
    const listRes = await get<any>(
      `/article/list?tagId=${matched.id}&pageNum=${pageNum.value}&pageSize=15`
    ).catch(() => null)
    const d = listRes?.code === 200 ? listRes.data : null
    return {
      tag: matched,
      tagsOk: true,
      articles: (d?.records || []) as any[],
      total: Number(d?.total || 0),
      totalPages: Number(d?.pages || 0)
    }
  },
  { watch: [() => route.params.slug, () => route.query.page] }
)

const tag = computed<TaxonomyItem | null>(() => data.value?.tag || null)
const articles = computed<any[]>(() => data.value?.articles || [])
const total = computed(() => data.value?.total || 0)
const totalPages = computed(() => data.value?.totalPages || 0)

// 数据取到了、但匹配不到这个标签 → 真正的 404（让搜索引擎不要收录）
if (data.value?.tagsOk && !data.value?.tag) {
  throw createError({ statusCode: 404, statusMessage: '标签不存在', fatal: true })
}

function goPage(p: number) {
  router.push({ query: { ...route.query, page: String(p) } })
}

const canonicalUrl = computed(() => taxonomyCanonical('/tag', tag.value))

useSeoMeta({
  title: () => (tag.value ? `标签：${tag.value.name} - 好啵博客` : '标签文章 - 好啵博客'),
  description: () =>
    tag.value
      ? `好啵博客中与「${tag.value.name}」相关的技术文章，共 ${tag.value.articleCount || total.value} 篇。`
      : '按标签浏览好啵博客的技术文章。',
  ogTitle: () => (tag.value ? `标签：${tag.value.name} - 好啵博客` : '标签文章 - 好啵博客'),
  ogDescription: () => (tag.value ? `与「${tag.value.name}」相关的技术文章。` : '按标签浏览好啵博客的技术文章。'),
  ogUrl: () => canonicalUrl.value
})
useHead(() => ({ link: [{ rel: 'canonical', href: canonicalUrl.value }] }))
</script>
