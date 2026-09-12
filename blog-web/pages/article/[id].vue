<template>
  <div class="max-w-7xl mx-auto py-6">
    <div v-if="pending" class="flex justify-center py-32">
      <div class="animate-spin h-8 w-8 border-2 border-primary-600 border-t-transparent rounded-full"></div>
    </div>
    <div v-else-if="!article" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-20 text-center text-gray-400">
      <span class="text-5xl mb-4 block">🔍</span>
      文章不存在或已被删除
    </div>
    <!-- 与 /post/{slug} 共用同一个组件，两条路由的展示完全一致 -->
    <ArticleDetail v-else :article="article" :tags="tags" :author="author" />
  </div>
</template>

<script setup lang="ts">
/**
 * /article/{id} —— 按 id 打开文章。
 *
 * 这个 URL 是给「没有 slug 的老数据」和 RAG 知识库的引用来源（后端 vector_store
 * 生成的命中链接就是 /article/{id}）兜底用的。页面本身只负责取数与 SEO，
 * 视图全部交给 ArticleDetail，避免又出现"两个页面功能不一样"。
 */
import { articlePath } from '~/utils/articlePath'

const route = useRoute()
const { get } = useApi()

const SITE_URL = 'https://codeup.asia'

const { data, pending } = await useAsyncData(
  `article-${route.params.id}`,
  async () => {
    const res = await get<any>(`/article/${route.params.id}`)
    if (res.code === 200) return res.data
    throw new Error(res.message || '文章不存在')
  },
  // 客户端路由导航（上一篇/下一篇）时自动重新拉取
  { watch: [() => route.params.id] }
)

const article = computed(() => (data.value as any)?.article || null)
const tags = computed<any[]>(() => (data.value as any)?.tags || [])
const author = computed(() => (data.value as any)?.author || null)

// canonical 与站内链接保持一致：有 slug 指向 /post/{slug}，否则才用 /article/{id}
const canonicalUrl = computed(() =>
  SITE_URL + articlePath(article.value ?? { id: route.params.id })
)

useSeoMeta({
  title: () => (article.value?.title ? `${article.value.title} - 好啵博客` : '文章详情 - 好啵博客'),
  description: () => article.value?.summary || '好啵博客技术文章',
  ogTitle: () => article.value?.title || '好啵博客',
  ogDescription: () => article.value?.summary || '一个程序员的个人技术博客',
  ogImage: () => article.value?.cover || `${SITE_URL}/apple-touch-icon.png`,
  ogType: 'article',
  ogUrl: () => canonicalUrl.value
})

// Article 结构化数据：搜索引擎/社交平台能拿到作者、发布时间与封面
useHead(() => ({
  link: [{ rel: 'canonical', href: canonicalUrl.value }],
  script: article.value
    ? [{
        type: 'application/ld+json',
        innerHTML: JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'Article',
          headline: article.value.title,
          description: article.value.summary || '',
          image: article.value.cover ? [article.value.cover] : undefined,
          datePublished: article.value.createTime || undefined,
          dateModified: article.value.updateTime || article.value.createTime || undefined,
          author: { '@type': 'Person', name: '好啵' },
          mainEntityOfPage: canonicalUrl.value
        })
      }]
    : []
}))
</script>
