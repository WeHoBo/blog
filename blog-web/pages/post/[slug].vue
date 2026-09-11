<template>
  <div class="max-w-7xl mx-auto py-6">
    <div class="flex gap-6">
      <main class="flex-1 min-w-0">
        <div v-if="pending" class="flex justify-center py-32">
          <div class="animate-spin h-8 w-8 border-2 border-primary-600 border-t-transparent rounded-full"></div>
        </div>
        <div v-else-if="error || !article" class="bg-white dark:bg-gray-800 rounded-lg shadow-sm p-20 text-center text-gray-400">
          <span class="text-5xl mb-4 block">🔍</span>文章不存在或已被删除
        </div>
        <article v-else class="bg-white dark:bg-gray-800 rounded-lg shadow-sm overflow-hidden">
          <div class="px-6 sm:px-10 pt-8">
            <h1 class="text-2xl sm:text-3xl font-extrabold text-gray-900 dark:text-gray-100 leading-tight mb-4 neon-glow">{{ article.title }}</h1>
            <div class="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-gray-400 mb-6 pb-6 border-b dark:border-gray-700">
              <span>{{ article.createTime?.substring(0, 10) }}</span>
              <span>👁 {{ article.viewCount }} 阅读</span>
              <span>💬 {{ article.commentCount || 0 }} 评论</span>
            </div>
          </div>
          <div class="px-6 sm:px-10 py-8">
            <MarkdownRenderer :content="article.contentMd || ''" />
          </div>
        </article>
        <div class="mt-6">
          <NuxtLink to="/" class="inline-flex items-center gap-1 text-primary-600 dark:text-primary-400 hover:underline text-sm font-medium">← 返回首页</NuxtLink>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const { get } = useApi()

const SITE_URL = 'https://codeup.asia'

const { data: articleData, pending, error } = await useAsyncData(`post-${route.params.slug}`, async () => {
  const res = await get<any>(`/article/post/${route.params.slug}`)
  if (res.code === 200 && res.data) return res.data.article || res.data
  throw new Error('文章不存在')
}, {
  // 客户端在 /post/a → /post/b 之间导航时自动重新拉取
  watch: [() => route.params.slug]
})

const article = computed(() => articleData.value as any)
const canonicalUrl = computed(() => `${SITE_URL}/post/${route.params.slug}`)

// 这个 URL 最规范，此前却完全没有 SEO meta：补上 title/description/og，
// 并声明 canonical（与 /article/{id} 去重，避免同一篇内容两个 URL 互相稀释权重）。
useSeoMeta({
  title: () => (article.value?.title ? `${article.value.title} - 好啵博客` : '文章 - 好啵博客'),
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
